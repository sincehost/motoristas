package screens

import androidx.compose.runtime.Composable
import database.AppRepository

@Composable
expect fun RelatorioViagensScreen(
    repository: AppRepository,
    onVoltar: () -> Unit
)