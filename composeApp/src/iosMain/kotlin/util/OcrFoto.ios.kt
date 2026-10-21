package util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

// Vision (framework nativo da Apple, iOS 13+) — mesmo papel do ML Kit no
// Android. performRequests é síncrono, então roda fora da thread principal.
@OptIn(ExperimentalForeignApi::class)
actual suspend fun reconhecerTextoFoto(base64: String): List<String> = withContext(Dispatchers.Default) {
    try {
        val limpo = if (base64.contains(",")) base64.substringAfter(",") else base64
        val data = NSData.create(base64EncodedString = limpo, options = 0u) ?: return@withContext emptyList()
        val cgImage = UIImage.imageWithData(data)?.CGImage ?: return@withContext emptyList()

        val linhas = mutableListOf<String>()
        val request = VNRecognizeTextRequest(completionHandler = { req, _ ->
            req?.results?.forEach { resultado ->
                val observacao = resultado as? VNRecognizedTextObservation ?: return@forEach
                val melhor = observacao.topCandidates(1uL).firstOrNull() as? VNRecognizedText ?: return@forEach
                linhas.add(melhor.string)
            }
        })
        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        // Correção de idioma "conserta" número pra palavra — desligada.
        request.usesLanguageCorrection = false

        VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any?>())
            .performRequests(listOf(request), null)
        linhas
    } catch (e: Throwable) {
        LogWriter.log("⚠️ [iOS] OCR da foto falhou: ${e.message}")
        emptyList()
    }
}
