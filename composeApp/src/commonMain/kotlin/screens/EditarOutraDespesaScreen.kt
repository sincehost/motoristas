package screens

import androidx.compose.runtime.Composable
import api.OutraDespesaItem
import database.AppRepository

@Composable
expect fun EditarOutraDespesaScreen(
    repository: AppRepository,
    item: OutraDespesaItem,
    viagemId: Int,
    onVoltar: () -> Unit
)
