package util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * rememberSaveable para TextFieldValue — sobrevive à morte do processo.
 *
 * O Compose não tem Saver built-in para TextFieldValue, então usamos este.
 * Salva apenas o texto (a posição do cursor é recriada no final do texto).
 *
 * Uso:
 *   var valor by rememberSaveableTextField("0,00")
 *
 * Em vez de:
 *   var valor by remember { mutableStateOf(TextFieldValue("0,00", selection = TextRange(4))) }
 */
@Composable
fun rememberSaveableTextField(initialValue: String = ""): MutableState<TextFieldValue> {
    return rememberSaveable(saver = textFieldValueSaver) {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(initialValue.length)))
    }
}

/**
 * Saver que persiste TextFieldValue como String simples.
 * Ao restaurar, posiciona cursor no final do texto.
 */
private val textFieldValueSaver = Saver<MutableState<TextFieldValue>, String>(
    save = { it.value.text },
    restore = { text ->
        mutableStateOf(TextFieldValue(text, selection = TextRange(text.length)))
    }
)
