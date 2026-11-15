package com.xingjian.assistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 统一字体层级：标题略加粗，正文保持轻盈 */
val XingJianTypography: Typography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium),
        bodyMedium = base.bodyMedium.copy(lineHeight = 20.sp),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.3.sp)
    )
}
