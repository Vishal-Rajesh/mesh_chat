package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BentoColorScheme = lightColorScheme(
    primary = BentoTextFaint, // 0xFF9C4330
    secondary = BentoPurpleText, // 0xFF6750A4
    tertiary = BentoBlueText, // 0xFF004A77
    background = BentoBg, // 0xFFFFF8F6
    surface = Color(0xFFFFFFFF),
    surfaceVariant = BentoPink, // 0xFFFFDAD4
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = BentoText, // 0xFF201A19
    onSurface = BentoText,
    onSurfaceVariant = BentoTextMuted, // 0xFF4F4442
    outline = BentoPinkBorder // 0xFFF5BBB1
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BentoColorScheme,
        typography = Typography,
        content = content
    )
}
