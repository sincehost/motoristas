package screens

import androidx.compose.runtime.Composable
import database.AppRepository

@Composable
expect fun ManutencaoScreen(
    repository: AppRepository,
    onVoltar: () -> Unit
)
