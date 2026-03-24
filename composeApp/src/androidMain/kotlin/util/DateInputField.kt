package util

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

/**
 * Campo de data que permite:
 * - Abrir calendário clicando no ícone
 * - Digitar manualmente no formato dd/MM/yyyy
 */
@Composable
fun DateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF1E88E5),
    enabled: Boolean = true
) {
    val context = LocalContext.current
    
    // Estado interno para controlar a digitação
    var textValue by remember(value) { mutableStateOf(value) }
    
    // Função para formatar enquanto digita (adiciona barras automaticamente)
    fun formatDateInput(input: String): String {
        // Remove tudo que não é número
        val digits = input.filter { it.isDigit() }
        
        return when {
            digits.length <= 2 -> digits
            digits.length <= 4 -> "${digits.substring(0, 2)}/${digits.substring(2)}"
            digits.length <= 8 -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${digits.substring(4, minOf(8, digits.length))}"
            else -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${digits.substring(4, 8)}"
        }
    }
    
    // Função para abrir o DatePicker
    fun showDatePicker() {
        val calendar = Calendar.getInstance()
        
        // Tentar parsear a data atual do campo
        try {
            val partes = value.split("/")
            if (partes.size == 3) {
                calendar.set(Calendar.DAY_OF_MONTH, partes[0].toInt())
                calendar.set(Calendar.MONTH, partes[1].toInt() - 1)
                calendar.set(Calendar.YEAR, partes[2].toInt())
            }
        } catch (e: Exception) {
            // Usa data atual se não conseguir parsear
        }
        
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val novaData = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                textValue = novaData
                onValueChange(novaData)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    Column(modifier = modifier) {
        // Label
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF374151),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Campo com ícone de calendário
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                // Permite deletar
                if (newValue.length < textValue.length) {
                    textValue = newValue
                    onValueChange(newValue)
                } else {
                    // Formata enquanto digita
                    val formatted = formatDateInput(newValue)
                    if (formatted.length <= 10) { // dd/MM/yyyy = 10 caracteres
                        textValue = formatted
                        onValueChange(formatted)
                    }
                }
            },
            enabled = enabled,
            placeholder = { 
                Text(
                    "dd/mm/aaaa",
                    color = Color(0xFF9CA3AF)
                ) 
            },
            trailingIcon = {
                IconButton(
                    onClick = { if (enabled) showDatePicker() }
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = "Abrir calendário",
                        tint = primaryColor
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = ui.darkTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Converte data de dd/MM/yyyy para yyyy-MM-dd (formato API)
 */
fun converterDataParaAPI(dataBR: String): String {
    return try {
        val partes = dataBR.split("/")
        if (partes.size == 3) {
            "${partes[2]}-${partes[1].padStart(2, '0')}-${partes[0].padStart(2, '0')}"
        } else dataBR
    } catch (e: Exception) { dataBR }
}

/**
 * Converte data de yyyy-MM-dd para dd/MM/yyyy (formato exibição)
 */
fun converterDataParaExibicao(dataAPI: String): String {
    return try {
        val partes = dataAPI.split("-")
        if (partes.size == 3) {
            "${partes[2]}/${partes[1]}/${partes[0]}"
        } else dataAPI
    } catch (e: Exception) { dataAPI }
}

/**
 * Retorna a data atual no formato dd/MM/yyyy
 */
fun dataAtualFormatada(): String {
    val calendar = Calendar.getInstance()
    return String.format(
        "%02d/%02d/%04d",
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.YEAR)
    )
}
