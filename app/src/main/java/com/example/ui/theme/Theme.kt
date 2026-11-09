package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CrossBarColorScheme = darkColorScheme(
    primary = CrossBarGold,
    secondary = CrossBarGreen,
    tertiary = CrossBarGold,
    background = CrossBarNavyBg,
    surface = CrossBarCardBg,
    onPrimary = CrossBarNavyBg,
    onSecondary = Color.White,
    onTertiary = CrossBarNavyBg,
    onBackground = CrossBarText,
    onSurface = CrossBarText
)

private val LightColorScheme = lightColorScheme(
    primary = CrossBarGreen,
    secondary = CrossBarGold,
    tertiary = CrossBarGreen,
    background = Color(0xFFF5F6F8),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color(0xFF14171A),
    onBackground = Color(0xFF14171A),
    onSurface = Color(0xFF14171A)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CrossBarColorScheme,
        typography = Typography,
        content = content
    )
}
