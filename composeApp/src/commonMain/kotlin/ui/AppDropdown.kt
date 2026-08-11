package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Substituto de `ExposedDropdownMenuBox` + `ExposedDropdownMenu` que NÃO usa
 * `Popup`. No destino iOS do Compose Multiplatform, dentro da estrutura de
 * navegação deste app (telas trocadas dentro de um `AnimatedVisibility`),
 * `Popup` (e portanto `DropdownMenu`/`ExposedDropdownMenu`, que são implementados
 * em cima de `Popup`) simplesmente não aparece — testado e confirmado. Por isso
 * a lista de opções é renderizada como conteúdo normal logo abaixo do campo
 * (empurra o resto da tela para baixo, não flutua por cima), sem passar pela
 * ponte de janela nativa que o Popup exige.
 *
 * Use no lugar de `ExposedDropdownMenuBox`/`ExposedDropdownMenu` em qualquer
 * campo de seleção do app. Para os itens da lista, use [AppDropdownMenuItem].
 */
@Composable
fun AppDropdownField(
    label: String,
    selectedText: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                leadingIcon = leadingIcon,
                trailingIcon = {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                },
                isError = isError,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            // Overlay transparente para capturar o toque — o campo em si é
            // somente leitura e não deve abrir teclado/foco de texto.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onExpandedChange(!expanded) }
            )
        }
        if (expanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(12.dp))
        }
        text()
    }
    HorizontalDivider(color = AppColors.Divider)
}
