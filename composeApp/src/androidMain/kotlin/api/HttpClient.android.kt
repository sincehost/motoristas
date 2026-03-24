package api

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

actual fun createHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        // CRÍTICO: Não lançar exceção em HTTP 400/404/500
        // A API PHP retorna http_response_code(400) com JSON válido
        // Sem isso, o Ktor lança ResponseException e nunca parseia a mensagem de erro
        expectSuccess = false
        // Timeouts essenciais para redes 2G/3G rurais onde motoristas operam
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000   // 15s para estabelecer conexão
            requestTimeoutMillis = 60_000   // 60s para toda a requisição (fotos base64 são grandes)
            socketTimeoutMillis  = 30_000   // 30s sem dados recebidos
        }
        engine {
            config {
                // Retry automático em falhas de conexão (não em erros HTTP)
                retryOnConnectionFailure(true)
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
            }
        }
    }
}
