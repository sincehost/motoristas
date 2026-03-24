package screens

import androidx.compose.runtime.Composable
import database.AppRepository

@Composable
expect fun EditarManutencaoScreen(
    repository: AppRepository,
    manutencaoId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
)