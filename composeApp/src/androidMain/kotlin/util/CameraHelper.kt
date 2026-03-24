package util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * CameraHelper — Funções auxiliares para câmera.
 *
 * Para compressão de fotos, use [ImageCompressor] diretamente:
 *   val (bitmap, base64) = ImageCompressor.compressFromUri(context, uri)
 *   val (bitmap, base64) = ImageCompressor.compressFromFile(file)
 */
object CameraHelper {

    fun createImageUri(context: Context): Uri {
        val imageFile = File(context.cacheDir, "foto_${java.lang.System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    /**
     * Converte Uri para Base64 comprimido.
     * Delega para ImageCompressor que faz inSampleSize + scale + NO_WRAP.
     */
    fun uriToBase64(context: Context, uri: Uri): String? {
        val result = ImageCompressor.compressFromUri(context, uri)
        return result.base64
    }

    /**
     * Converte Uri para Bitmap + Base64 comprimido.
     * Retorna par (Bitmap para exibir, Base64 para enviar).
     */
    fun uriToBitmapAndBase64(context: Context, uri: Uri): Pair<Bitmap?, String?> {
        val result = ImageCompressor.compressFromUri(context, uri)
        return result.bitmap to result.base64
    }

    /**
     * Converte File para Bitmap + Base64 comprimido.
     * Retorna par (Bitmap para exibir, Base64 para enviar).
     */
    fun fileToBitmapAndBase64(file: File): Pair<Bitmap?, String?> {
        val result = ImageCompressor.compressFromFile(file)
        return result.bitmap to result.base64
    }
}
