package util

import platform.Foundation.*
import platform.UIKit.*

/**
 * CameraHelper — Funções auxiliares para câmera no iOS.
 *
 * Para compressão de fotos, usa [ImageCompressor] internamente:
 * - Redimensiona para 1280x960 mantendo proporção
 * - Comprime JPEG a 85%
 * - Resultado: ~200-400KB em vez de 5-7MB
 */
object CameraHelper {

    /**
     * Converte UIImage para Base64 comprimido.
     * Delega para ImageCompressor que faz resize + JPEG compression.
     *
     * @return String com prefixo "data:image/jpeg;base64,..." ou null se falhar
     */
    fun imageToBase64(image: UIImage): String? {
        return try {
            val result = ImageCompressor.compressImage(image)
            result.base64
        } catch (e: Throwable) {
            LogWriter.log("❌ [iOS] Erro ao converter imagem: ${e.message}")
            null
        }
    }

    /**
     * Converte UIImage para bytes comprimidos (para criar ImageBitmap via Skia).
     *
     * @return ByteArray dos bytes JPEG ou null se falhar
     */
    fun imageToBytes(image: UIImage): ByteArray? {
        return ImageCompressor.compressToBytes(image)
    }
}
