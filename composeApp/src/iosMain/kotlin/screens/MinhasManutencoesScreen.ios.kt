package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.*
import database.AppRepository
import kotlinx.coroutines.launch
import platform.Foundation.*
import ui.AppColors
import ui.GradientTopBar

// Estados de navegação
private sealed class TelaManutencao {
    object Lista : TelaManutencao()
    data class Editar(val manutencaoId: Int, val viagemId: Int) : TelaManutencao()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun MinhasManutencoesScreen(
    repository: AppRepository,
    onVoltar: () -> Unit
) {
    var telaAtual by remember { mutableStateOf<TelaManutencao>(TelaManutencao.Lista) }

    when (val tela = telaAtual) {
        is TelaManutencao.Lista -> ListaManutencoesContent(
            repository = repository,
            onVoltar = onVoltar,
            onEditar = { manutId, viagId -> telaAtual = TelaManutencao.Editar(manutId, viagId) }
        )
        is TelaManutencao.Editar -> EditarManutencaoScreen(
            repository = repository,
            manutencaoId = tela.manutencaoId,
            viagemId = tela.viagemId,
            onVoltar = { telaAtual = TelaManutencao.Lista }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListaManutencoesContent(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onEditar: (Int, Int) -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var carregando by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var manutencoes by remember { mutableStateOf<List<ManutencaoItem>>(emptyList()) }

    var mostrarDialogoExcluir by remember { mutableStateOf(false) }
    var manutencaoParaExcluir by remember { mutableStateOf<ManutencaoItem?>(null) }
    var excluindo by remember { mutableStateOf(false) }

    // Função para mostrar mensagens
    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = mensagem,
                duration = if (isErro) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    fun carregarManutencoes() {
        scope.launch {
            carregando = true
            erro = null
            try {
                val response = ApiClient.getManutencoes(
                    motorista?.motorista_id ?: ""
                )
                if (response.status == "ok") {
                    manutencoes = response.manutencoes
                } else {
                    erro = response.mensagem ?: "Erro ao carregar manutenções"
                }
            } catch (e: Exception) {
                erro = "Sem conexão com internet"
            }
            carregando = false
        }
    }

    fun executarExclusao() {
        val manutencao = manutencaoParaExcluir ?: return
        scope.launch {
            excluindo = true
            try {
                val request = ExcluirDespesaRequest(

                    motorista_id = motorista?.motorista_id ?: "",
                    id = manutencao.id
                )
                val response = ApiClient.excluirManutencao(request)
                if (response.status == "ok") {
                    mostrarMensagem("✓ " + (response.mensagem ?: "Manutenção excluída com sucesso!"))
                    carregarManutencoes()
                } else {
                    mostrarMensagem(response.mensagem ?: "Erro ao excluir", isErro = true)
                }
            } catch (e: Exception) {
                mostrarMensagem("Erro: ${e.message}", isErro = true)
            }
            excluindo = false
            mostrarDialogoExcluir = false
            manutencaoParaExcluir = null
        }
    }

    LaunchedEffect(Unit) {
        carregarManutencoes()
    }

    if (mostrarDialogoExcluir && manutencaoParaExcluir != null) {
        AlertDialog(
            onDismissRequest = { if (!excluindo) mostrarDialogoExcluir = false },
            icon = { Icon(Icons.Default.Warning, null, tint = AppColors.Error) },
            title = { Text("Confirmar Exclusão", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Tem certeza que deseja excluir esta manutenção?")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Serviço: ${manutencaoParaExcluir?.servico}",
                        color = AppColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { executarExclusao() },
                    enabled = !excluindo,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                ) {
                    if (excluindo) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Excluir")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { mostrarDialogoExcluir = false },
                    enabled = !excluindo
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Minhas Manutenções",
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
                        data.visuals.message.contains("excluída", ignoreCase = true))
                        Color(0xFF10B981) else AppColors.Error,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    actionColor = Color.White
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppColors.Background)
        ) {
            when {
                carregando -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFEF4444))
                        Spacer(Modifier.height(16.dp))
                        Text("Carregando manutenções...", color = AppColors.TextSecondary)
                    }
                }
                erro != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = AppColors.Error,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            erro!!,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.Error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { carregarManutencoes() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) {
                            Text("Tentar Novamente")
                        }
                    }
                }
                manutencoes.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Nenhuma manutenção registrada",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.TextSecondary
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(manutencoes) { manutencao ->
                            ManutencaoCard(
                                manutencao = manutencao,
                                onEditar = { onEditar(manutencao.id, manutencao.viagem_id) },
                                onExcluir = {
                                    manutencaoParaExcluir = manutencao
                                    mostrarDialogoExcluir = true
                                }
                            )
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManutencaoCard(
    manutencao: ManutencaoItem,
    onEditar: () -> Unit,
    onExcluir: () -> Unit
) {
    val (podeEditarExcluir, diasRestantes) = remember(manutencao.data_manutencao) {
        calcularPermissaoEdicao(manutencao.data_manutencao)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF003366))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        manutencao.placa,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        formatarData(manutencao.data_manutencao),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow("Serviço:", manutencao.servico)
                if (manutencao.descricao_servico.isNotBlank()) {
                    InfoRow("Descrição:", manutencao.descricao_servico)
                }
                if (manutencao.local_manutencao.isNotBlank()) {
                    InfoRow("Local:", manutencao.local_manutencao)
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        "R$ ${formatarValor(manutencao.valor)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF003366)
                    )
                }

                if (podeEditarExcluir) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE5E7EB))
                    Spacer(Modifier.height(8.dp))

                    if (diasRestantes in 0..2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (diasRestantes == 0) "Último dia para editar/excluir"
                                else "Faltam $diasRestantes dia(s) para expirar",
                                fontSize = 12.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onEditar) {
                            Icon(
                                Icons.Default.Edit,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFF003366)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Editar", color = Color(0xFF003366))
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onExcluir) {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = AppColors.Error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Excluir", color = AppColors.Error)
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            null,
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Prazo de 5 dias expirado",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF555555),
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            color = AppColors.TextPrimary
        )
    }
}

