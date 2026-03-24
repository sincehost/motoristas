package ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// ===============================
// GERENCIADOR DE TEMA (GLOBAL)
// ===============================

enum class ThemeMode { LIGHT, DARK, SYSTEM }

object ThemeManager {
    var themeMode by mutableStateOf(ThemeMode.LIGHT)
}

// ===============================
// CORES — LIGHT
// ===============================
object AppColorsLight {
    val Primary = Color(0xFF07275A)
    val PrimaryDark = Color(0xFF0F2554)
    val Secondary = Color(0xFF499C2A)
    val Success = Color(0xFF499C2A)
    val Background = Color(0xFFF5F7FA)
    val Surface = Color.White
    val SurfaceVariant = Color(0xFFF0F2F5)
    val Error = Color(0xFFE53935)
    val TextPrimary = Color(0xFF1A202C)
    val TextSecondary = Color(0xFF718096)
    val Orange = Color(0xFFFF9800)
    val Purple = Color(0xFF042D3D)
    val Warning = Color(0xFFF59E0B)
    val CardBackground = Color.White
    val Divider = Color(0xFFE2E8F0)
    val IconTint = Color(0xFF4A5568)
}

// ===============================
// CORES — DARK (profissional, alto contraste, confortável para noite)
// ===============================
object AppColorsDark {
    val Primary = Color(0xFF5B8DEF)        // Azul claro vibrante — legível em fundo escuro
    val PrimaryDark = Color(0xFF3D6BC4)
    val Secondary = Color(0xFF66D944)       // Verde claro vibrante
    val Success = Color(0xFF66D944)
    val Background = Color(0xFF121212)      // Preto suave padrão Material Dark
    val Surface = Color(0xFF1E1E1E)         // Cinza escuro para cards
    val SurfaceVariant = Color(0xFF252525)  // Ligeiramente mais claro que Surface
    val Error = Color(0xFFFF7070)           // Vermelho suave
    val TextPrimary = Color(0xFFECECEC)     // Branco suave — não branco puro (cansa a vista)
    val TextSecondary = Color(0xFF9E9E9E)   // Cinza médio — boa legibilidade
    val Orange = Color(0xFFFFB74D)
    val Purple = Color(0xFF80DEEA)
    val Warning = Color(0xFFFFD54F)
    val CardBackground = Color(0xFF1E1E1E)  // Mesmo que Surface
    val Divider = Color(0xFF333333)
    val IconTint = Color(0xFFBDBDBD)
}

// ===============================
// PROXY DINÂMICO — USA CORES DO TEMA ATIVO
// ===============================
object AppColors {
    val Primary: Color @Composable get() = if (isDark()) AppColorsDark.Primary else AppColorsLight.Primary
    val PrimaryDark: Color @Composable get() = if (isDark()) AppColorsDark.PrimaryDark else AppColorsLight.PrimaryDark
    val Secondary: Color @Composable get() = if (isDark()) AppColorsDark.Secondary else AppColorsLight.Secondary
    val Success: Color @Composable get() = if (isDark()) AppColorsDark.Success else AppColorsLight.Success
    val Background: Color @Composable get() = if (isDark()) AppColorsDark.Background else AppColorsLight.Background
    val Surface: Color @Composable get() = if (isDark()) AppColorsDark.Surface else AppColorsLight.Surface
    val SurfaceVariant: Color @Composable get() = if (isDark()) AppColorsDark.SurfaceVariant else AppColorsLight.SurfaceVariant
    val Error: Color @Composable get() = if (isDark()) AppColorsDark.Error else AppColorsLight.Error
    val TextPrimary: Color @Composable get() = if (isDark()) AppColorsDark.TextPrimary else AppColorsLight.TextPrimary
    val TextSecondary: Color @Composable get() = if (isDark()) AppColorsDark.TextSecondary else AppColorsLight.TextSecondary
    val Orange: Color @Composable get() = if (isDark()) AppColorsDark.Orange else AppColorsLight.Orange
    val Purple: Color @Composable get() = if (isDark()) AppColorsDark.Purple else AppColorsLight.Purple
    val Warning: Color @Composable get() = if (isDark()) AppColorsDark.Warning else AppColorsLight.Warning
    val CardBackground: Color @Composable get() = if (isDark()) AppColorsDark.CardBackground else AppColorsLight.CardBackground
    val Divider: Color @Composable get() = if (isDark()) AppColorsDark.Divider else AppColorsLight.Divider
    val IconTint: Color @Composable get() = if (isDark()) AppColorsDark.IconTint else AppColorsLight.IconTint
}

