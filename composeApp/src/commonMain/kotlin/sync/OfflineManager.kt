package sync

import database.AppRepository
import br.com.lfsystem.app.database.Motorista
import kotlinx.coroutines.*
import util.LogWriter

/**
 * OfflineManager — Coordena sincronização offline do app.
 *
 * Delega a lógica de sync real para o [SyncManager].
 * Gerencia estado de sync (syncing, pendentes, último sync),
 * controle de concorrência e auto-sync quando internet volta.
 *
 * Uso no DashboardScreen:
 *   val offlineManager = remember { OfflineManager(repository) }
 *   offlineManager.init(scope)
 *   offlineManager.syncPendentes(motorista) { status -> ... }
 */
class OfflineManager(private val repository: AppRepository) {

    private val syncManager = SyncManager(repository)

    // Estado observável
    var isSyncing: Boolean = false
        private set
    var pendingCount: Int = 0
        private set
    var lastSyncTime: Long = 0L
        private set
    var lastError: String? = null
        private set

    // Controle de concorrência
    private var syncJob: Job? = null
    private var syncScope: CoroutineScope? = null

    // ===============================
    // INICIALIZAÇÃO
    // ===============================

    /**
     * Inicializar com escopo de coroutine.
     * Chamar no LaunchedEffect(Unit) do Dashboard.
     */
    fun init(scope: CoroutineScope) {
        this.syncScope = scope
        atualizarContador()
        LogWriter.log("✅ OfflineManager inicializado (pendentes: $pendingCount)")
    }

    // ===============================
    // SINCRONIZAÇÃO
    // ===============================

    /**
     * Sincronizar todos os dados pendentes.
     * Controla concorrência — se já está sincronizando, ignora.
     *
     * @param motorista Motorista logado
     * @param silencioso Se true, não mostra status quando não há pendentes
     * @param onStatusUpdate Callback para atualizar UI
     */
    fun syncPendentes(
        motorista: Motorista?,
        silencioso: Boolean = false,
        onStatusUpdate: (SyncStatus) -> Unit
    ) {
        if (isSyncing) {
            LogWriter.log("⏳ Sync já em andamento — ignorando chamada duplicada")
            if (!silencioso) {
                onStatusUpdate(SyncStatus(
                    state = SyncState.SYNCING,
                    message = "Sincronização em andamento..."
                ))
            }
            return
        }

        val scope = syncScope ?: run {
            LogWriter.log("❌ OfflineManager não inicializado (scope null)")
            return
        }

        syncJob = scope.launch {
            isSyncing = true
            lastError = null

            try {
                syncManager.sincronizar(
                    motorista = motorista,
                    silencioso = silencioso,
                    onStatusUpdate = { status ->
                        // Atualizar estado local
                        if (status.state == SyncState.SUCCESS || status.state == SyncState.ERROR) {
                            lastSyncTime = status.lastSync
                        }
                        if (status.state == SyncState.ERROR) {
                            lastError = status.message
                        }
                        // Propagar para UI
                        onStatusUpdate(status)
                        // Atualizar contador
                        atualizarContador()
                    }
                )
            } catch (e: Exception) {
                LogWriter.log("❌ Exceção no syncPendentes: ${e.message}")
                lastError = e.message
                onStatusUpdate(SyncStatus(
                    state = SyncState.ERROR,
                    message = "Erro inesperado: ${e.message}"
                ))
            } finally {
                isSyncing = false
                atualizarContador()
            }
        }
    }

    // ===============================
    // VERIFICAÇÃO DE VIAGEM
    // ===============================

    /**
     * Verifica viagem em andamento (API + fallback local).
     * Delegado ao SyncManager.
     */
    suspend fun verificarViagemAtual(
        motorista: Motorista?,
        onLoading: (Boolean) -> Unit,
        onModoOffline: (Boolean) -> Unit,
        onViagemEncontrada: (Long, String) -> Unit,
        onSemViagem: () -> Unit
    ) {
        syncManager.verificarViagemAtual(
            motorista = motorista,
            onLoading = onLoading,
            onModoOffline = onModoOffline,
            onViagemEncontrada = onViagemEncontrada,
            onSemViagem = onSemViagem
        )
    }

    // ===============================
    // VERIFICAÇÃO DE CONECTIVIDADE
    // ===============================

    /**
     * Testa se há conexão com o servidor.
     */
    suspend fun verificarConectividade(): Boolean {
        return syncManager.verificarConectividade()
    }

    // ===============================
    // ESTADO
    // ===============================

    /**
     * Atualiza e retorna contagem de pendentes.
     */
    fun refreshPendingCount(): Int {
        atualizarContador()
        return pendingCount
    }

    /**
     * Status completo para UI.
     */
    fun getStatus(): SyncManagerStatus {
        return SyncManagerStatus(
            isSyncing = isSyncing,
            pendingCount = pendingCount,
            lastSyncTime = lastSyncTime,
            lastError = lastError
        )
    }

    // ===============================
    // CANCELAMENTO
    // ===============================

    /**
     * Cancela sync em andamento (ex: ao sair da tela).
     */
    fun cancelSync() {
        syncJob?.cancel()
        isSyncing = false
        LogWriter.log("🛑 Sync cancelado pelo usuário")
    }

    // ===============================
    // INTERNO
    // ===============================

    private fun atualizarContador() {
        try {
            pendingCount = syncManager.contarTotalPendentes()
        } catch (e: Exception) {
            LogWriter.log("❌ Erro ao contar pendentes: ${e.message}")
        }
    }
}

/**
 * Status geral do OfflineManager para consulta externa.
 */
data class SyncManagerStatus(
    val isSyncing: Boolean = false,
    val pendingCount: Int = 0,
    val lastSyncTime: Long = 0L,
    val lastError: String? = null
)
