package screens

import androidx.compose.runtime.Composable

/**
 * Handler multiplataforma para o botão "Voltar" do sistema.
 *
 * - Android: Usa androidx.activity.compose.BackHandler
 * - iOS: No-op (iOS não tem botão voltar do sistema)
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
