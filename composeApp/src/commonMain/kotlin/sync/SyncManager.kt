package sync

import api.*
import database.AppRepository
import br.com.lfsystem.app.database.Motorista
import util.LogWriter
import kotlinx.datetime.Clock

// ===============================
// DATA CLASSES DE SINCRONIZAÇÃO
// ===============================

enum class SyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR,
    NO_INTERNET
}

data class SyncStatus(
    val state: SyncState = SyncState.IDLE,
    val message: String = "",
    val progress: Int = 0,
    val total: Int = 0,
    val lastSync: Long = 0L
)

data class SyncItemResult(
    val sucesso: Boolean,
    val erro: String = "",
    val viagemIdServidor: Long? = null
)

/**
 * SyncManager — Gerencia TODA a lógica de sincronização.
 *
 * Antes, essa lógica ficava espalhada dentro do DashboardScreen (1800+ linhas).
 * Agora está centralizada aqui, com API limpa para o Dashboard consumir.
 *
 * Uso:
 *   val syncManager = SyncManager(repository)
 *   syncManager.sincronizar(motorista, silencioso = false) { status -> ... }
 */
class SyncManager(private val repository: AppRepository) {

    companion object {
        private const val MAX_RETRIES = 3
        private val BACKOFF_DELAYS = longArrayOf(1000L, 2000L, 4000L) // 1s, 2s, 4s
    }

    /**
     * Executa um bloco com retry e backoff exponencial.
     * Só tenta novamente em erros de rede (timeout, connection refused).
     * Erros de lógica (400, 404) não são retriados.
     */
    private suspend fun <T> comRetry(label: String, block: suspend () -> T): T {
        var ultimoErro: Exception? = null
        for (tentativa in 0 until MAX_RETRIES) {
            try {
                return block()
            } catch (e: Exception) {
                val msg = e.message ?: ""

                // Detectar token expirado/inválido
                val isAuthError = msg.contains("401", ignoreCase = true) ||
                        msg.contains("unauthorized", ignoreCase = true) ||
                        msg.contains("token", ignoreCase = true) && msg.contains("invalid", ignoreCase = true)
                if (isAuthError) {
                    LogWriter.log("🔒 $label: Token expirado/inválido — requer re-login")
                    screens.AppEvents.emitir(screens.AppEvent.TokenExpirado)
                    throw e // Não fazer retry em erro de autenticação
                }

                val isRedeErro = msg.contains("timeout", ignoreCase = true) ||
                        msg.contains("Unable to resolve", ignoreCase = true) ||
                        msg.contains("Connection refused", ignoreCase = true) ||
                        msg.contains("Network is unreachable", ignoreCase = true) ||
                        msg.contains("connect timed out", ignoreCase = true) ||
                        msg.contains("SocketTimeout", ignoreCase = true)
                if (!isRedeErro || tentativa == MAX_RETRIES - 1) throw e
                ultimoErro = e
                val delay = BACKOFF_DELAYS.getOrElse(tentativa) { 4000L }
                LogWriter.log("   ⏳ $label: retry ${tentativa + 1}/$MAX_RETRIES em ${delay}ms (${e.message})")
                kotlinx.coroutines.delay(delay)
            }
        }
        throw ultimoErro ?: Exception("Retry esgotado para $label")
    }

    // ===============================
    // SINCRONIZAÇÃO PRINCIPAL
    // ===============================

