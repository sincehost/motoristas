package util

import platform.UIKit.*
import platform.CoreGraphics.*
import platform.Foundation.*
import kotlinx.cinterop.*

/**
 * ImageCompressor — Equivalente iOS do ImageCompressor.kt do Android.
 *
 * Resolve os mesmos problemas:
 * - Fotos do iPhone (~5MB) viravam ~7MB em Base64, travando sync em 3G
 * - Sem redimensionamento: foto de 12MP carregada inteira na memória
 * - Base64 gigante estourava timeout de 60s na sync
 *
 * Resultado: fotos saem com ~200-400KB em vez de 5-7MB.
 * Resolução 1280x960 com qualidade 85% mantém texto legível em cupons e painel.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
object ImageCompressor {

    // Configurações idênticas ao Android para consistência
    private const val MAX_WIDTH = 1280.0
    private const val MAX_HEIGHT = 960.0
    private const val JPEG_QUALITY = 0.85

    /**
     * Resultado da compressão.
     * base64 inclui o prefixo data:image/jpeg;base64,...
     */
    data class CompressResult(
        val base64: String?,
        val sizeKB: Int = 0
    )

    /**
     * Comprime UIImage para base64 otimizado.
     *
     * Processo:
     * 1. Redimensiona para MAX_WIDTH x MAX_HEIGHT mantendo proporção
     * 2. Comprime para JPEG com qualidade configurável
     * 3. Converte para Base64
     *
     * @param image UIImage capturada pela câmera ou galeria
     * @return CompressResult com base64 e tamanho em KB
     */
    fun compressImage(image: UIImage): CompressResult {
        return try {
            // PASSO 1: Redimensionar se necessário
            val resized = resizeIfNeeded(image)

            // PASSO 2: Comprimir para JPEG
            val data = UIImageJPEGRepresentation(resized, JPEG_QUALITY)
                ?: return CompressResult(null)

            // PASSO 3: Converter para Base64
            val base64 = data.base64EncodedStringWithOptions(0u)
            val sizeKB = (base64.length / 1024)

            LogWriter.log("📸 [iOS] Foto comprimida: ${sizeKB}KB (original: ${image.size.useContents { "${width.toInt()}x${height.toInt()}" }})")

            CompressResult(
                base64 = "data:image/jpeg;base64,$base64",
                sizeKB = sizeKB
            )
        } catch (e: Exception) {
            LogWriter.log("❌ [iOS] Erro ao comprimir imagem: ${e.message}")
            CompressResult(null)
        }
    }

    /**
     * Converte UIImage para bytes (para criar ImageBitmap via Skia).
     * Também comprime a imagem antes de converter.
     *
     * @return ByteArray dos bytes JPEG comprimidos, ou null se falhar
     */
    fun compressToBytes(image: UIImage): ByteArray? {
        return try {
            val resized = resizeIfNeeded(image)
            val data = UIImageJPEGRepresentation(resized, JPEG_QUALITY) ?: return null
            val length = data.length.toInt()
            if (length == 0) return null

            val bytes = ByteArray(length)
            bytes.usePinned { pinned ->
                data.getBytes(pinned.addressOf(0), data.length)
            }
            bytes
        } catch (e: Exception) {
            LogWriter.log("❌ [iOS] Erro ao converter imagem para bytes: ${e.message}")
            null
        }
    }

    /**
     * Redimensiona UIImage mantendo proporção.
     * Se já está dentro dos limites, retorna a imagem original.
     *
     * Usa UIGraphicsBeginImageContextWithOptions com scale=1.0
     * para evitar multiplicação pela escala do display (@2x, @3x).
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun resizeIfNeeded(image: UIImage): UIImage {
        val originalWidth = image.size.useContents { width }
        val originalHeight = image.size.useContents { height }

        // Se já está dentro dos limites, não redimensionar
        if (originalWidth <= MAX_WIDTH && originalHeight <= MAX_HEIGHT) {
            return image
        }

        // Calcular novo tamanho mantendo proporção
        val ratio = minOf(MAX_WIDTH / originalWidth, MAX_HEIGHT / originalHeight)
        val newWidth = (originalWidth * ratio)
        val newHeight = (originalHeight * ratio)

        val newSize = CGSizeMake(newWidth, newHeight)

        // scale=1.0 garante que não multiplica pela escala do display
        // opaque=false mantém transparência (embora JPEG não use)
        UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, newWidth, newHeight))
        val resized = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return resized ?: image
    }
}
