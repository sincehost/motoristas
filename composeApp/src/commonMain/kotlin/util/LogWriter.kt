package util

/**
 * Interface comum para escrever logs
 */
expect object LogWriter {
    fun log(message: String)
    fun saveToFile(): String
    fun clearLogs()
    fun getLogsAsText(): String
}
