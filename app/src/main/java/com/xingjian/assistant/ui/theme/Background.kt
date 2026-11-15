package com.xingjian.assistant.ui.theme

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xingjian.assistant.data.repository.SettingsRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 当前是否启用了自定义背景图，供 Scaffold 决定是否透明化 */
val LocalCustomBackground = compositionLocalOf { false }

/**
 * 全局背景层：自定义图片 + 与主题匹配的半透明蒙层。
 * 蒙层保证文字与卡片在各模块上都有足够对比度。
 */
@Composable
fun AppBackground(settings: SettingsRepository, content: @Composable () -> Unit) {
    val version by settings.backgroundVersion.collectAsStateWithLifecycle()
    val file = remember(version) { settings.backgroundFile() }
    var bitmap by remember(version) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(version) {
        bitmap = file?.let { decodeBackground(it) }
    }

    CompositionLocalProvider(LocalCustomBackground provides (bitmap != null)) {
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.background.copy(
                                alpha = if (dark) 0.88f else 0.84f
                            )
                        )
                )
            }
            content()
        }
    }
}

/** 解码背景图：按长边采样，ImageDecoder 会自动修正 EXIF 方向 */
internal suspend fun decodeBackground(file: File): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (longest / (sample * 2) >= 1600) sample *= 2
            decoder.setTargetSampleSize(sample)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()
}
