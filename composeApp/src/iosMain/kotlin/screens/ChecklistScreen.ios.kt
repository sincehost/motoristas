package screens

import androidx.compose.runtime.Composable
import database.AppRepository

// Telas comuns (Android e iOS): ver ChecklistComum.kt.
@Composable
actual fun ChecklistPreViagemScreen(repository: AppRepository, onVoltar: () -> Unit, onSucesso: () -> Unit) =
    ChecklistTela(repository = repository, chegada = false, onVoltar = onVoltar, onSucesso = onSucesso)

@Composable
actual fun ChecklistPosViagemScreen(repository: AppRepository, onVoltar: () -> Unit, onSucesso: () -> Unit) =
    ChecklistTela(repository = repository, chegada = true, onVoltar = onVoltar, onSucesso = onSucesso)
