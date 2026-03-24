package util

import platform.Foundation.*

/**
 * LogWriter — Implementação iOS
 *
 * Usa println() para console e mantém buffer em memória
 * para exportação via getLogsAsText() / saveToFile().
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual object LogWriter {

    private val logs = mutableListOf<String>()

    actual fun log(message: String) {
        val formatter = NSDateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        val timestamp = formatter.stringFromDate(NSDate())
        val logLine = "[$timestamp] $message"

        logs.add(logLine)
        println(logLine)
    }

    actual fun saveToFile(): String {
        return try {
            val fileManager = NSFileManager.defaultManager
            val documentsDir = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true
            ).firstOrNull() as? String ?: return "Erro: diretório não encontrado"

            val logsDir = "$documentsDir/logs"
            if (!fileManager.fileExistsAtPath(logsDir)) {
                fileManager.createDirectoryAtPath(
                    logsDir,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null
                )
            }

            val formatter = NSDateFormatter()
            formatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
            val fileName = "sync_${formatter.stringFromDate(NSDate())}.txt"
            val filePath = "$logsDir/$fileName"

            val content = logs.joinToString("\n")
            val nsString = content as NSString
            nsString.writeToFile(filePath, atomically = true, encoding = NSUTF8StringEncoding, error = null)

            log("✅ LOGS SALVOS EM: $filePath")
            filePath
        } catch (e: Exception) {
            "Erro ao salvar: ${e.message}"
        }
    }

    actual fun clearLogs() {
        logs.clear()
    }

    actual fun getLogsAsText(): String {
        return logs.joinToString("\n")
    }
}
