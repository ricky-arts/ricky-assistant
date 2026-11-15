package com.xingjian.assistant.ui.game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xingjian.assistant.di.AppContainer
import com.xingjian.assistant.domain.model.GameDetail
import com.xingjian.assistant.domain.model.GameSummary
import com.xingjian.assistant.ui.components.EmptyState
import com.xingjian.assistant.ui.components.KeyValueRow
import com.xingjian.assistant.ui.components.MessageBanner
import com.xingjian.assistant.ui.components.SectionCard
import com.xingjian.assistant.ui.components.TagChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun GameScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: GameViewModel = viewModel(factory = container.viewModelFactory)
    val detail by vm.detail.collectAsStateWithLifecycle()
    val detailLoading by vm.detailLoading.collectAsStateWithLifecycle()
    val downloading by vm.downloading.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    val current = detail
    when {
        current != null -> {
            BackHandler { vm.closeDetail() }
            GameDetailPane(vm, current, downloading, progress, modifier)
        }
        detailLoading -> {
            BackHandler { vm.closeDetail() }
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        else -> GameRankPane(vm, modifier)
    }
}

@Composable
private fun GameRankPane(vm: GameViewModel, modifier: Modifier) {
    val board by vm.board.collectAsStateWithLifecycle()
    val games by vm.games.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val banner = message

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (banner != null) {
            item { MessageBanner(message = banner, onDismiss = { vm.clearMessage() }) }
        }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameBoard.entries.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = board == item,
                            onClick = { vm.selectBoard(item) },
                            label = { Text(item.label) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.loadRanking() }, enabled = !loading) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新榜单")
                        }
                    }
                }
            }
        }

        if (!loading && games.isEmpty()) {
            item { SectionCard { EmptyState("暂无榜单数据") } }
        }

        itemsIndexed(games, key = { _, game -> game.appId }) { index, game ->
            GameRankCard(rank = index + 1, game = game, onClick = { vm.openDetail(game) })
        }
    }
}

@Composable
private fun GameRankCard(rank: Int, game: GameSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (rank <= 3) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(30.dp)
            )
            RemoteImage(
                url = game.iconUrl,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = formatScore(game.score),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = formatHits(game.hitsTotal),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (game.tags.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = game.tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GameDetailPane(
    vm: GameViewModel,
    detail: GameDetail,
    downloading: Boolean,
    progress: Float,
    modifier: Modifier
) {
    val message by vm.message.collectAsStateWithLifecycle()
    val banner = message

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (banner != null) {
            item { MessageBanner(message = banner, onDismiss = { vm.clearMessage() }) }
        }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.closeDetail() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回榜单")
                    }
                    RemoteImage(
                        url = detail.iconUrl,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = formatScore(detail.score),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = formatHits(detail.hitsTotal),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (detail.developers.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = detail.developers.joinToString(" / "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (detail.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        detail.tags.take(4).forEach { tag ->
                            TagChip(text = tag, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        if (detail.screenshots.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(detail.screenshots) { url ->
                        RemoteImage(
                            url = url,
                            modifier = Modifier
                                .width(200.dp)
                                .height(112.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }

        if (detail.description.isNotBlank()) {
            item {
                SectionCard(title = "简介") {
                    Text(
                        text = detail.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(title = "安装包") {
                KeyValueRow("版本", detail.apk?.versionName?.ifBlank { "未知" } ?: "未知")
                KeyValueRow("大小", formatSize(detail.apk?.sizeBytes ?: 0L))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.download() },
                    enabled = !downloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (downloading) {
                            "下载中 ${(progress * 100).toInt()}%"
                        } else {
                            "下载安装"
                        }
                    )
                }
                if (downloading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "应用内下载，完成后自动调起系统安装",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { vm.cancelDownload() }) { Text("取消") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(url) { mutableStateOf(GameImageLoader.cached(url)) }
    LaunchedEffect(url) {
        if (url.isBlank() || bitmap != null) return@LaunchedEffect
        bitmap = GameImageLoader.load(url)
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

/** 轻量图片加载器：OkHttp + 内存 LruCache，避免为几张封面引入额外依赖 */
private object GameImageLoader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url) ?: runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", IMAGE_UA)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()?.also { cache.put(url, it) }
    }

    private const val IMAGE_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
}

private fun formatScore(score: Double): String =
    if (score <= 0.0) "—" else String.format(Locale.CHINA, "%.1f", score)

private fun formatHits(hits: Long): String = when {
    hits >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿热度", hits / 100_000_000.0)
    hits >= 10_000 -> String.format(Locale.CHINA, "%.1f万热度", hits / 10_000.0)
    hits > 0 -> "$hits 热度"
    else -> "热度 —"
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "未知"
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.CHINA, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    else -> String.format(Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024.0))
}
