package util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Implementação Android para salvar logs de sincronização em arquivo
 * Os logs serão salvos em: Android/data/br.com.lfsystem.app/files/logs/
 */
actual object LogWriter {
    
    private var context: Context? = null
    private val logs = mutableListOf<String>()
    
    fun init(context: Context) {
        this.context = context
    }
    
    actual fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logLine = "[$timestamp] $message"
        
        // Adiciona na lista
        logs.add(logLine)
        
        // Também imprime no console
        println(logLine)
    }
    
    actual fun saveToFile(): String {
        val ctx = context ?: return "Erro: Context não inicializado"
        
        try {
            // Diretório de arquivos do app (acessível sem permissões extras)
            val logsDir = File(ctx.getExternalFilesDir(null), "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            
            // Nome do arquivo com data/hora
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val fileName = "sync_${dateFormat.format(Date())}.txt"
            val file = File(logsDir, fileName)
            
            // Escreve todos os logs
            file.writeText(logs.joinToString("\n"))
            
            val path = file.absolutePath
            log("✅ LOGS SALVOS EM: $path")
            
            return path
            
        } catch (e: Exception) {
            return "Erro ao salvar: ${e.message}"
        }
    }
    
    actual fun clearLogs() {
        logs.clear()
    }
    
    actual fun getLogsAsText(): String {
        return logs.joinToString("\n")
    }
}
