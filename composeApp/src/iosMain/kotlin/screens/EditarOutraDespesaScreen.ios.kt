package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.OutraDespesaItem
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarOutraDespesaScreen(
    repository: AppRepository,
    item: OutraDespesaItem,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()

    var tipo by remember { mutableStateOf(item.tipo) }
    var descricao by remember { mutableStateOf(item.descricao) }
    var valor by remember { mutableStateOf(item.valor.toString()) }
    var data by remember { mutableStateOf(item.data) }
    var local by remember { mutableStateOf(item.local) }
    var salvando by remember { mutableStateOf(false) }
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    fun salvar() {
        if (valor.isBlank()) { erroMsg = "Informe o valor"; return }
        scope.launch {
            salvando = true
            try {
                val resp = api.ApiClient.atualizarOutraDespesa(
                    api.AtualizarOutraDespesaRequest(
                        despesa_id = item.id,
                        motorista_id = motorista?.motorista_id ?: "",
                        viagem_id = viagemId,
                        tipo = tipo,
                        descricao = descricao.ifEmpty { tipo },
                        valor = valor,
                        data = data,
                        local = local.ifEmpty { null },
                        foto_comprovante = null
                    )
                )
                if (resp.status == "ok") sucessoMsg = "Despesa atualizada!"
                else erroMsg = resp.mensagem ?: "Erro ao atualizar"
            } catch (e: Exception) { erroMsg = "Erro: ${e.message}" }
            salvando = false
        }
    }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onVoltar() }

    Scaffold(topBar = { GradientTopBar(title = "Editar Despesa", onBackClick = onVoltar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    OutlinedTextField(tipo, { tipo = it }, label = { Text("Tipo") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(descricao, { descricao = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(valor, { valor = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Valor (R$)") }, leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(data, { data = it }, label = { Text("Data") }, leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(local, { local = it }, label = { Text("Local") }, leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                if (salvando) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Salvar Alterações", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