    /**
     * Executa sincronização completa de todos os dados pendentes.
     * Ordem: Viagens → Atualizar IDs → Abastecimentos → ARLAs → Descargas
     *        → Manutenções → Finalizações → Outras Despesas → Checklists
     *
     * @param motorista Motorista logado (nullable para segurança)
     * @param silencioso Se true, não emite status SUCCESS quando não há pendentes
     * @param onStatusUpdate Callback para atualizar UI com progresso
     */
    suspend fun sincronizar(
        motorista: Motorista?,
        silencioso: Boolean = false,
        onStatusUpdate: (SyncStatus) -> Unit
    ) {
        LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        LogWriter.log("🔄 SINCRONIZAÇÃO INICIADA (via SyncManager)")
        LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val agora = Clock.System.now().toEpochMilliseconds()
        val temInternet = verificarConectividade()

        if (!temInternet) {
            LogWriter.log("❌ SEM INTERNET - Sincronização cancelada")
            onStatusUpdate(SyncStatus(
                state = SyncState.NO_INTERNET,
                message = "Conecte-se à internet para sincronizar",
                lastSync = agora
            ))
            return
        }

        LogWriter.log("✅ Internet disponível - Prosseguindo...")

        val totalItens = contarTotalPendentes()

        if (totalItens == 0) {
            LogWriter.log("ℹ️ Nenhum item para sincronizar")
            if (!silencioso) {
                onStatusUpdate(SyncStatus(
                    state = SyncState.SUCCESS,
                    message = "Tudo sincronizado!",
                    lastSync = agora
                ))
            }
            return
        }

        LogWriter.log("📊 Total de itens para sincronizar: $totalItens")

        onStatusUpdate(SyncStatus(
            state = SyncState.SYNCING,
            message = "Conectando ao servidor...",
            progress = 0,
            total = totalItens,
            lastSync = agora
        ))

        var progresso = 0
        var sucessos = 0
        val erros = mutableListOf<String>()

        // MAPA PARA ARMAZENAR ID LOCAL -> ID SERVIDOR
        val mapaIdsViagens = mutableMapOf<Long, Long>()

        // ========================================
        // PASSO 1: SINCRONIZAR VIAGENS PRIMEIRO
        // ========================================
        val viagens = repository.getViagensParaSincronizar()
        LogWriter.log("📤 Sincronizando ${viagens.size} viagens...")
        for (viagem in viagens) {
            val resultado = sincronizarViagem(viagem, motorista)
            if (resultado.sucesso) {
                sucessos++
                if (resultado.viagemIdServidor != null) {
                    mapaIdsViagens[viagem.id] = resultado.viagemIdServidor
                    repository.salvarUltimaViagemServidor(resultado.viagemIdServidor)
                    LogWriter.log("✅ Viagem sincronizada: ID Local ${viagem.id} -> ID Servidor ${resultado.viagemIdServidor}")
                }
            } else {
                erros.add(resultado.erro)
            }
            progresso++
            onStatusUpdate(SyncStatus(
                state = SyncState.SYNCING,
                message = "Viagens: $progresso/${viagens.size}",
                progress = progresso,
                total = totalItens
            ))
        }

        // ========================================
        // PASSO 2: ATUALIZAR IDs DOS DEPENDENTES
        // ========================================
        if (mapaIdsViagens.isNotEmpty()) {
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            LogWriter.log("🔄 ATUALIZANDO IDs DE VIAGENS NOS DEPENDENTES")
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            for ((idLocal, idServidor) in mapaIdsViagens) {
                LogWriter.log("   📝 Atualizando: $idLocal -> $idServidor")

                // CORREÇÃO: Dependentes podem ter viagem_id = idLocal (positivo)
                // OU viagem_id = -idLocal (negativo, formato usado pelo ViagemAtual offline).
                // Precisamos atualizar AMBOS os formatos.

                // 1. Atualizar itens com viagem_id = idLocal (formato da tabela Viagem)
                repository.atualizarViagemIdEmDependentes(idLocal, idServidor)
                LogWriter.log("   ✅ Atualizados dependentes com viagem_id=$idLocal -> $idServidor")

                // 2. Atualizar itens com viagem_id = -idLocal (formato negativo do ViagemAtual offline)
                val idNegativo = -idLocal
                repository.atualizarViagemIdEmDependentes(idNegativo, idServidor)
                LogWriter.log("   ✅ Atualizados dependentes com viagem_id=$idNegativo -> $idServidor")
            }

            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        } else {
            LogWriter.log("⚠️ Nenhum mapeamento de IDs para atualizar!")
        }

        // CORREÇÃO C3: Removido fallback para ultimoIdServidor.
        // Antes, itens com viagem_id inválido eram associados à "última viagem
        // do servidor", que podia ser a viagem ERRADA se o motorista já fez
        // várias viagens. Agora, itens não resolvidos ficam pendentes e o
        // resolverViagemId() tenta resolver via mapeamento persistido.
        // Se não resolver, o item fica para o próximo ciclo de sync.
        LogWriter.log("mapaIdsViagens = $mapaIdsViagens")

        // ========================================
        // PASSO 3: SINCRONIZAR ABASTECIMENTOS
        // ========================================
        val abastecimentos = repository.getAbastecimentosParaSincronizar()
        LogWriter.log("📤 Sincronizando ${abastecimentos.size} abastecimentos...")
        for (abastecimento in abastecimentos) {
            val resultado = sincronizarAbastecimento(abastecimento, motorista, null)
            if (resultado.sucesso) sucessos++ else erros.add(resultado.erro)
            progresso++
            onStatusUpdate(SyncStatus(
                state = SyncState.SYNCING,
                message = "Abastecimentos: ${progresso - viagens.size}/${abastecimentos.size}",
                progress = progresso,
                total = totalItens
            ))
        }

        // ========================================
        // PASSO 4: SINCRONIZAR ARLAS
        // ========================================
        val arlas = repository.getArlasParaSincronizar()
        LogWriter.log("📤 Sincronizando ${arlas.size} ARLAS...")
        for (arla in arlas) {
            val resultado = sincronizarArla(arla, motorista, null)
            if (resultado.sucesso) sucessos++ else erros.add(resultado.erro)
            progresso++
            onStatusUpdate(SyncStatus(
                state = SyncState.SYNCING,
                message = "ARLAS: ${progresso - viagens.size - abastecimentos.size}/${arlas.size}",
                progress = progresso,
                total = totalItens
            ))
        }

        // ========================================
        // PASSO 5: SINCRONIZAR DESCARGAS
        // ========================================
        val descargas = repository.getDescargasParaSincronizar()
        LogWriter.log("📤 Sincronizando ${descargas.size} descargas...")
        for (descarga in descargas) {
            val resultado = sincronizarDescarga(descarga, motorista, null)
            if (resultado.sucesso) sucessos++ else erros.add(resultado.erro)
            progresso++
            onStatusUpdate(SyncStatus(state = SyncState.SYNCING, progress = progresso, total = totalItens))
        }

        // ========================================
        // PASSO 6: SINCRONIZAR MANUTENÇÕES
        // ========================================
        val manutencoes = repository.getManutencoesParaSincronizar()
        LogWriter.log("📤 Sincronizando ${manutencoes.size} manutenções...")
        for (manutencao in manutencoes) {
            val resultado = sincronizarManutencao(manutencao, motorista)
            if (resultado.sucesso) sucessos++ else erros.add(resultado.erro)
            progresso++
            onStatusUpdate(SyncStatus(state = SyncState.SYNCING, progress = progresso, total = totalItens))
        }

        // ========================================
        // PASSO 7: SINCRONIZAR FINALIZAÇÕES
        // ========================================
        val finalizacoes = repository.getFinalizacoesParaSincronizar()
        LogWriter.log("📤 Sincronizando ${finalizacoes.size} finalizações...")
        for (finalizacao in finalizacoes) {
            val resultado = sincronizarFinalizacao(finalizacao, motorista)
            if (resultado.sucesso) sucessos++ else erros.add(resultado.erro)
            progresso++
            onStatusUpdate(SyncStatus(state = SyncState.SYNCING, progress = progresso, total = totalItens))
        }

        // ========================================
        // PASSO 8: SINCRONIZAR OUTRAS DESPESAS
        // ========================================
        val outrasDespesas = repository.getOutrasDespesasParaSincronizar()
        LogWriter.log("📤 Sincronizando ${outrasDespesas.size} outras despesas...")
        for (despesa in outrasDespesas) {
            val resultado = sincronizarOutraDespesa(despesa, motorista, null)
            if (resultado.sucesso) sucessos++ else erros.add(resultado.erro)
            progresso++
            onStatusUpdate(SyncStatus(state = SyncState.SYNCING, progress = progresso, total = totalItens))
        }

        // ========================================
        // PASSO 9: SINCRONIZAR CHECKLISTS PRÉ-VIAGEM
        // ========================================
        val checklistsPre = repository.getChecklistsPreParaSincronizar()
        LogWriter.log("📤 Sincronizando ${checklistsPre.size} checklists pré-viagem...")
        for (checklist in checklistsPre) {
            val resultado = sincronizarChecklistPre(checklist, motorista)
            if (resultado.sucesso) sucessos++ else erros.add(resultado.erro)
            progresso++
            onStatusUpdate(SyncStatus(state = SyncState.SYNCING, progress = progresso, total = totalItens))
        }

        // ========================================
        // PASSO 10: SINCRONIZAR CHECKLISTS PÓS-VIAGEM
        // ========================================
        val checklistsPos = repository.getChecklistsPosParaSincronizar()
        LogWriter.log("📤 Sincronizando ${checklistsPos.size} checklists pós-viagem...")
        for (checklist in checklistsPos) {
            val resultado = sincronizarChecklistPos(checklist, motorista)
            if (resultado.sucesso) sucessos++ else erros.add(resultado.erro)
            progresso++
            onStatusUpdate(SyncStatus(state = SyncState.SYNCING, progress = progresso, total = totalItens))
        }

        // ========================================
        // PASSO 11: LIMPAR SINCRONIZADOS
        // Só limpa se TODOS foram sincronizados com sucesso.
        // Se houve erros, mantém tudo para tentar novamente.
        // ========================================
        if (erros.isEmpty()) {
            limparSincronizados()
            LogWriter.log("🧹 Itens sincronizados limpos com sucesso")
        } else {
            LogWriter.log("⚠️ ${erros.size} erros encontrados — NÃO limpando itens sincronizados para evitar perda de dados")
        }

        LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        LogWriter.log("✅ Sucessos: $sucessos")
        LogWriter.log("❌ Erros: ${erros.size}")
        LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val estadoFinal = when {
            erros.isEmpty() -> SyncStatus(
                state = SyncState.SUCCESS,
                message = "$sucessos ${if (sucessos == 1) "item sincronizado" else "itens sincronizados"}",
                lastSync = agora
            )
            sucessos > 0 -> SyncStatus(
                state = SyncState.ERROR,
                message = "$sucessos OK, ${erros.size} ${if (erros.size == 1) "erro" else "erros"}",
                lastSync = agora
            )
            else -> SyncStatus(
                state = SyncState.ERROR,
                message = "Falha ao sincronizar. Dados salvos para nova tentativa.",
                lastSync = agora
            )
        }

        onStatusUpdate(estadoFinal)
    }

