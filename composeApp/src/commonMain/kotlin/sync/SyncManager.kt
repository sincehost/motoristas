package sync

import api.*
import database.AppRepository
import br.com.lfsystem.app.database.Motorista
import util.LogWriter
import kotlinx.datetime.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor

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

        /**
         * Uma sincronização por vez no processo inteiro — a tela inicial e o
         * envio em segundo plano (Android) usam instâncias diferentes de
         * SyncManager. Duas ao mesmo tempo mandariam o mesmo lançamento duas
         * vezes.
         */
        private val trava = Mutex()
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

                if (util.isErroDeAutenticacao(msg)) {
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
     * Ordem: Viagens → Atualizar IDs → Abastecimentos → ARLAs → Fretes
     *        → Descargas → Manutenções → Outras Despesas → Checklists
     *        → Finalizações (sempre por último)
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
        if (!trava.tryLock()) {
            LogWriter.log("⏳ Sincronização já em andamento em outro ponto do app — ignorando")
            if (!silencioso) onStatusUpdate(SyncStatus(state = SyncState.SYNCING, message = "Sincronização em andamento..."))
            return
        }
        try {
            coroutineScope {
                // O trabalho pesado (ler lançamentos com fotos do banco, montar
                // o JSON) roda fora da thread da tela; os avisos de progresso
                // voltam pro dispatcher de quem chamou (a tela, na thread principal).
                val despachanteDoChamador = coroutineContext[ContinuationInterceptor] ?: Dispatchers.Default
                withContext(Dispatchers.Default) {
                    executarSincronizacao(motorista, silencioso) { status ->
                        this@coroutineScope.launch(despachanteDoChamador) { onStatusUpdate(status) }
                    }
                }
            }
        } finally {
            trava.unlock()
        }
    }

    private suspend fun executarSincronizacao(
        motorista: Motorista?,
        silencioso: Boolean,
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
        // PASSO 0: FINALIZAÇÕES JÁ PRONTAS (viagem com id do servidor e sem
        // lançamento pendente). Têm que sair ANTES das viagens novas: com a
        // anterior ainda aberta no servidor, a nova é recusada ("Você já tem
        // uma viagem em andamento"). As que ainda esperam lançamentos ficam
        // pro último passo.
        // ========================================
        val finalizacoesJaTentadas = mutableSetOf<Long>()
        for (finalizacao in repository.getFinalizacoesParaSincronizar()) {
            if (repository.idViagemNoServidor(finalizacao.viagem_id) <= 0) continue
            if (repository.temLancamentosPendentesDaViagem(finalizacao.viagem_id)) continue
            finalizacoesJaTentadas += finalizacao.id
            val resultado = sincronizarFinalizacao(finalizacao, motorista)
            if (resultado.sucesso) sucessos++ else erros.add(resultado.erro)
            progresso++
            onStatusUpdate(SyncStatus(state = SyncState.SYNCING, progress = progresso, total = totalItens))
        }

        // ========================================
        // PASSO 1: SINCRONIZAR VIAGENS PRIMEIRO
        // ========================================
        val progressoAntesViagens = progresso
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
                message = "Viagens: ${progresso - progressoAntesViagens}/${viagens.size}",
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

                // Dependentes de viagem offline guardam viagem_id = -idLocal
                // (formato do ViagemAtual offline). Antes também trocava os
                // que tinham viagem_id = +idLocal, mas nenhuma tela grava
                // assim — e um id positivo é id de viagem do servidor: numa
                // empresa com ids baixos, movia lançamentos de outra viagem.
                val idNegativo = -idLocal
                repository.atualizarViagemIdEmDependentes(idNegativo, idServidor)
                LogWriter.log("   ✅ Atualizados dependentes com viagem_id=$idNegativo -> $idServidor")

                // A viagem em andamento também troca pro id do servidor — senão
                // o que for lançado depois (até o painel recarregar com sinal)
                // ainda sai com o id provisório.
                repository.atualizarIdViagemAtual(idNegativo, idServidor)
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
                message = "Abastecimentos: ${progresso - progressoAntesViagens - viagens.size}/${abastecimentos.size}",
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
                message = "ARLAS: ${progresso - progressoAntesViagens - viagens.size - abastecimentos.size}/${arlas.size}",
                progress = progresso,
                total = totalItens
            ))
        }

        // ========================================
        // PASSO 4b: SINCRONIZAR FRETES (Rota Contínua)
        // ========================================
        val fretes = repository.getFretesParaSincronizar()
        LogWriter.log("📤 Sincronizando ${fretes.size} fretes...")
        for (frete in fretes) {
            val resultado = sincronizarFrete(frete, motorista, null)
            if (resultado.sucesso) sucessos++ else erros.add(resultado.erro)
            progresso++
            onStatusUpdate(SyncStatus(state = SyncState.SYNCING, progress = progresso, total = totalItens))
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
        // PASSO 7: SINCRONIZAR OUTRAS DESPESAS
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
        // PASSO 8: SINCRONIZAR CHECKLISTS PRÉ-VIAGEM
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
        // PASSO 9: SINCRONIZAR CHECKLISTS PÓS-VIAGEM
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
        // PASSO 10: SINCRONIZAR FINALIZAÇÕES — sempre por último. O servidor
        // recusa qualquer lançamento numa viagem já finalizada, então a
        // finalização só sai quando nada daquela viagem estiver pendente
        // (inclusive o que falhou agora há pouco por sinal fraco).
        // ========================================
        val finalizacoes = repository.getFinalizacoesParaSincronizar().filter { it.id !in finalizacoesJaTentadas }
        LogWriter.log("📤 Sincronizando ${finalizacoes.size} finalizações...")
        for (finalizacao in finalizacoes) {
            val resultado = if (repository.temLancamentosPendentesDaViagem(finalizacao.viagem_id)) {
                LogWriter.log("  ⏳ Finalização da viagem ${finalizacao.viagem_id} aguardando lançamentos pendentes")
                repository.marcarFinalizacaoErro(finalizacao.id, util.MSG_FINALIZACAO_AGUARDANDO_LANCAMENTOS)
                SyncItemResult(sucesso = false, erro = "Finalização: ${util.MSG_FINALIZACAO_AGUARDANDO_LANCAMENTOS}")
            } else {
                sincronizarFinalizacao(finalizacao, motorista)
            }
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

        // Mostra o motivo real do primeiro erro na tela (não só a contagem) —
        // sem isso não tem como o motorista (ou quem estiver testando) saber
        // POR QUE algo não sincronizou sem acesso aos logs/Xcode.
        val estadoFinal = when {
            erros.isEmpty() -> SyncStatus(
                state = SyncState.SUCCESS,
                message = "$sucessos ${if (sucessos == 1) "item sincronizado" else "itens sincronizados"}",
                lastSync = agora
            )
            sucessos > 0 -> SyncStatus(
                state = SyncState.ERROR,
                message = "$sucessos OK, ${erros.size} ${if (erros.size == 1) "erro" else "erros"}: ${erros.first()}",
                lastSync = agora
            )
            else -> SyncStatus(
                state = SyncState.ERROR,
                message = erros.first(),
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
            // Chamada leve (index.php) — antes baixava o catálogo inteiro
            // (sync.php) só pra saber se tinha internet, a cada sincronização.
            LogWriter.log("🌐 Verificando conectividade...")
            ApiClient.ping()
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

                    // Já finalizada no celular, finalização ainda na fila
                    // (esperando lançamentos subirem): o servidor ainda a vê
                    // aberta, mas pro motorista ela acabou — não reabrir.
                    if (repository.temFinalizacaoPendenteDaViagem(viagem.id.toLong())) {
                        LogWriter.log("⏳ Viagem ${viagem.id} aberta no servidor, mas finalização está na fila — não reabrindo")
                        repository.limparViagemAtual()
                        onLoading(false)
                        onModoOffline(false)
                        onSemViagem()
                        return
                    }

                    // Preservar km_inicio local se API retornar vazio
                    val viagemLocalExistente = repository.getViagemAtual()
                    val kmInicioParaSalvar = if (viagem.km_inicio.isNotEmpty()) {
                        viagem.km_inicio
                    } else {
                        viagemLocalExistente?.km_inicio ?: ""
                    }

                    // Preservar km_rota que já estava salvo localmente
                    val kmRotaExistente = viagemLocalExistente?.km_rota ?: "0"

                    repository.salvarViagemAtualComComposicao(
                        viagemId = viagem.id.toLong(),
                        destino = viagem.destino,
                        dataInicio = viagem.data,
                        kmInicio = kmInicioParaSalvar,
                        kmRota = kmRotaExistente,
                        placa = viagem.placa,
                        veiculoId = viagem.veiculo_id?.toLong(),
                        implemento1Placa = viagem.implemento1_placa,
                        implemento2Placa = viagem.implemento2_placa
                    )
                    onLoading(false)
                    onModoOffline(false)
                    onViagemEncontrada(viagem.id.toLong(), viagem.destino)

                    // Catálogos: no máximo uma vez por hora (ver CatalogoSync)
                    CatalogoSync.atualizar(repository)
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
            } else {
                // status != "ok" (ex: motorista_id vazio) — resposta válida,
                // não uma exceção, então o catch abaixo nunca rodava e o
                // card "Verificando viagem..." ficava girando pra sempre.
                // Mesmo fallback do catch: usa dado local se tiver.
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
                repository.getFretesParaSincronizar().size +
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
                    // destino_id 0 é a sentinela de Rota Contínua (ver
                    // IniciarViagemScreen) — o servidor recusa 0, então manda
                    // null + o nome digitado como destino_livre, igual ao
                    // envio online.
                    destino_id = viagem.destino_id.toInt().takeIf { it > 0 },
                    destino_livre = if (viagem.destino_id <= 0L) viagem.destino_nome else null,
                    data_viagem = viagem.data_viagem,
                    km_inicio = viagem.km_inicio,
                    placa = viagem.placa,
                    veiculo_id = viagem.veiculo_id?.toInt(),
                    implemento1_id = viagem.implemento1_id?.toInt(),
                    implemento2_id = viagem.implemento2_id?.toInt(),
                    pesocarga = viagem.pesocarga,
                    valorfrete = viagem.valorfrete,
                    foto_painel_saida = viagem.foto_painel_saida
                )
            )}

            LogWriter.log("📥 RESPOSTA SERVIDOR VIAGEM:")
            LogWriter.log("   status: ${resp.status}")
            LogWriter.log("   mensagem: ${resp.mensagem ?: "sem mensagem"}")
            LogWriter.log("   viagem_id: ${resp.viagem_id ?: "NULL"}")

            // Reenvio depois de tempo esgotado: a 1ª tentativa chegou e foi
            // salva, a resposta é que não voltou. O servidor responde "já foi
            // cadastrada" sem dizer o id — sem ele, a viagem e tudo lançado
            // nela ficavam presos pra sempre. Como o motorista só pode ter uma
            // viagem aberta, a aberta dele com a mesma placa e data é esta.
            val idJaCadastrada = if (resp.status != "ok" && resp.mensagem?.contains("já foi cadastrada", ignoreCase = true) == true)
                buscarViagemAbertaIgual(viagem, motorista) else null
            if (idJaCadastrada != null) {
                LogWriter.log("✅ Viagem ${viagem.id} já estava no servidor (id $idJaCadastrada) — reaproveitando")
            }

            if (resp.status == "ok" || idJaCadastrada != null) {
                repository.marcarViagemSincronizada(viagem.id)
                repository.marcarViagemErro(viagem.id, null)
                val viagemIdServidor = idJaCadastrada ?: resp.viagem_id?.toLong()

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
                repository.marcarViagemErro(viagem.id, resp.mensagem)
                SyncItemResult(sucesso = false, erro = "Viagem: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            LogWriter.log("❌ EXCEÇÃO SINCRONIZAR VIAGEM:")
            LogWriter.log("   ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            repository.marcarViagemErro(viagem.id, util.mensagemErroAmigavel(e.message))
            SyncItemResult(sucesso = false, erro = "Viagem: ${util.mensagemErroAmigavel(e.message)}")
        }
    }

    /** Id da viagem aberta do motorista no servidor com a mesma placa e data
     * desta viagem local, ou null se não houver / sem conexão. */
    private suspend fun buscarViagemAbertaIgual(
        viagem: br.com.lfsystem.app.database.Viagem,
        motorista: Motorista?
    ): Long? = try {
        ApiClient.abastecimentoDados(AbastecimentoDadosRequest(motorista_id = motorista?.motorista_id ?: ""))
            .viagens
            .firstOrNull { it.placa == viagem.placa && it.data.take(10) == viagem.data_viagem.take(10) }
            ?.id?.toLong()
    } catch (e: Exception) {
        LogWriter.log("⚠️ Não deu pra conferir viagem já cadastrada: ${e.message}")
        null
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

            val placaResolvida = try {
                repository.getAllEquipamentos()
                    .firstOrNull { it.servidor_id == abastecimento.equipamento_id }
                    ?.placa ?: ""
            } catch (_: Exception) { "" }

            // Envia os valores mascarados em BR ("15,00") direto, igual todo
            // outro campo de valor do app (ARLA, manutenção, etc.) — é o
            // servidor que converte pra decimal. Converter aqui ANTES de
            // enviar fazia o servidor reprocessar um valor que já não tinha
            // vírgula decimal, inflando em 100x (15,00 virava "15.00" aqui,
            // depois o servidor removia o "." como se fosse separador de
            // milhar, virando 1500). valor_litro agora vem do campo que o
            // motorista realmente digitou (persistido localmente), não mais
            // calculado a partir de valor/litros.
            val valorLitroParaEnviar = abastecimento.valor_litro.ifBlank {
                // Fallback pra registros antigos sincronizados antes dessa
                // coluna existir: estima a partir de valor/litros.
                try {
                    val v = abastecimento.valor.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                    val l = abastecimento.litros.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (l > 0) ((v / l * 100).toLong() / 100.0).toString().replace(".", ",") else "0,00"
                } catch (_: Exception) { "0,00" }
            }

            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            LogWriter.log("📤 SINCRONIZANDO ABASTECIMENTO - ID: ${abastecimento.id}")
            LogWriter.log("   viagem_id: $viagemIdParaEnviar")
            LogWriter.log("   equipamento_id: ${abastecimento.equipamento_id} -> placa: '$placaResolvida'")
            LogWriter.log("   tipo_combustivel: ${abastecimento.tipo_combustivel}")
            LogWriter.log("   tipo_pagamento: ${abastecimento.tipo_pagamento}")
            LogWriter.log("   litros: '${abastecimento.litros}'")
            LogWriter.log("   valor: '${abastecimento.valor}'")
            LogWriter.log("   valor_litro: '$valorLitroParaEnviar'")

            val resp = comRetry("Abastecimento") { ApiClient.salvarAbastecimento(
                SalvarAbastecimentoRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    viagem_id = viagemIdParaEnviar.toInt(),
                    data = abastecimento.data_,
                    placa = placaResolvida,
                    nome_posto = abastecimento.posto,
                    tipo_combustivel = abastecimento.tipo_combustivel,
                    // A API valida tipo_pagamento contra formas_pagamento.codigo, que é
                    // sempre minúsculo ("ctf", "dinheiro") — NÃO uppercase aqui, senão
                    // o servidor rejeita com "forma de pagamento inválida".
                    tipo_pagamento = abastecimento.tipo_pagamento,
                    litros_abastecidos = abastecimento.litros,
                    valor_litro = valorLitroParaEnviar,
                    valor = abastecimento.valor,
                    km_posto = abastecimento.km_posto,
                    horas = abastecimento.horas,
                    cupom_fiscal_base64 = abastecimento.foto,
                    marcador_base64 = abastecimento.foto_marcador
                )
            )}

            LogWriter.log("📥 RESPOSTA SERVIDOR ABASTECIMENTO:")
            LogWriter.log("   status: ${resp.status}")
            LogWriter.log("   mensagem: ${resp.mensagem ?: "sem mensagem"}")
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            if (resp.status == "ok") {
                repository.marcarAbastecimentoSincronizado(abastecimento.id)
                repository.marcarAbastecimentoErro(abastecimento.id, null)
                SyncItemResult(sucesso = true)
            } else {
                repository.marcarAbastecimentoErro(abastecimento.id, resp.mensagem)
                SyncItemResult(sucesso = false, erro = "Abastecimento: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            LogWriter.log("❌ EXCEÇÃO SINCRONIZAR ABASTECIMENTO:")
            LogWriter.log("   ${e::class.simpleName}: ${e.message}")
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            repository.marcarAbastecimentoErro(abastecimento.id, util.mensagemErroAmigavel(e.message))
            SyncItemResult(sucesso = false, erro = "Abastecimento: ${util.mensagemErroAmigavel(e.message)}")
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
                repository.marcarArlaErro(arla.id, null)
                SyncItemResult(sucesso = true)
            } else {
                LogWriter.log("❌ ARLA REJEITADA: ${resp.mensagem}")
                repository.marcarArlaErro(arla.id, resp.mensagem)
                SyncItemResult(sucesso = false, erro = "ARLA: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            LogWriter.log("❌ EXCEÇÃO SINCRONIZAR ARLA:")
            LogWriter.log("   ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            repository.marcarArlaErro(arla.id, util.mensagemErroAmigavel(e.message))
            SyncItemResult(sucesso = false, erro = "ARLA: ${util.mensagemErroAmigavel(e.message)}")
        }
    }

    private suspend fun sincronizarFrete(
        frete: br.com.lfsystem.app.database.Frete,
        motorista: Motorista?,
        fallbackViagemId: Long? = null
    ): SyncItemResult {
        return try {
            val viagemIdParaEnviar = resolverViagemId(frete.viagem_id, fallbackViagemId, "FRETE")

            if (viagemIdParaEnviar <= 0) {
                LogWriter.log("  ⏳ FRETE viagem_id ainda não resolvido ($viagemIdParaEnviar), aguardando próximo sync")
                return SyncItemResult(sucesso = false, erro = "Frete: aguardando viagem ser sincronizada primeiro")
            }

            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            LogWriter.log("📤 SINCRONIZANDO FRETE - ID: ${frete.id}")
            LogWriter.log("   viagem_id: $viagemIdParaEnviar")
            LogWriter.log("   tipo: ${frete.tipo}")
            LogWriter.log("   numero_identificacao: ${frete.numero_identificacao}")
            LogWriter.log("   valor: ${frete.valor}")

            val resp = comRetry("FRETE") { ApiClient.salvarFrete(
                SalvarFreteRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    viagem_id = viagemIdParaEnviar.toInt(),
                    tipo = frete.tipo,
                    numero_identificacao = frete.numero_identificacao,
                    valor = frete.valor,
                    foto_base64 = frete.foto
                )
            )}

            LogWriter.log("📥 RESPOSTA SERVIDOR FRETE:")
            LogWriter.log("   status: ${resp.status}")
            LogWriter.log("   mensagem: ${resp.mensagem ?: "sem mensagem"}")
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            if (resp.status == "ok") {
                repository.marcarFreteSincronizado(frete.id)
                repository.marcarFreteErro(frete.id, null)
                SyncItemResult(sucesso = true)
            } else {
                LogWriter.log("❌ FRETE REJEITADO: ${resp.mensagem}")
                repository.marcarFreteErro(frete.id, resp.mensagem)
                SyncItemResult(sucesso = false, erro = "Frete: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            LogWriter.log("❌ EXCEÇÃO SINCRONIZAR FRETE:")
            LogWriter.log("   ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            LogWriter.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            repository.marcarFreteErro(frete.id, util.mensagemErroAmigavel(e.message))
            SyncItemResult(sucesso = false, erro = "Frete: ${util.mensagemErroAmigavel(e.message)}")
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
                repository.marcarDescargaErro(descarga.id, null)
                SyncItemResult(sucesso = true)
            } else {
                repository.marcarDescargaErro(descarga.id, resp.mensagem)
                SyncItemResult(sucesso = false, erro = "Descarga: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            repository.marcarDescargaErro(descarga.id, util.mensagemErroAmigavel(e.message))
            SyncItemResult(sucesso = false, erro = "Descarga: ${util.mensagemErroAmigavel(e.message)}")
        }
    }

    private suspend fun sincronizarManutencao(
        manutencao: br.com.lfsystem.app.database.Manutencao,
        motorista: Motorista?
    ): SyncItemResult {
        return try {
            // viagem_id 0 = manutenção lançada sem viagem aberta — o servidor
            // aceita assim (manutencao/salvar.php não exige viagem). Antes caía
            // no fallback "última viagem do servidor" e a manutenção ia parar
            // numa viagem que não tinha nada a ver (ou travava se não houvesse
            // nenhuma).
            // Sem plano B "última viagem do servidor": se a viagem offline
            // ainda não subiu, a manutenção espera em vez de cair noutra viagem.
            val semViagem = manutencao.viagem_id == 0L
            val viagemIdParaEnviar = if (semViagem) 0L
                else resolverViagemId(manutencao.viagem_id, null, "Manutenção")

            if (!semViagem && viagemIdParaEnviar <= 0) {
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
                    tipo_manutencao = manutencao.tipo_manutencao,
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
                repository.marcarManutencaoErro(manutencao.id, null)
                SyncItemResult(sucesso = true)
            } else {
                repository.marcarManutencaoErro(manutencao.id, resp.mensagem)
                SyncItemResult(sucesso = false, erro = "Manutenção: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            repository.marcarManutencaoErro(manutencao.id, util.mensagemErroAmigavel(e.message))
            SyncItemResult(sucesso = false, erro = "Manutenção: ${util.mensagemErroAmigavel(e.message)}")
        }
    }

    private suspend fun sincronizarFinalizacao(
        finalizacao: br.com.lfsystem.app.database.FinalizacaoViagem,
        motorista: Motorista?
    ): SyncItemResult {
        return try {
            val teveRetorno = finalizacao.teve_retorno == 1L

            // Resolve viagem_id negativo para o ID real do servidor. Sem plano
            // B "última viagem do servidor": com a viagem offline ainda sem
            // subir, ele mandava a finalização pra viagem ANTERIOR — o servidor
            // dizia "já foi finalizada", ela era marcada como enviada e sumia.
            val viagemIdOriginal = finalizacao.viagem_id
            val viagemIdParaEnviar = resolverViagemId(viagemIdOriginal, null, "Finalização")

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
                repository.marcarFinalizacaoErro(finalizacao.id, null)
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
                    repository.marcarFinalizacaoErro(finalizacao.id, null)
                    SyncItemResult(sucesso = true)
                } else {
                    // Para qualquer outro erro (incluindo "não encontrada"),
                    // NÃO marcar como sincronizado — tentar novamente no próximo ciclo
                    LogWriter.log("  ⏳ Erro não permanente, tentará novamente no próximo sync")
                    repository.marcarFinalizacaoErro(finalizacao.id, msg)
                    SyncItemResult(sucesso = false, erro = "Finalização: $msg")
                }
            }
        } catch (e: Exception) {
            LogWriter.log("  ❌ Exception na sync: ${e.message}")
            repository.marcarFinalizacaoErro(finalizacao.id, util.mensagemErroAmigavel(e.message))
            SyncItemResult(sucesso = false, erro = "Finalização: ${util.mensagemErroAmigavel(e.message)}")
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
                    repository.marcarOutraDespesaErro(despesa.id, null)
                    SyncItemResult(sucesso = true)
                } else {
                    repository.marcarOutraDespesaErro(despesa.id, resp.mensagem)
                    SyncItemResult(sucesso = false, erro = "OutraDespesa: ${resp.mensagem}")
                }
            } catch (e: Exception) {
                LogWriter.log("⚠️ Falha ao enviar outra despesa, mantendo para próxima sync: ${e.message}")
                repository.marcarOutraDespesaErro(despesa.id, util.mensagemErroAmigavel(e.message))
                SyncItemResult(sucesso = false, erro = "OutraDespesa: ${util.mensagemErroAmigavel(e.message)}")
            }
        } catch (e: Exception) {
            repository.marcarOutraDespesaErro(despesa.id, util.mensagemErroAmigavel(e.message))
            SyncItemResult(sucesso = false, erro = "OutraDespesa: ${util.mensagemErroAmigavel(e.message)}")
        }
    }

    private suspend fun sincronizarChecklistPre(
        checklist: br.com.lfsystem.app.database.ChecklistPreViagem,
        motorista: Motorista?
    ): SyncItemResult {
        return try {
            // Mesma resolução de id dos outros lançamentos — checklist feito
            // numa viagem iniciada offline sai com o id provisório (negativo).
            val viagemIdParaEnviar = resolverViagemId(checklist.viagem_id, null, "ChecklistPré")
            if (viagemIdParaEnviar <= 0) {
                LogWriter.log("  ⏳ ChecklistPré viagem_id ainda não resolvido ($viagemIdParaEnviar), aguardando próximo sync")
                return SyncItemResult(sucesso = false, erro = "ChecklistPré: aguardando viagem ser sincronizada primeiro")
            }
            val resp = comRetry("ChecklistPré") { ApiClient.salvarChecklistPre(
                SalvarChecklistPreRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    viagem_id = viagemIdParaEnviar.toInt(),
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
                repository.marcarChecklistPreErro(checklist.id, null)
                SyncItemResult(sucesso = true)
            } else {
                repository.marcarChecklistPreErro(checklist.id, resp.mensagem)
                SyncItemResult(sucesso = false, erro = "ChecklistPré: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            repository.marcarChecklistPreErro(checklist.id, util.mensagemErroAmigavel(e.message))
            SyncItemResult(sucesso = false, erro = "ChecklistPré: ${util.mensagemErroAmigavel(e.message)}")
        }
    }

    private suspend fun sincronizarChecklistPos(
        checklist: br.com.lfsystem.app.database.ChecklistPosViagem,
        motorista: Motorista?
    ): SyncItemResult {
        return try {
            val viagemIdParaEnviar = resolverViagemId(checklist.viagem_id, null, "ChecklistPós")
            if (viagemIdParaEnviar <= 0) {
                LogWriter.log("  ⏳ ChecklistPós viagem_id ainda não resolvido ($viagemIdParaEnviar), aguardando próximo sync")
                return SyncItemResult(sucesso = false, erro = "ChecklistPós: aguardando viagem ser sincronizada primeiro")
            }
            val resp = comRetry("ChecklistPós") { ApiClient.salvarChecklistPos(
                SalvarChecklistPosRequest(
                    motorista_id = motorista?.motorista_id ?: "",
                    viagem_id = viagemIdParaEnviar.toInt(),
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
                repository.marcarChecklistPosErro(checklist.id, null)
                SyncItemResult(sucesso = true)
            } else {
                repository.marcarChecklistPosErro(checklist.id, resp.mensagem)
                SyncItemResult(sucesso = false, erro = "ChecklistPós: ${resp.mensagem}")
            }
        } catch (e: Exception) {
            repository.marcarChecklistPosErro(checklist.id, util.mensagemErroAmigavel(e.message))
            SyncItemResult(sucesso = false, erro = "ChecklistPós: ${util.mensagemErroAmigavel(e.message)}")
        }
    }
}
