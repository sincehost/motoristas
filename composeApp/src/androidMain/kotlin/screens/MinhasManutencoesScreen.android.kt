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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.*
import database.AppRepository
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import ui.AppColors
import ui.GradientTopBar
import java.text.SimpleDateFormat
import java.util.*

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
    val context = LocalContext.current
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()

    var carregando by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var todasManutencoes by remember { mutableStateOf<List<ManutencaoItem>>(emptyList()) }
    
    // Paginação MANUAL (no app, não na API)
    var paginaAtual by remember { mutableStateOf(1) }
    val itensPorPagina = 10
    
    var mostrarDialogoExcluir by remember { mutableStateOf(false) }
    var manutencaoParaExcluir by remember { mutableStateOf<ManutencaoItem?>(null) }
    var excluindo by remember { mutableStateOf(false) }
    var mensagemSucesso by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Calcular paginação manual
    val totalPaginas = remember(todasManutencoes) {
        if (todasManutencoes.isEmpty()) 1
        else (todasManutencoes.size + itensPorPagina - 1) / itensPorPagina
    }
    
    val manutencoesVisiveis = remember(todasManutencoes, paginaAtual) {
        val inicio = (paginaAtual - 1) * itensPorPagina
        val fim = minOf(inicio + itensPorPagina, todasManutencoes.size)
        if (inicio < todasManutencoes.size) {
            todasManutencoes.subList(inicio, fim)
        } else {
            emptyList()
        }
    }

    fun carregarManutencoes() {
        scope.launch {
            carregando = true
            erro = null
            try {
                // Tenta chamar com page primeiro
                val response = try {
                    ApiClient.getManutencoes(motorista?.motorista_id ?: "", 1)
                } catch (e: Exception) {
                    // Se falhar, tenta sem page (API antiga)
                    println("Erro ao chamar com page, tentando sem: ${e.message}")
                    ApiClient.getManutencoes(motorista?.motorista_id ?: "")
                }
                
                if (response.status == "ok") {
                    // Ordenar por data mais recente primeiro
                    todasManutencoes = response.manutencoes.sortedByDescending { it.data_manutencao }
                    paginaAtual = 1 // Resetar para primeira página
                } else {
                    erro = response.mensagem ?: "Erro ao carregar manutenções"
                }
            } catch (e: Exception) {
                println("Erro ao carregar manutenções: ${e.message}")
                e.printStackTrace()
                erro = "Erro ao carregar manutenções: ${e.message}"
            }
            carregando = false
            isRefreshing = false
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
                    mensagemSucesso = response.mensagem ?: "Manutenção excluída com sucesso!"
                    carregarManutencoes()
                } else {
                    erro = response.mensagem ?: "Erro ao excluir"
                }
            } catch (e: Exception) {
                erro = "Erro: ${e.message}"
            }
            excluindo = false
            mostrarDialogoExcluir = false
            manutencaoParaExcluir = null
        }
    }

    LaunchedEffect(Unit) {
        carregarManutencoes()
    }

    mensagemSucesso?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            mensagemSucesso = null
        }
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
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Excluir")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoExcluir = false }, enabled = !excluindo) {
                    Text("Cancelar")
                }
            }
        )
    }

    val pullRefreshState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Minhas Manutenções",
                onBackClick = onVoltar
            )
        }
    )
    { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                carregarManutencoes()
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
        ) {
            // Card de informações de paginação
            if (!carregando && erro == null && todasManutencoes.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, null, tint = AppColors.Primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Total: ${todasManutencoes.size}", fontWeight = FontWeight.Medium)
                        }
                        Text(
                            "Pág $paginaAtual/$totalPaginas",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }

            when {
                carregando -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = AppColors.Primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Carregando manutenções...", color = AppColors.TextSecondary)
                        }
                    }
                }
                erro != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
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
                                color = AppColors.Error,
                                textAlign = TextAlign.Center,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = { carregarManutencoes() },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Tentar novamente")
                            }
                        }
                    }
                }
                todasManutencoes.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Nenhuma manutenção encontrada",
                                color = AppColors.TextSecondary,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(manutencoesVisiveis) { manutencao ->
                            ManutencaoCard(
                                manutencao = manutencao,
                                onEditar = { onEditar(manutencao.id, manutencao.viagem_id) },
                                onExcluir = {
                                    manutencaoParaExcluir = manutencao
                                    mostrarDialogoExcluir = true
                                }
                            )
                        }
                    }

                    // Controles de paginação
                    if (totalPaginas > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { paginaAtual-- },
                                enabled = paginaAtual > 1,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) {
                                Icon(Icons.Default.ArrowBack, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Anterior")
                            }
                            
                            Spacer(Modifier.width(16.dp))
                            
                            Button(
                                onClick = { paginaAtual++ },
                                enabled = paginaAtual < totalPaginas,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) {
                                Text("Próxima")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowForward, null)
                            }
                        }
                    }
                }
            }

            // Mensagem de sucesso
            mensagemSucesso?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ui.isDark()) AppColors.SurfaceVariant else AppColors.Secondary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = AppColors.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            msg,
                            fontSize = 13.sp,
                            color = AppColors.Secondary
                        )
                    }
                }
            }
        }
        } // PullToRefreshBox
    }
}

@Composable
private fun ManutencaoCard(
    manutencao: ManutencaoItem,
    onEditar: () -> Unit,
    onExcluir: () -> Unit
) {
    val podeEditarExcluir = remember(manutencao.data_manutencao) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dataManutencao = sdf.parse(manutencao.data_manutencao)
            if (dataManutencao != null) {
                val calendario = Calendar.getInstance()
                calendario.time = dataManutencao
                calendario.add(Calendar.DAY_OF_MONTH, 5)
                val dataLimite = calendario.time
                val hoje = Date()
                hoje.before(dataLimite) || hoje == dataLimite
            } else false
        } catch (e: Exception) {
            false
        }
    }

    val diasRestantes = remember(manutencao.data_manutencao) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dataManutencao = sdf.parse(manutencao.data_manutencao)
            if (dataManutencao != null) {
                val calendario = Calendar.getInstance()
                calendario.time = dataManutencao
                calendario.add(Calendar.DAY_OF_MONTH, 5)
                val dataLimite = calendario.time
                val hoje = Date()
                val diff = dataLimite.time - hoje.time
                val dias = (diff / (1000 * 60 * 60 * 24)).toInt()
                if (dias >= 0) dias else -1
            } else -1
        } catch (e: Exception) {
            -1
        }
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
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
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

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onEditar) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = Color(0xFF003366))
                            Spacer(Modifier.width(4.dp))
                            Text("Editar", color = Color(0xFF003366))
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onExcluir) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = AppColors.Error)
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
                        Icon(Icons.Default.Lock, null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp))
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
        String.format("%,.2f", numero).replace(",", "X").replace(".", ",").replace("X", ".")
    } catch (e: Exception) {
        valor
    }
}