    // ===============================
    // VERIFICAÇÃO DE CONECTIVIDADE
    // ===============================

    suspend fun verificarConectividade(): Boolean {
        return try {
            LogWriter.log("🌐 Verificando conectividade...")
            ApiClient.syncDados()
            LogWriter.log("✅ Internet OK")
            true
        } catch (e: Exception) {
            val semInternet = e.message?.contains("Unable to resolve host") == true ||
                    e.message?.contains("No address associated with hostname") == true ||
                    e.message?.contains("Network is unreachable") == true ||
                    e.message?.contains("Connection refused") == true

            if (semInternet) {
                LogWriter.log("❌ Sem internet: ${e.message}")
            } else {
                LogWriter.log("⚠️ Erro na API: ${e.message}")
            }

            !semInternet
        }
    }

    // ===============================
    // VERIFICAÇÃO DE VIAGEM ATUAL
    // ===============================

    /**
     * Verifica se há viagem em andamento (API com fallback local).
     * Usado pelo Dashboard para mostrar status da viagem.
     */
    suspend fun verificarViagemAtual(
        motorista: Motorista?,
        onLoading: (Boolean) -> Unit,
        onModoOffline: (Boolean) -> Unit,
        onViagemEncontrada: (Long, String) -> Unit,
        onSemViagem: () -> Unit
    ) {
        onLoading(true)
        onModoOffline(false)

        if (motorista == null) {
            onLoading(false)
            onSemViagem()
            return
        }

        try {
            val response = ApiClient.abastecimentoDados(
                AbastecimentoDadosRequest(motorista_id = motorista.motorista_id)
            )

            if (response.status == "ok") {
                if (response.viagens.isNotEmpty()) {
                    val viagem = response.viagens.first()

                    // Preservar km_inicio local se API retornar vazio
                    val viagemLocalExistente = repository.getViagemAtual()
                    val kmInicioParaSalvar = if (viagem.km_inicio.isNotEmpty()) {
                        viagem.km_inicio
                    } else {
                        viagemLocalExistente?.km_inicio ?: ""
                    }

                    // Preservar km_rota que já estava salvo localmente
                    val kmRotaExistente = viagemLocalExistente?.km_rota ?: "0"

                    repository.salvarViagemAtual(
                        viagemId = viagem.id.toLong(),
                        destino = viagem.destino,
                        dataInicio = viagem.data,
                        kmInicio = kmInicioParaSalvar,
                        kmRota = kmRotaExistente
                    )
                    onLoading(false)
                    onModoOffline(false)
                    onViagemEncontrada(viagem.id.toLong(), viagem.destino)

                    // Tenta sincronizar destinos/equipamentos em background
                    try {
                        val syncResp = ApiClient.syncDados()
                        if (syncResp.status == "ok") {
                            repository.salvarDestinos(syncResp.destinos.map { it.id to it.nome })
                            repository.salvarEquipamentos(syncResp.equipamentos.map { it.id to it.placa })
                        }
                    } catch (_: Exception) { }
                    return
                } else {
                    val viagemLocal = repository.getViagemAtual()
                    if (viagemLocal != null) {
                        // NÃO limpar se há finalização pendente
                        // Verificar com viagem_id local E com o ID positivo equivalente
                        val viagemIdLocal = viagemLocal.viagem_id
                        val viagemIdPositivo = if (viagemIdLocal < 0) -viagemIdLocal else viagemIdLocal
                        val finalizacoesPendentes = repository.getFinalizacoesParaSincronizar()
                        val temFinalizacaoPendente = finalizacoesPendentes.any {
                            it.viagem_id == viagemIdLocal ||
                            it.viagem_id == viagemIdPositivo ||
                            (viagemIdLocal < 0 && repository.getMapeamentoIdServidor(viagemIdPositivo)?.let { idSrv -> it.viagem_id == idSrv } == true)
                        }

                        if (temFinalizacaoPendente) {
                            LogWriter.log("⚠️ API sem viagem aberta, mas há finalização pendente local (viagem_id=$viagemIdLocal). Mantendo ViagemAtual.")
                            onLoading(false)
                            onModoOffline(false)
                            onViagemEncontrada(viagemLocal.viagem_id, viagemLocal.destino)
                            return
                        }

                        // Não limpar se há viagem offline pendente de sync
                        val temViagemPendente = repository.getViagensParaSincronizar().isNotEmpty()
                        if (temViagemPendente) {
                            LogWriter.log("⚠️ API sem viagem aberta, mas há viagem pendente de sync. Mantendo ViagemAtual.")
                            onLoading(false)
                            onModoOffline(false)
                            onViagemEncontrada(viagemLocal.viagem_id, viagemLocal.destino)
                            return
                        }

                        // Seguro limpar
                        LogWriter.log("🧹 API sem viagem aberta e sem pendências locais. Limpando ViagemAtual.")
                        repository.limparViagemAtual()
                    }
                    onLoading(false)
                    onModoOffline(false)
                    onSemViagem()
                    return
                }
            }
        } catch (e: Exception) {
            // Fallback offline
            val viagemLocal = repository.getViagemAtual()
            if (viagemLocal != null) {
                onLoading(false)
                onModoOffline(true)
                onViagemEncontrada(viagemLocal.viagem_id, viagemLocal.destino)
            } else {
                onLoading(false)
                onModoOffline(false)
                onSemViagem()
            }
        }
    }

