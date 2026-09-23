package com.example.bizmanagezz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BizGreen = Color(0xFF176B4D)
private val BizGreenLight = Color(0xFFD7F2E5)
private val BizGold = Color(0xFFE7A929)
private val BizBackground = Color(0xFFF7F9F7)

private val LightColors = lightColorScheme(
    primary = BizGreen,
    onPrimary = Color.White,
    secondary = BizGold,
    background = BizBackground,
    surface = Color.White,
    surfaceVariant = BizGreenLight
)

@Composable
fun BizManagezzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content
    )
}
