package screens

import androidx.compose.runtime.Composable
import database.AppRepository

// Tela comum (Android e iOS): ver ChecklistComum.kt.
@Composable
actual fun ChecklistPosViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) = ChecklistTela(repository = repository, chegada = true, onVoltar = onVoltar, onSucesso = onSucesso)
