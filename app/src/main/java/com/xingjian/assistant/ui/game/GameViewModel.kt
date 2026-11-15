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
            _message.value = "正在解析下载地址…"
            val resolved = runCatching { repository.resolveDownload(game.packageName) }.getOrNull()
            when {
                resolved == null -> {
                    _message.value = "未获取到可直接安装的直链，已打开 TapTap 页面"
                    repository.openTapTapPage(game.appId)
                }
                resolved.second -> {
                    _message.value = "该游戏为分包安装包（xapk），已打开 TapTap 页面继续下载"
                    repository.openTapTapPage(game.appId)
                }
                else -> {
                    val queued = repository.enqueueApkDownload(
                        resolved.first,
                        game.title,
                        game.packageName
                    )
                    _message.value = if (queued) {
                        "已开始下载，完成后在通知栏点击安装"
                    } else {
                        "下载任务创建失败"
                    }
                }
            }
            _downloading.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
