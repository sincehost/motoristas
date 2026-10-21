package util

import android.graphics.BitmapFactory
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual suspend fun reconhecerTextoFoto(base64: String): List<String> {
    val bitmap = try {
        val limpo = if (base64.contains(",")) base64.substringAfter(",") else base64
        val bytes = Base64.decode(limpo, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    } ?: return emptyList()

    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { texto ->
                    cont.resume(texto.textBlocks.flatMap { bloco -> bloco.lines.map { it.text } })
                }
                .addOnFailureListener { e ->
                    LogWriter.log("⚠️ OCR da foto falhou: ${e.message}")
                    cont.resume(emptyList())
                }
        }
    } finally {
        recognizer.close()
    }
}