@Composable
fun isDark(): Boolean {
    return when (ThemeManager.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}

/**
 * Propriedade Composable para uso em expressões inline.
 * Exemplo: if (ui.isDarkState) Icons.Default.LightMode else Icons.Default.DarkMode
 */
val isDarkState: Boolean
    @Composable get() = isDark()

// ===============================
// TEMA MATERIAL
// ===============================
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isDark()
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = AppColorsDark.Primary,
            secondary = AppColorsDark.Secondary,
            background = AppColorsDark.Background,
            surface = AppColorsDark.Surface,
            surfaceVariant = Color(0xFF2A2A2A),            // Fundo dos inputs/dropdowns
            surfaceContainerHighest = Color(0xFF2A2A2A),   // Fundo dos inputs M3
            surfaceContainer = Color(0xFF252525),
            surfaceBright = Color(0xFF333333),
            error = AppColorsDark.Error,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = AppColorsDark.TextPrimary,
            onSurface = AppColorsDark.TextPrimary,         // Texto digitado nos inputs
            onSurfaceVariant = AppColorsDark.TextSecondary, // Labels e placeholders
            outline = Color(0xFF555555),                    // Borda dos inputs (visível no escuro)
            outlineVariant = Color(0xFF444444),
            inverseSurface = Color(0xFFE0E0E0),
            inverseOnSurface = Color(0xFF1A1A1A)
        )
    } else {
        lightColorScheme(
            primary = AppColorsLight.Primary,
            secondary = AppColorsLight.Secondary,
            background = AppColorsLight.Background,
            surface = AppColorsLight.Surface,
            surfaceVariant = AppColorsLight.SurfaceVariant,
            error = AppColorsLight.Error,
            onPrimary = Color.White,
            onBackground = AppColorsLight.TextPrimary,
            onSurface = AppColorsLight.TextPrimary,
            onSurfaceVariant = AppColorsLight.TextSecondary,
            outline = AppColorsLight.Divider
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

/**
 * Cores padrão para OutlinedTextField no dark mode.
 * Usar nas telas que precisam forçar fundo escuro nos inputs:
 *
 *   OutlinedTextField(
 *       ...,
 *       colors = darkTextFieldColors()
 *   )
 */
@Composable
fun darkTextFieldColors() = if (isDark()) {
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        // Fundo escuro
        unfocusedContainerColor = Color(0xFF2A2A2A),
        focusedContainerColor = Color(0xFF2A2A2A),
        // Texto claro
        unfocusedTextColor = AppColorsDark.TextPrimary,
        focusedTextColor = AppColorsDark.TextPrimary,
        // Labels cinza
        unfocusedLabelColor = AppColorsDark.TextSecondary,
        focusedLabelColor = AppColorsDark.Primary,
        // Placeholder cinza
        unfocusedPlaceholderColor = Color(0xFF666666),
        focusedPlaceholderColor = Color(0xFF666666),
        // Bordas visíveis
        unfocusedBorderColor = Color(0xFF555555),
        focusedBorderColor = AppColorsDark.Primary,
        // Ícones
        unfocusedLeadingIconColor = AppColorsDark.TextSecondary,
        focusedLeadingIconColor = AppColorsDark.Primary,
        unfocusedTrailingIconColor = AppColorsDark.TextSecondary,
        focusedTrailingIconColor = AppColorsDark.Primary,
        // Cursor
        cursorColor = AppColorsDark.Primary
    )
} else {
    androidx.compose.material3.OutlinedTextFieldDefaults.colors()
}