    // ===============================
    // CONTAGEM DE PENDENTES
    // ===============================

    fun contarTotalPendentes(): Int {
        return repository.getViagensParaSincronizar().size +
                repository.getAbastecimentosParaSincronizar().size +
                repository.getArlasParaSincronizar().size +
                repository.getDescargasParaSincronizar().size +
                repository.getManutencoesParaSincronizar().size +
                repository.getFinalizacoesParaSincronizar().size +
                repository.getOutrasDespesasParaSincronizar().size +
                repository.getChecklistsPreParaSincronizar().size +
                repository.getChecklistsPosParaSincronizar().size
    }

    // ===============================
    // LIMPEZA DE ITENS SINCRONIZADOS
    // ===============================

    // CORREÇÃO C2: Limpeza atômica — todas as tabelas numa única transação SQL.
    // Antes, cada DELETE rodava separado. Se o app travasse no meio (bateria,
    // kill forçado), ficava em estado inconsistente (ex: viagens limpas mas
    // abastecimentos ainda presentes). Agora é tudo-ou-nada.
    private fun limparSincronizados() {
        repository.limparTodosSincronizadosAtomicamente()
    }

    // ===============================
    // RESOLVER viagem_id (lógica unificada)
    // ===============================

    /**
     * Resolve o viagem_id real para enviar ao servidor.
     * Prioridade: valor válido > mapeamento persistido > fallback.
     *
     * Essa lógica antes era copiada em cada função de sync individual.
     */
    private fun resolverViagemId(viagemIdLocal: Long, fallbackViagemId: Long?, label: String): Long {
        return when {
            viagemIdLocal > 0 -> viagemIdLocal
            viagemIdLocal < 0 -> {
                // ID local negativo — buscar mapeamento persistido
                val idMapeado = repository.getMapeamentoIdServidor(-viagemIdLocal)
                if (idMapeado != null) {
                    LogWriter.log("   ✅ $label viagem_id resolvido pelo mapeamento persistido: $viagemIdLocal -> $idMapeado")
                    idMapeado
                } else if (fallbackViagemId != null) {
                    LogWriter.log("   ⚠️ $label viagem_id ($viagemIdLocal), usando fallback: $fallbackViagemId")
                    fallbackViagemId
                } else {
                    viagemIdLocal
                }
            }
            else -> {
                // viagem_id = 0
                if (fallbackViagemId != null) {
                    LogWriter.log("   ⚠️ $label viagem_id inválido ($viagemIdLocal), usando fallback: $fallbackViagemId")
                    fallbackViagemId
                } else {
                    viagemIdLocal
                }
            }
        }
    }

    // ===============================
    // FUNÇÕES DE SINCRONIZAÇÃO INDIVIDUAL
    // ===============================

