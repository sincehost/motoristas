package api

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

actual fun createHttpClient(): HttpClient {
    return HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(Json { 
                ignoreUnknownKeys = true 
                isLenient = true
                coerceInputValues = true
            })
        }
        // CRÍTICO: Não lançar exceção em HTTP 400/404/500
        // A API PHP retorna http_response_code(400) com JSON válido
        expectSuccess = false
        // Timeouts essenciais para redes 2G/3G rurais onde motoristas operam
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000   // 15s para estabelecer conexão
            requestTimeoutMillis = 60_000   // 60s para toda a requisição (fotos base64 são grandes)
            socketTimeoutMillis  = 30_000   // 30s sem dados recebidos
        }
    }
}