// Função para calcular permissão de edição usando NSDate (iOS)
private fun calcularPermissaoEdicao(dataManutencao: String): Pair<Boolean, Int> {
    return try {
        // Parsear data no formato yyyy-MM-dd
        val partes = dataManutencao.split("-")
        if (partes.size != 3) return Pair(false, -1)

        val ano = partes[0].toIntOrNull() ?: return Pair(false, -1)
        val mes = partes[1].toIntOrNull() ?: return Pair(false, -1)
        val dia = partes[2].toIntOrNull() ?: return Pair(false, -1)

        // Criar componentes de data
        val components = NSDateComponents()
        components.year = ano.toLong()
        components.month = mes.toLong()
        components.day = dia.toLong()
        components.hour = 0
        components.minute = 0
        components.second = 0

        val calendar = NSCalendar.currentCalendar
        val dataParsed = calendar.dateFromComponents(components)

        if (dataParsed == null) return Pair(false, -1)

        // Adicionar 5 dias
        val componentsAdd = NSDateComponents()
        componentsAdd.day = 5
        val dataLimite = calendar.dateByAddingComponents(componentsAdd, dataParsed, 0u)

        if (dataLimite == null) return Pair(false, -1)

        // Data atual
        val hoje = NSDate()

        // Calcular diferença em dias
        val unitFlags = NSCalendarUnitDay
        val diffComponents = calendar.components(unitFlags, hoje, dataLimite, 0u)
        val diasRestantes = diffComponents.day.toInt()

        // Pode editar se ainda está dentro do prazo (dias >= 0)
        val podeEditar = diasRestantes >= 0

        Pair(podeEditar, if (podeEditar) diasRestantes else -1)
    } catch (e: Exception) {
        Pair(false, -1)
    }
}

private fun formatarData(data: String): String {
    return try {
        val partes = data.split("-")
        if (partes.size == 3) {
            "${partes[2]}/${partes[1]}/${partes[0]}"
        } else data
    } catch (e: Exception) {
        data
    }
}

private fun formatarValor(valor: String): String {
    return try {
        val numero = valor.replace(",", ".").toDoubleOrNull() ?: 0.0
        val inteiro = numero.toInt()
        val decimal = ((numero - inteiro) * 100).toInt()
        val inteiroFormatado = inteiro.toString().reversed().chunked(3).joinToString(".").reversed()
        "$inteiroFormatado,${decimal.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        valor
    }
}