    private suspend fun sincronizarViagem(
        viagem: br.com.lfsystem.app.database.Viagem,
        motorista: Motorista?
    ): SyncItemResult {
        return try {
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            LogWriter.log("📤 SINCRONIZANDO VIAGEM - ID LOCAL: ${viagem.id}")
            LogWriter.log("   numerobd: ${viagem.numerobd}")
            LogWriter.log("   destino_id: ${viagem.destino_id}")

            val resp = comRetry("Viagem ${viagem.id}") {
                ApiClient.inserirViagem(
                ViagemRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    numerobd = viagem.numerobd,
                    cte = viagem.cte,
                    numerobd2 = viagem.numerobd2,
                    cte2 = viagem.cte2,
                    destino_id = viagem.destino_id.toInt(),
                    data_viagem = viagem.data_viagem,
                    km_inicio = viagem.km_inicio,
                    placa = viagem.placa,
                    pesocarga = viagem.pesocarga,
                    valorfrete = viagem.valorfrete,
                    foto_painel_saida = viagem.foto_painel_saida
                )
            )}

            LogWriter.log("📥 RESPOSTA SERVIDOR VIAGEM:")
            LogWriter.log("   status: ${resp.status}")
            LogWriter.log("   mensagem: ${resp.mensagem ?: "sem mensagem"}")
            LogWriter.log("   viagem_id: ${resp.viagem_id ?: "NULL"}")

            if (resp.status == "ok") {
                repository.marcarViagemSincronizada(viagem.id)
                val viagemIdServidor = resp.viagem_id?.toLong()

                if (viagemIdServidor != null) {
                    // Salvar mapeamento idLocal -> idServidor
                    // resolverViagemId() usa -viagemIdLocal para buscar, então salvar com viagem.id é suficiente
                    repository.salvarMapeamentoViagem(idLocal = viagem.id, idServidor = viagemIdServidor)
                    repository.salvarUltimaViagemServidor(viagemIdServidor)
                    LogWriter.log("✅ VIAGEM SINCRONIZADA: ID Local ${viagem.id} -> ID Servidor $viagemIdServidor")
                } else {
                    LogWriter.log("⚠️ AVISO: Viagem sincronizada mas servidor NÃO retornou viagem_id!")
                }
                LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                SyncItemResult(sucesso = true, viagemIdServidor = viagemIdServidor)
            } else {
                LogWriter.log("❌ VIAGEM REJEITADA: ${resp.mensagem}")
                LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                SyncItemResult(sucesso = false, erro = "Viagem: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            LogWriter.log("❌ EXCEÇÃO SINCRONIZAR VIAGEM:")
            LogWriter.log("   ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            SyncItemResult(sucesso = false, erro = "Viagem: ${e.message}")
        }
    }

    private suspend fun sincronizarAbastecimento(
        abastecimento: br.com.lfsystem.app.database.Abastecimento,
        motorista: Motorista?,
        fallbackViagemId: Long? = null
    ): SyncItemResult {
        return try {
            val viagemIdParaEnviar = resolverViagemId(abastecimento.viagem_id, fallbackViagemId, "Abastecimento")

            if (viagemIdParaEnviar <= 0) {
                LogWriter.log("  ⏳ Abastecimento viagem_id ainda não resolvido ($viagemIdParaEnviar), aguardando próximo sync")
                return SyncItemResult(sucesso = false, erro = "Abastecimento: aguardando viagem ser sincronizada primeiro")
            }

            val resp = comRetry("Abastecimento") { ApiClient.salvarAbastecimento(
                SalvarAbastecimentoRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    viagem_id = viagemIdParaEnviar.toInt(),
                    data = abastecimento.data_,
                    placa = try {
                        repository.getViagemAtual()?.let { "" } ?: ""
                    } catch (_: Exception) { "" },
                    nome_posto = abastecimento.posto,
                    tipo_combustivel = abastecimento.tipo_combustivel,
                    tipo_pagamento = abastecimento.tipo_pagamento.uppercase(),
                    litros_abastecidos = abastecimento.litros,
                    valor_litro = try {
                        val v = abastecimento.valor.replace(",", ".").toDoubleOrNull() ?: 0.0
                        val l = abastecimento.litros.replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (l > 0) ((v / l * 100).toLong() / 100.0).toString() else "0.00"
                    } catch (_: Exception) { "0.00" },
                    valor = abastecimento.valor,
                    km_posto = abastecimento.km_posto,
                    horas = abastecimento.horas,
                    cupom_fiscal_base64 = abastecimento.foto,
                    marcador_base64 = abastecimento.foto_marcador
                )
            )}

            if (resp.status == "ok") {
                repository.marcarAbastecimentoSincronizado(abastecimento.id)
                SyncItemResult(sucesso = true)
            } else {
                SyncItemResult(sucesso = false, erro = "Abastecimento: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            SyncItemResult(sucesso = false, erro = "Abastecimento: ${e.message}")
        }
    }

    private suspend fun sincronizarArla(
        arla: br.com.lfsystem.app.database.Arla,
        motorista: Motorista?,
        fallbackViagemId: Long? = null
    ): SyncItemResult {
        return try {
            val viagemIdParaEnviar = resolverViagemId(arla.viagem_id, fallbackViagemId, "ARLA")

            if (viagemIdParaEnviar <= 0) {
                LogWriter.log("  ⏳ ARLA viagem_id ainda não resolvido ($viagemIdParaEnviar), aguardando próximo sync")
                return SyncItemResult(sucesso = false, erro = "ARLA: aguardando viagem ser sincronizada primeiro")
            }

            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            LogWriter.log("📤 SINCRONIZANDO ARLA - ID: ${arla.id}")
            LogWriter.log("   viagem_id: $viagemIdParaEnviar")
            LogWriter.log("   data: ${arla.data_}")
            LogWriter.log("   valor: ${arla.valor}")
            LogWriter.log("   litros: ${arla.litros}")
            LogWriter.log("   posto: ${arla.posto}")
            LogWriter.log("   km_posto: ${arla.km_posto}")

            val resp = comRetry("ARLA") { ApiClient.salvarArla(
                SalvarArlaRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    viagem_id = viagemIdParaEnviar.toInt(),
                    data = arla.data_,
                    valor = arla.valor,
                    litros = arla.litros,
                    posto = arla.posto,
                    km_posto = arla.km_posto,
                    foto_base64 = arla.foto
                )
            )}

            LogWriter.log("📥 RESPOSTA SERVIDOR ARLA:")
            LogWriter.log("   status: ${resp.status}")
            LogWriter.log("   mensagem: ${resp.mensagem ?: "sem mensagem"}")
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            if (resp.status == "ok") {
                repository.marcarArlaSincronizada(arla.id)
                SyncItemResult(sucesso = true)
            } else {
                LogWriter.log("❌ ARLA REJEITADA: ${resp.mensagem}")
                SyncItemResult(sucesso = false, erro = "ARLA: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            LogWriter.log("❌ EXCEÇÃO SINCRONIZAR ARLA:")
            LogWriter.log("   ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            SyncItemResult(sucesso = false, erro = "ARLA: ${e.message}")
        }
    }

    private suspend fun sincronizarDescarga(
        descarga: br.com.lfsystem.app.database.Descarga,
        motorista: Motorista?,
        fallbackViagemId: Long? = null
    ): SyncItemResult {
        return try {
            val viagemIdParaEnviar = resolverViagemId(descarga.viagem_id, fallbackViagemId, "Descarga")

            if (viagemIdParaEnviar <= 0) {
                LogWriter.log("  ⏳ Descarga viagem_id ainda não resolvido ($viagemIdParaEnviar), aguardando próximo sync")
                return SyncItemResult(sucesso = false, erro = "Descarga: aguardando viagem ser sincronizada primeiro")
            }

            val resp = comRetry("Descarga") { ApiClient.salvarDescarga(
                SalvarDescargaRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    viagem_id = viagemIdParaEnviar.toInt(),
                    data = descarga.data_,
                    placa = descarga.placa,
                    ordem_descarga = descarga.ordem_descarga.toInt(),
                    valor = descarga.valor,
                    foto = descarga.foto
                )
            )}

            if (resp.status == "ok") {
                repository.marcarDescargaSincronizada(descarga.id)
                SyncItemResult(sucesso = true)
            } else {
                SyncItemResult(sucesso = false, erro = "Descarga: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            SyncItemResult(sucesso = false, erro = "Descarga: ${e.message}")
        }
    }

    private suspend fun sincronizarManutencao(
        manutencao: br.com.lfsystem.app.database.Manutencao,
        motorista: Motorista?
    ): SyncItemResult {
        return try {
            val viagemIdParaEnviar = resolverViagemId(manutencao.viagem_id, repository.getUltimaViagemServidor(), "Manutenção")

            if (viagemIdParaEnviar <= 0) {
                LogWriter.log("  ⏳ Manutenção viagem_id ainda não resolvido ($viagemIdParaEnviar), aguardando próximo sync")
                return SyncItemResult(sucesso = false, erro = "Manutenção: aguardando viagem ser sincronizada primeiro")
            }

            val resp = comRetry("Manutenção") { ApiClient.salvarManutencao(
                SalvarManutencaoRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    viagem_id = viagemIdParaEnviar.toInt(),
                    data_manutencao = manutencao.data_manutencao,
                    placa = manutencao.placa,
                    servico = manutencao.servico,
                    descricao_servico = manutencao.descricao_servico,
                    local_manutencao = manutencao.local_manutencao,
                    valor = manutencao.valor,
                    km_troca_oleo = manutencao.km_troca_oleo,
                    km_troca_pneu = manutencao.km_troca_pneu,
                    pneus = manutencao.pneus?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
                    tipos_pneu = manutencao.tipos_pneu?.split(";")?.mapNotNull {
                        val parts = it.split(":")
                        if (parts.size == 2) {
                            parts[0].trim().toIntOrNull()?.let { key -> key to parts[1].trim() }
                        } else null
                    }?.toMap() ?: emptyMap(),
                    foto_comprovante1 = manutencao.foto_comprovante1,
                    foto_comprovante2 = manutencao.foto_comprovante2
                )
            )}

            if (resp.status == "ok") {
                repository.marcarManutencaoSincronizada(manutencao.id)
                SyncItemResult(sucesso = true)
            } else {
                SyncItemResult(sucesso = false, erro = "Manutenção: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            SyncItemResult(sucesso = false, erro = "Manutenção: ${e.message}")
        }
    }

    private suspend fun sincronizarFinalizacao(
        finalizacao: br.com.lfsystem.app.database.FinalizacaoViagem,
        motorista: Motorista?
    ): SyncItemResult {
        return try {
            val teveRetorno = finalizacao.teve_retorno == 1L

            // CORREÇÃO: Resolver viagem_id negativo para o ID real do servidor
            // Antes, enviava finalizacao.viagem_id direto (que pode ser -1, -2, etc.)
            val viagemIdOriginal = finalizacao.viagem_id
            val viagemIdParaEnviar = resolverViagemId(viagemIdOriginal, repository.getUltimaViagemServidor(), "Finalização")

            LogWriter.log("━━━ SYNC FINALIZAÇÃO ━━━")
            LogWriter.log("  viagem_id original: $viagemIdOriginal")
            LogWriter.log("  viagem_id resolvido: $viagemIdParaEnviar")
            LogWriter.log("  motorista_id: ${motorista?.motorista_id}")
            LogWriter.log("  km_chegada: ${finalizacao.km_chegada}")
            LogWriter.log("  data_chegada: ${finalizacao.data_chegada}")
            LogWriter.log("  teve_retorno: $teveRetorno")
            LogWriter.log("  peso_retorno: ${finalizacao.pesocarga_retorno}")
            LogWriter.log("  valor_retorno: ${finalizacao.valorfrete_retorno}")
            LogWriter.log("  local_carregou: ${finalizacao.local_carregou}")
            LogWriter.log("  ordem_retorno: ${finalizacao.ordem_retorno}")
            LogWriter.log("  cte_retorno: ${finalizacao.cte_retorno}")
            LogWriter.log("  foto: ${if (finalizacao.foto_painel_chegada != null) "${finalizacao.foto_painel_chegada!!.length} chars" else "null"}")

            // Se ainda não conseguiu resolver para um ID válido, não enviar
            if (viagemIdParaEnviar <= 0) {
                LogWriter.log("  ⏳ viagem_id ainda não resolvido ($viagemIdParaEnviar), aguardando próximo ciclo de sync")
                return SyncItemResult(sucesso = false, erro = "Finalização: aguardando viagem ser sincronizada primeiro")
            }

            val resp = comRetry("Finalização") { ApiClient.finalizarViagem(
                FinalizarViagemRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    viagem_id = viagemIdParaEnviar.toInt(),
                    data_chegada = finalizacao.data_chegada,
                    km_chegada = finalizacao.km_chegada,
                    teve_retorno = teveRetorno,
                    peso_carga_retorno = if (teveRetorno) finalizacao.pesocarga_retorno else null,
                    valor_frete_retorno = if (teveRetorno) finalizacao.valorfrete_retorno else null,
                    local_carregou = if (teveRetorno) finalizacao.local_carregou else null,
                    ordem_retorno = if (teveRetorno) finalizacao.ordem_retorno else null,
                    cte_retorno = if (teveRetorno) finalizacao.cte_retorno else null,
                    observacao = finalizacao.observacao,
                    foto_painel_base64 = finalizacao.foto_painel_chegada
                )
            )}

            LogWriter.log("  API response: status=${resp.status}, msg=${resp.mensagem}")

            if (resp.status == "ok") {
                repository.marcarFinalizacaoSincronizada(finalizacao.id)
                LogWriter.log("  ✅ Finalização sincronizada com sucesso!")
                SyncItemResult(sucesso = true)
            } else {
                val msg = resp.mensagem ?: ""
                LogWriter.log("  ❌ API retornou erro: $msg")

                // Só marcar como sincronizado se a viagem já foi finalizada no servidor
                // (significa que alguém já finalizou, então não precisamos reenviar)
                if (msg.contains("já foi finalizada", ignoreCase = true)) {
                    LogWriter.log("  ⚠️ Viagem já finalizada no servidor — marcando como sincronizada")
                    repository.marcarFinalizacaoSincronizada(finalizacao.id)
                    SyncItemResult(sucesso = true)
                } else {
                    // Para qualquer outro erro (incluindo "não encontrada"),
                    // NÃO marcar como sincronizado — tentar novamente no próximo ciclo
                    LogWriter.log("  ⏳ Erro não permanente, tentará novamente no próximo sync")
                    SyncItemResult(sucesso = false, erro = "Finalização: $msg")
                }
            }
        } catch (e: Exception) {
            LogWriter.log("  ❌ Exception na sync: ${e.message}")
            SyncItemResult(sucesso = false, erro = "Finalização: ${e.message}")
        }
    }

    private suspend fun sincronizarOutraDespesa(
        despesa: br.com.lfsystem.app.database.OutraDespesa,
        motorista: Motorista?,
        fallbackViagemId: Long? = null
    ): SyncItemResult {
        return try {
            val viagemIdParaEnviar = resolverViagemId(despesa.viagem_id, fallbackViagemId, "OutraDespesa")

            if (viagemIdParaEnviar <= 0) {
                LogWriter.log("  ⏳ OutraDespesa viagem_id ainda não resolvido ($viagemIdParaEnviar), aguardando próximo sync")
                return SyncItemResult(sucesso = false, erro = "OutraDespesa: aguardando viagem ser sincronizada primeiro")
            }

            LogWriter.log("📤 Sincronizando OutraDespesa: tipo=${despesa.tipo}, valor=${despesa.valor}, viagem_id=$viagemIdParaEnviar")

            try {
                val resp = comRetry("OutraDespesa") { ApiClient.salvarOutraDespesa(
                    SalvarOutraDespesaRequest(
                        motorista_id = motorista?.motorista_id ?: "",
                        viagem_id = viagemIdParaEnviar.toInt(),
                        tipo = despesa.tipo,
                        descricao = despesa.descricao ?: despesa.tipo,
                        valor = despesa.valor,
                        data = despesa.data_,
                        local = despesa.local_,
                        foto_comprovante = despesa.foto_comprovante
                    )
                )}

                if (resp.status == "ok") {
                    repository.marcarOutraDespesaSincronizada(despesa.id)
                    SyncItemResult(sucesso = true)
                } else {
                    SyncItemResult(sucesso = false, erro = "OutraDespesa: ${resp.mensagem}")
                }
            } catch (e: Exception) {
                LogWriter.log("⚠️ API de outras despesas não disponível, mantendo para próxima sync: ${e.message}")
                SyncItemResult(sucesso = false, erro = "OutraDespesa: API não disponível ainda")
            }
        } catch (e: Exception) {
            SyncItemResult(sucesso = false, erro = "OutraDespesa: ${e.message}")
        }
    }

    private suspend fun sincronizarChecklistPre(
        checklist: br.com.lfsystem.app.database.ChecklistPreViagem,
        motorista: Motorista?
    ): SyncItemResult {
        return try {
            val resp = comRetry("ChecklistPré") { ApiClient.salvarChecklistPre(
                SalvarChecklistPreRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    viagem_id = checklist.viagem_id.toInt(),
                    data_checklist = checklist.data_checklist, placa = checklist.placa,
                    doc_cnh_valida = checklist.doc_cnh_valida.toInt(), doc_crlv_veiculo = checklist.doc_crlv_veiculo.toInt(),
                    doc_antt_valida = checklist.doc_antt_valida.toInt(), doc_seguro_carga = checklist.doc_seguro_carga.toInt(),
                    doc_ordem_coleta = checklist.doc_ordem_coleta.toInt(),
                    elet_farol_dianteiro = checklist.elet_farol_dianteiro.toInt(), elet_farol_traseiro = checklist.elet_farol_traseiro.toInt(),
                    elet_luz_freio = checklist.elet_luz_freio.toInt(), elet_seta_direita = checklist.elet_seta_direita.toInt(),
                    elet_seta_esquerda = checklist.elet_seta_esquerda.toInt(), elet_luz_re = checklist.elet_luz_re.toInt(),
                    elet_painel_funcionando = checklist.elet_painel_funcionando.toInt(),
                    pneu_calibragem_ok = checklist.pneu_calibragem_ok.toInt(), pneu_estado_conservacao = checklist.pneu_estado_conservacao.toInt(),
                    pneu_estepe_ok = checklist.pneu_estepe_ok.toInt(), pneu_ferramentas_troca = checklist.pneu_ferramentas_troca.toInt(),
                    fluido_oleo_motor = checklist.fluido_oleo_motor.toInt(), fluido_agua_radiador = checklist.fluido_agua_radiador.toInt(),
                    fluido_fluido_freio = checklist.fluido_fluido_freio.toInt(), fluido_arla32 = checklist.fluido_arla32.toInt(),
                    fluido_combustivel = checklist.fluido_combustivel.toInt(),
                    seg_extintor_validade = checklist.seg_extintor_validade.toInt(), seg_triangulo = checklist.seg_triangulo.toInt(),
                    seg_macaco_chave_roda = checklist.seg_macaco_chave_roda.toInt(), seg_cones_faixa = checklist.seg_cones_faixa.toInt(),
                    seg_epi_completo = checklist.seg_epi_completo.toInt(),
                    carr_lonas_cordas = checklist.carr_lonas_cordas.toInt(), carr_portas_bau = checklist.carr_portas_bau.toInt(),
                    carr_assoalho_estado = checklist.carr_assoalho_estado.toInt(), carr_travas_lacres = checklist.carr_travas_lacres.toInt(),
                    cab_bancos_cintos = checklist.cab_bancos_cintos.toInt(), cab_espelhos_retrovisores = checklist.cab_espelhos_retrovisores.toInt(),
                    cab_limpador_parabrisa = checklist.cab_limpador_parabrisa.toInt(), cab_ar_condicionado = checklist.cab_ar_condicionado.toInt(),
                    cab_freio_estacionamento = checklist.cab_freio_estacionamento.toInt(),
                    observacoes = checklist.observacoes
                )
            )}
            if (resp.status == "ok") {
                repository.marcarChecklistPreSincronizado(checklist.id)
                SyncItemResult(sucesso = true)
            } else {
                SyncItemResult(sucesso = false, erro = "ChecklistPré: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            SyncItemResult(sucesso = false, erro = "ChecklistPré: ${e.message}")
        }
    }

    private suspend fun sincronizarChecklistPos(
        checklist: br.com.lfsystem.app.database.ChecklistPosViagem,
        motorista: Motorista?
    ): SyncItemResult {
        return try {
            val resp = comRetry("ChecklistPós") { ApiClient.salvarChecklistPos(
                SalvarChecklistPosRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    viagem_id = checklist.viagem_id.toInt(),
                    data_checklist = checklist.data_checklist, placa = checklist.placa,
                    avaria_carroceria = checklist.avaria_carroceria.toInt(), avaria_cabine = checklist.avaria_cabine.toInt(),
                    avaria_pneus = checklist.avaria_pneus.toInt(), avaria_espelhos = checklist.avaria_espelhos.toInt(),
                    avaria_farois = checklist.avaria_farois.toInt(), avaria_descricao = checklist.avaria_descricao,
                    pos_nivel_oleo = checklist.pos_nivel_oleo.toInt(), pos_nivel_agua = checklist.pos_nivel_agua.toInt(),
                    pos_nivel_combustivel = checklist.pos_nivel_combustivel.toInt(), pos_nivel_arla = checklist.pos_nivel_arla.toInt(),
                    limp_cabine_limpa = checklist.limp_cabine_limpa.toInt(), limp_carroceria_limpa = checklist.limp_carroceria_limpa.toInt(),
                    limp_bau_vazio = checklist.limp_bau_vazio.toInt(),
                    func_freios_ok = checklist.func_freios_ok.toInt(), func_direcao_ok = checklist.func_direcao_ok.toInt(),
                    func_suspensao_ok = checklist.func_suspensao_ok.toInt(), func_motor_ruido = checklist.func_motor_ruido.toInt(),
                    func_cambio_ok = checklist.func_cambio_ok.toInt(),
                    pend_manutencao_urgente = checklist.pend_manutencao_urgente.toInt(), pend_descricao_manutencao = checklist.pend_descricao_manutencao,
                    pend_abastecimento_necessario = checklist.pend_abastecimento_necessario.toInt(),
                    pend_troca_oleo_proxima = checklist.pend_troca_oleo_proxima.toInt(), pend_km_atual = checklist.pend_km_atual,
                    observacoes = checklist.observacoes
                )
            )}
            if (resp.status == "ok") {
                repository.marcarChecklistPosSincronizado(checklist.id)
                SyncItemResult(sucesso = true)
            } else {
                SyncItemResult(sucesso = false, erro = "ChecklistPós: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            SyncItemResult(sucesso = false, erro = "ChecklistPós: ${e.message}")
        }
    }
}
