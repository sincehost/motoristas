package util

import platform.Foundation.*
import platform.UIKit.*
import platform.AVFoundation.AVAuthorizationStatus
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

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
     * Verdadeiro se a permissão de câmera já foi negada (usuário tocou "Não
     * Permitir" alguma vez, ou é MDM/restrição) — nesse estado o iOS NÃO
     * mostra o pedido de permissão de novo, e abrir o UIImagePickerController
     * mesmo assim resulta em tela preta que se auto-cancela sozinha, sem
     * nenhuma mensagem clara pro motorista. Chamar isso ANTES de abrir a
     * câmera evita essa experiência confusa — se negado, mostra direto
     * "vá em Ajustes" em vez de tentar abrir e falhar silenciosamente.
     * Se o status for "ainda não perguntado", retorna false: o próprio
     * UIImagePickerController dispara o pedido de permissão normalmente.
     */
    fun cameraSemPermissao(): Boolean {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        return status == AVAuthorizationStatus.AVAuthorizationStatusDenied ||
            status == AVAuthorizationStatus.AVAuthorizationStatusRestricted
    }

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

    /**
     * Decodifica uma string Base64 (com ou sem o prefixo "data:image/...;base64,")
     * de volta para ImageBitmap, para exibir uma foto já cadastrada nas telas de edição.
     *
     * @return ImageBitmap pronto para uso em Image(bitmap = ...), ou null se falhar
     */
    @OptIn(ExperimentalForeignApi::class)
    fun base64ToImageBitmap(base64: String): ImageBitmap? {
        return try {
            val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
            val data = NSData.create(base64EncodedString = cleanBase64, options = 0u) ?: return null
            val length = data.length.toInt()
            if (length == 0) return null
            val bytes = ByteArray(length)
            bytes.usePinned { pinned ->
                data.getBytes(pinned.addressOf(0), data.length)
            }
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (e: Throwable) {
            LogWriter.log("❌ [iOS] Erro ao decodificar foto base64: ${e.message}")
            null
        }
    }
}
