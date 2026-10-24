package screens

import androidx.compose.runtime.Composable
import database.AppRepository

// Tela comum (Android e iOS): ver ChecklistComum.kt.
@Composable
actual fun ChecklistPreViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) = ChecklistTela(repository = repository, chegada = false, onVoltar = onVoltar, onSucesso = onSucesso)
