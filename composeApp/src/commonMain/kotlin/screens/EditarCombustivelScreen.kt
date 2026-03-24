package screens

import androidx.compose.runtime.Composable
import database.AppRepository

@Composable
expect fun EditarCombustivelScreen(
    repository: AppRepository,
    abastecimentoId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
)
