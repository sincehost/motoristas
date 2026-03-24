package screens

import androidx.compose.runtime.Composable
import database.AppRepository

@Composable
expect fun ChecklistPreViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
)

@Composable
expect fun ChecklistPosViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
)
