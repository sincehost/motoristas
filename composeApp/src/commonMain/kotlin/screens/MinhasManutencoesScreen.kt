package screens

import androidx.compose.runtime.Composable
import database.AppRepository

@Composable
expect fun MinhasManutencoesScreen(
    repository: AppRepository,
    onVoltar: () -> Unit
)
