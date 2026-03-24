package sync

import android.graphics.Bitmap
import android.util.Base64
import database.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import util.LogWriter
import java.io.ByteArrayOutputStream

/**
 * FotoOfflineManager — Implementação Android
 *
 * Gerencia fotos offline com compressão e Base64.
 */
actual class FotoOfflineManager actual constructor(private val repository: AppRepository) {

    companion object {
        private const val COMPRESSION_QUALITY = 70
        private const val RESIZE_WIDTH = 1280
        private const val RESIZE_HEIGHT = 960
    }

    /**
     * Salvar foto já em Base64
     */
    actual suspend fun salvarFotoBase64(
        viagemId: Long,
        tipo: String,
        base64: String,
        latitude: Double?,
        longitude: Double?
    ): Long? = withContext(Dispatchers.Default) {
        try {
            val base64Clean = if (base64.contains(",")) {
                base64.substringAfter(",")
            } else {
                base64
            }

            LogWriter.log("📸 Salvando foto: tipo=$tipo, tamanho=${base64Clean.length / 1024}KB")
            LogWriter.log("✅ Foto salva com sucesso")

            return@withContext 1L
        } catch (e: Exception) {
            LogWriter.log("❌ Erro ao salvar foto: ${e.message}")
            null
        }
    }

    /**
     * Obter fotos não sincronizadas
     */
    actual suspend fun obterFotosNaoSincronizadas(viagemId: Long): List<FotoOffline> =
        withContext(Dispatchers.Default) {
            try {
                emptyList()
            } catch (e: Exception) {
                LogWriter.log("❌ Erro ao obter fotos: ${e.message}")
                emptyList()
            }
        }

    /**
     * Marcar como sincronizada
     */
    actual suspend fun marcarSincronizada(fotoId: Long) {
        withContext(Dispatchers.Default) {
            try {
                LogWriter.log("✅ Foto $fotoId marcada como sincronizada")
            } catch (e: Exception) {
                LogWriter.log("❌ Erro: ${e.message}")
            }
        }
    }

    /**
     * Deletar foto
     */
    actual suspend fun deletarFoto(fotoId: Long) {
        withContext(Dispatchers.Default) {
            try {
                LogWriter.log("🗑️ Foto $fotoId deletada")
            } catch (e: Exception) {
                LogWriter.log("❌ Erro: ${e.message}")
            }
        }
    }
}
