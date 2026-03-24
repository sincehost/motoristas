package sync

import database.AppRepository
import util.LogWriter

/**
 * FotoOfflineManager — expect/actual
 *
 * A declaração expect fica em commonMain.
 * A implementação actual (com android.graphics.Bitmap etc.) fica em androidMain.
 *
 * NOTA: Atualmente não é referenciado por nenhuma tela, mas a estrutura
 * está pronta para quando for necessário usar fotos offline.
 */
expect class FotoOfflineManager(repository: AppRepository) {

    /**
     * Salvar foto já em Base64
     */
    suspend fun salvarFotoBase64(
        viagemId: Long,
        tipo: String,
        base64: String,
        latitude: Double?,
        longitude: Double?
    ): Long?

    /**
     * Obter fotos não sincronizadas
     */
    suspend fun obterFotosNaoSincronizadas(viagemId: Long): List<FotoOffline>

    /**
     * Marcar como sincronizada
     */
    suspend fun marcarSincronizada(fotoId: Long)

    /**
     * Deletar foto
     */
    suspend fun deletarFoto(fotoId: Long)
}

// ============= DATA CLASSES =============

data class FotoOffline(
    val id: Long,
    val viagemId: Long,
    val tipo: String,
    val imagemBase64: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sincronizada: Boolean = false,
    val dataCriacao: Long = 0L
)

data class OperacaoPendente(
    val id: Long,
    val tipo: String,
    val dados: String,
    val status: String,
    val tentativas: Int,
    val fotoIds: List<Long> = emptyList(),
    val dataCriacao: Long = 0L
)
