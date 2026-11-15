package com.xingjian.assistant.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingjian.assistant.data.repository.GameRepository
import com.xingjian.assistant.domain.model.GameDetail
import com.xingjian.assistant.domain.model.GameSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 榜单类型：热门 / 新品 / 预约 */
enum class GameBoard(val label: String, val typeName: String) {
    HOT("热门", "hot"),
    NEW("新品", "new"),
    RESERVE("预约", "reserve")
}

class GameViewModel(private val repository: GameRepository) : ViewModel() {

    private val _board = MutableStateFlow(GameBoard.HOT)
    val board: StateFlow<GameBoard> = _board.asStateFlow()

    private val _games = MutableStateFlow<List<GameSummary>>(emptyList())
    val games: StateFlow<List<GameSummary>> = _games.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _detail = MutableStateFlow<GameDetail?>(null)
    val detail: StateFlow<GameDetail?> = _detail.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    /** 0f~1f 为下载进度；下载前解析直链阶段固定为 0 */
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadRanking()
    }

    fun selectBoard(target: GameBoard) {
        if (_board.value == target && _games.value.isNotEmpty()) return
        _board.value = target
        loadRanking()
    }

    fun loadRanking() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            runCatching { repository.ranking(_board.value.typeName) }
                .onSuccess { list ->
                    _games.value = list
                    if (list.isEmpty()) _message.value = "该榜单暂无数据"
                }
                .onFailure { _message.value = "榜单加载失败：${it.message ?: "网络异常"}" }
            _loading.value = false
        }
    }

    fun openDetail(game: GameSummary) {
        if (_detailLoading.value) return
        viewModelScope.launch {
            _detailLoading.value = true
            runCatching { repository.detail(game.appId) }
                .onSuccess { _detail.value = it }
                .onFailure { _message.value = "详情加载失败：${it.message ?: "网络异常"}" }
            _detailLoading.value = false
        }
    }

    fun closeDetail() {
        _detail.value = null
        _detailLoading.value = false
    }

    fun download() {
        val game = _detail.value ?: return
        if (_downloading.value) return
        viewModelScope.launch {
            _downloading.value = true
            _progress.value = 0f
            _message.value = "正在解析安装包地址…"
            val link = runCatching { repository.resolveDownload(game) }.getOrNull()
            if (link == null) {
                _downloading.value = false
                _message.value = "该游戏暂未提供可下载的安装包（可能尚未上线或仅供预约），请稍后再试"
                return@launch
            }
            _message.value = "正在下载 ${game.title}…"
            repository.downloadAndInstall(link, game.packageName) { percent ->
                _progress.value = percent.coerceIn(0f, 1f)
            }
                .onSuccess {
                    _message.value = "下载完成，请按系统安装页提示确认安装"
                }
                .onFailure { error ->
                    _message.value = if (error is SecurityException) {
                        "请先在系统设置中允许本应用安装未知应用"
                    } else {
                        "下载失败：${error.message ?: "网络异常"}"
                    }
                }
            _downloading.value = false
        }
    }

    fun cancelDownload() {
        repository.cancelDownload()
        _downloading.value = false
        _message.value = "已取消下载"
    }

    fun clearMessage() {
        _message.value = null
    }
}
