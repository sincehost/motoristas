package screens

import androidx.compose.runtime.Composable
import database.AppRepository

@Composable
expect fun MinhasViagensScreen(
    repository: AppRepository,
    onVoltar: () -> Unit
)
