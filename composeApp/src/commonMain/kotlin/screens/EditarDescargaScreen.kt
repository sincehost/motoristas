package screens

import androidx.compose.runtime.Composable
import database.AppRepository

@Composable
expect fun EditarDescargaScreen(
    repository: AppRepository,
    descargaId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
)