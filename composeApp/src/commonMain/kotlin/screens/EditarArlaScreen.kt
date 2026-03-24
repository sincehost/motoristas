package screens

import androidx.compose.runtime.Composable
import database.AppRepository

@Composable
expect fun EditarArlaScreen(
    repository: AppRepository,
    arlaId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
)