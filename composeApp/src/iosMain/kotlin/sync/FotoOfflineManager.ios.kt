package sync

import database.AppRepository
import util.LogWriter

/**
 * FotoOfflineManager — Implementação iOS FUNCIONAL.
 *
 * Usa as queries do Offline.sq para persistir fotos no banco SQLite.
 * Antes era stub (retornava valores fake), agora salva e recupera de verdade.
 *
 * As fotos são salvas como base64 no banco local. Quando o SyncManager
 * envia os dados para o servidor, as fotos já estão embutidas nos campos
 * base64 das tabelas (Abastecimento.foto, Descarga.foto, etc).
 *
 * Esta classe gerencia fotos que precisam de ciclo de vida separado
 * (ex: fotos tiradas antes de ter viagem, fotos grandes que precisam
 * de upload separado no futuro).
 */
actual class FotoOfflineManager actual constructor(private val repository: AppRepository) {

    /**
     * Salva foto Base64 no banco local para sincronização posterior.
     *
     * @return ID da foto salva, ou null se falhar
     */
    actual suspend fun salvarFotoBase64(
        viagemId: Long,
        tipo: String,
        base64: String,
        latitude: Double?,
        longitude: Double?
    ): Long? {
        return try {
            val base64Clean = if (base64.contains(",")) base64.substringAfter(",") else base64
            val sizeKB = base64Clean.length / 1024
            LogWriter.log("📸 [iOS] Salvando foto: tipo=$tipo, tamanho=${sizeKB}KB, viagem=$viagemId")

            repository.insertFotoOffline(
                viagemId = viagemId,
                tipo = tipo,
                imagemBase64 = base64Clean,
                latitude = latitude,
                longitude = longitude
            )

            val fotoId = repository.getLastFotoOfflineId()
            LogWriter.log("✅ [iOS] Foto salva com ID=$fotoId")
            fotoId
        } catch (e: Exception) {
            LogWriter.log("❌ [iOS] Erro ao salvar foto: ${e.message}")
            null
        }
    }

    /**
     * Obtém fotos não sincronizadas de uma viagem.
     */
    actual suspend fun obterFotosNaoSincronizadas(viagemId: Long): List<FotoOffline> {
        return try {
            repository.getFotosNaoSincronizadas(viagemId).map { row ->
                FotoOffline(
                    id = row.id,
                    viagemId = row.viagem_id,
                    tipo = row.tipo,
                    imagemBase64 = row.imagem_base64,
                    latitude = row.latitude,
                    longitude = row.longitude,
                    sincronizada = row.sincronizada == 1L,
                    dataCriacao = row.data_criacao ?: 0L
                )
            }
        } catch (e: Exception) {
            LogWriter.log("❌ [iOS] Erro ao obter fotos: ${e.message}")
            emptyList()
        }
    }

    /**
     * Marca foto como sincronizada após envio bem-sucedido ao servidor.
     */
    actual suspend fun marcarSincronizada(fotoId: Long) {
        try {
            repository.marcarFotoOfflineSincronizada(fotoId)
            LogWriter.log("✅ [iOS] Foto $fotoId marcada como sincronizada")
        } catch (e: Exception) {
            LogWriter.log("❌ [iOS] Erro ao marcar foto sincronizada: ${e.message}")
        }
    }

    /**
     * Deleta foto do banco local.
     */
    actual suspend fun deletarFoto(fotoId: Long) {
        try {
            repository.deletarFotoOffline(fotoId)
            LogWriter.log("🗑️ [iOS] Foto $fotoId deletada")
        } catch (e: Exception) {
            LogWriter.log("❌ [iOS] Erro ao deletar foto: ${e.message}")
        }
    }
}
