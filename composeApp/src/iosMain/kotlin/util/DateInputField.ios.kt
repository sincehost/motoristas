package util

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import ui.AppColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import platform.Foundation.*

@Composable
fun DateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF1E88E5),
    enabled: Boolean = true
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var textValue by remember(value) { mutableStateOf(value) }

    fun formatDateInput(input: String): String {
        val digits = input.filter { it.isDigit() }
        return when {
            digits.length <= 2 -> digits
            digits.length <= 4 -> "${digits.substring(0, 2)}/${digits.substring(2)}"
            digits.length <= 8 -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${digits.substring(4, minOf(8, digits.length))}"
            else -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${digits.substring(4, 8)}"
        }
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF374151),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                if (newValue.length < textValue.length) {
                    textValue = newValue
                    onValueChange(newValue)
                } else {
                    val formatted = formatDateInput(newValue)
                    if (formatted.length <= 10) {
                        textValue = formatted
                        onValueChange(formatted)
                    }
                }
            },
            enabled = enabled,
            placeholder = { Text("dd/mm/aaaa", color = Color(0xFF9CA3AF)) },
            trailingIcon = {
                IconButton(onClick = { if (enabled) showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Abrir calendário", tint = primaryColor)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = ui.darkTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            currentDate = value,
            onDateSelected = { selectedDate ->
                textValue = selectedDate
                onValueChange(selectedDate)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    currentDate: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val (day, month, year) = try {
        if (currentDate.isNotEmpty() && currentDate.contains("/")) {
            val parts = currentDate.split("/")
            Triple(
                parts[0].toIntOrNull() ?: getCurrentDay(),
                parts[1].toIntOrNull() ?: getCurrentMonth(),
                parts[2].toIntOrNull() ?: getCurrentYear()
            )
        } else {
            Triple(getCurrentDay(), getCurrentMonth(), getCurrentYear())
        }
    } catch (e: Exception) {
        Triple(getCurrentDay(), getCurrentMonth(), getCurrentYear())
    }

    var selectedDay by remember { mutableStateOf(day) }
    var selectedMonth by remember { mutableStateOf(month) }
    var selectedYear by remember { mutableStateOf(year) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Selecionar Data", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    NumberPicker(value = selectedDay, range = 1..31, onValueChange = { selectedDay = it }, label = "Dia", modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    NumberPicker(value = selectedMonth, range = 1..12, onValueChange = { selectedMonth = it }, label = "Mês", modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    NumberPicker(value = selectedYear, range = 2020..2030, onValueChange = { selectedYear = it }, label = "Ano", modifier = Modifier.weight(1.5f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val formattedDate = buildString {
                                append(if (selectedDay < 10) "0$selectedDay" else "$selectedDay")
                                append("/")
                                append(if (selectedMonth < 10) "0$selectedMonth" else "$selectedMonth")
                                append("/")
                                append(selectedYear.toString())
                            }
                            onDateSelected(formattedDate)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { newValue ->
                val number = newValue.toIntOrNull()
                if (number != null && number in range) {
                    onValueChange(number)
                }
            },
            modifier = Modifier.width(80.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

fun converterDataParaAPI(dataBR: String): String {
    return try {
        val partes = dataBR.split("/")
        if (partes.size == 3) {
            val dia = if (partes[0].length == 1) "0${partes[0]}" else partes[0]
            val mes = if (partes[1].length == 1) "0${partes[1]}" else partes[1]
            "${partes[2]}-$mes-$dia"
        } else dataBR
    } catch (e: Exception) { dataBR }
}

fun converterDataParaExibicao(dataAPI: String): String {
    return try {
        val partes = dataAPI.split("-")
        if (partes.size == 3) {
            "${partes[2]}/${partes[1]}/${partes[0]}"
        } else dataAPI
    } catch (e: Exception) { dataAPI }
}

fun dataAtualFormatada(): String {
    val now = NSDate()
    val calendar = NSCalendar.currentCalendar
    val components = calendar.components(
        NSCalendarUnitDay.toULong() or NSCalendarUnitMonth.toULong() or NSCalendarUnitYear.toULong(),
        now
    )
    val day = components.day.toInt()
    val month = components.month.toInt()
    val year = components.year.toInt()
    return buildString {
        append(if (day < 10) "0$day" else "$day")
        append("/")
        append(if (month < 10) "0$month" else "$month")
        append("/")
        append(year.toString())
    }
}

private fun getCurrentDay(): Int {
    val now = NSDate()
    val calendar = NSCalendar.currentCalendar
    val components = calendar.components(NSCalendarUnitDay.toULong(), now)
    return components.day.toInt()
}

private fun getCurrentMonth(): Int {
    val now = NSDate()
    val calendar = NSCalendar.currentCalendar
    val components = calendar.components(NSCalendarUnitMonth.toULong(), now)
    return components.month.toInt()
}

private fun getCurrentYear(): Int {
    val now = NSDate()
    val calendar = NSCalendar.currentCalendar
    val components = calendar.components(NSCalendarUnitYear.toULong(), now)
    return components.year.toInt()
}
