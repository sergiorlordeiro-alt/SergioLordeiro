package com.meusgastos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Verde = Color(0xFF15803D)
val Vermelho = Color(0xFFC2410C)
val Azul = Color(0xFF1D4ED8)
val Ambar = Color(0xFFB45309)

private val Claro = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F0EC), onPrimaryContainer = Color(0xFF06302B),
    background = Color(0xFFF4F6F5), surface = Color.White, surfaceVariant = Color(0xFFE3E9E7),
    onSurfaceVariant = Color(0xFF64736F), outlineVariant = Color(0xFFE3E9E7),
)
private val Escuro = darkColorScheme(
    primary = Color(0xFF2DD4BF), onPrimary = Color(0xFF06201C),
    primaryContainer = Color(0xFF123A35), onPrimaryContainer = Color(0xFFD9F0EC),
    background = Color(0xFF0E1513), surface = Color(0xFF17211E), surfaceVariant = Color(0xFF26332F),
    onSurfaceVariant = Color(0xFF93A39F), outlineVariant = Color(0xFF26332F),
)

@Composable
fun MeusGastosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Escuro else Claro, content = content)
}
