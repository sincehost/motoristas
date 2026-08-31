package screens

import androidx.compose.runtime.Composable
import database.AppRepository

@Composable
expect fun AdicionarFreteScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
)
