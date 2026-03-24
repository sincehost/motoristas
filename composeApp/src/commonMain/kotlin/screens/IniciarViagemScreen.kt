package screens

import androidx.compose.runtime.Composable
import database.AppRepository

@Composable
expect fun IniciarViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
)
