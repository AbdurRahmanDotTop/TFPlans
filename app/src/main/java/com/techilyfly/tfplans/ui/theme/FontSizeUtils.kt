package com.techilyfly.tfplans.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object FontSizeManager {
    const val MIN_SP = 12f
    const val DEFAULT_SP = 16f
    const val MAX_SP = 24f

    fun parseSp(fontSizeStr: String): Float {
        return when (fontSizeStr.lowercase().trim()) {
            "small" -> 13f
            "medium" -> 16f
            "large" -> 19f
            "x-large", "xlarge", "extra large" -> 22f
            else -> fontSizeStr.toFloatOrNull()?.coerceIn(MIN_SP, MAX_SP) ?: DEFAULT_SP
        }
    }

    fun getTitleSp(fontSizeStr: String): TextUnit = (parseSp(fontSizeStr) + 4f).sp
    fun getCardTitleSp(fontSizeStr: String): TextUnit = (parseSp(fontSizeStr) + 1f).sp
    fun getBodySp(fontSizeStr: String): TextUnit = parseSp(fontSizeStr).sp
    fun getPreviewSp(fontSizeStr: String): TextUnit = (parseSp(fontSizeStr) - 2f).coerceAtLeast(11f).sp
    fun getLabelSp(fontSizeStr: String): TextUnit = (parseSp(fontSizeStr) - 4f).coerceAtLeast(10f).sp
}
