package screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.ApiClient
import api.AtualizarArlaRequest
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar
import util.DateInputField
import util.dataAtualFormatada
import util.converterDataParaAPI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarArlaScreen(
    repository: AppRepository,
    arlaId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }

    var carregando by remember { mutableStateOf(true) }
    var salvando by remember { mutableStateOf(false) }
    
    var data by remember { mutableStateOf(dataAtualFormatada()) }
    var valor by remember { mutableStateOf("") }
    var litros by remember { mutableStateOf("") }
    var posto by remember { mutableStateOf("") }
    var kmPosto by remember { mutableStateOf("") }
    
    var fotoBase64 by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = mensagem,
                duration = if (isErro) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(arlaId) {
        carregando = true
        try {
            val response = ApiClient.detalheArla(
                api.DetalheArlaRequest(
                    arla_id = arlaId
                )
            )
            if (response.status == "ok" && response.arla != null) {
                val arla = response.arla
                data = arla.data
                valor = arla.valor.toString()
                litros = arla.litros.toString()
                posto = arla.posto
                kmPosto = arla.km_posto
                fotoBase64 = arla.foto
            } else {
                mostrarMensagem("Erro ao carregar dados", isErro = true)
            }
        } catch (e: Exception) {
            mostrarMensagem("Erro: ${e.message}", isErro = true)
        }
        carregando = false
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Editar ARLA",
                onBackClick = onVoltar
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (data.visuals.message.contains("sucesso", ignoreCase = true) ||
                        data.visuals.message.contains("atualizada", ignoreCase = true))
                        AppColors.Secondary else AppColors.Error,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    actionColor = Color.White
                )
            }
        }
    ) { padding ->
        if (carregando) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF06B6D4))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Informações do ARLA",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )

                        Spacer(Modifier.height(20.dp))

                        DateInputField(
                            value = data,
                            onValueChange = { data = it },
                            label = "Data",
                            primaryColor = Color(0xFF06B6D4),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = valor,
                            onValueChange = {
                                val digits = it.filter { c -> c.isDigit() }.take(7)
                                valor = formatarValor(digits)
                            },
                            label = { Text("Valor (R\$) *") },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = litros,
                            onValueChange = {
                                val digits = it.filter { c -> c.isDigit() || c == '.' }.take(6)
                                litros = digits
                            },
                            label = { Text("Litros *") },
                            leadingIcon = { Icon(Icons.Default.Opacity, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = posto,
                            onValueChange = { posto = it },
                            label = { Text("Nome do Posto *") },
                            leadingIcon = { Icon(Icons.Default.Store, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = kmPosto,
                            onValueChange = { kmPosto = it.filter { c -> c.isDigit() } },
                            label = { Text("KM do Posto *") },
                            leadingIcon = { Icon(Icons.Default.Speed, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Spacer(Modifier.height(16.dp))

                        Text("Foto do Comprovante", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        
                        // Nota: Funcionalidade de câmera desabilitada no iOS
                        // Será implementada posteriormente usando APIs nativas do iOS
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .background(Color.Gray.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CameraAlt, 
                                    "Câmera", 
                                    modifier = Modifier.size(48.dp), 
                                    tint = Color.Gray
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Funcionalidade de câmera\nem desenvolvimento para iOS", 
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    if (data.isBlank()) { mostrarMensagem("Informe a data", isErro = true); return@launch }
                                    if (valor.isBlank()) { mostrarMensagem("Informe o valor", isErro = true); return@launch }
                                    if (litros.isBlank()) { mostrarMensagem("Informe os litros", isErro = true); return@launch }
                                    if (posto.isBlank()) { mostrarMensagem("Informe o posto", isErro = true); return@launch }
                                    if (kmPosto.isBlank()) { mostrarMensagem("Informe o KM do posto", isErro = true); return@launch }

                                    salvando = true
                                    try {
                                        val response = ApiClient.atualizarArla(
                                            AtualizarArlaRequest(
                                                arla_id = arlaId,
                                                data = converterDataParaAPI(data),
                                                valor = valor.replace(".", "").replace(",", ".").toDouble(),
                                                litros = litros.toDouble(),
                                                posto = posto,
                                                km_posto = kmPosto,
                                                foto = fotoBase64
                                            )
                                        )
                                        if (response.status == "ok") {
                                            mostrarMensagem("✓ ARLA atualizado com sucesso!")
                                            kotlinx.coroutines.delay(1500)
                                            onVoltar()
                                        } else {
                                            mostrarMensagem(response.mensagem ?: "Erro ao salvar", isErro = true)
                                        }
                                    } catch (e: Exception) {
                                        mostrarMensagem("Erro: ${e.message}", isErro = true)
                                    }
                                    salvando = false
                                }
                            },
                            enabled = !salvando,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                        ) {
                            if (salvando) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("SALVAR ALTERAÇÕES", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

private fun formatarValor(digits: String): String {
    if (digits.isEmpty()) return ""
    val valor = digits.toLongOrNull() ?: return ""
    val reais = valor / 100
    val centavos = valor % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
}
