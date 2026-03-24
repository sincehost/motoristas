package screens

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Eventos que podem acontecer no app.
 * Qualquer tela emite, o Dashboard escuta e atualiza imediatamente.
 */
sealed class AppEvent {
    /** Viagem foi iniciada ou atualizada — Dashboard deve re-buscar viagem em andamento */
    object ViagemAtualizada : AppEvent()

    /** Viagem foi finalizada — Dashboard deve limpar estado local imediatamente */
    object ViagemFinalizada : AppEvent()

    /** Um item foi sincronizado ou adicionado — Dashboard deve atualizar contador de pendentes */
    object DadosPendentesAlterados : AppEvent()

    /** Conectividade mudou */
    data class Conectividade(val online: Boolean) : AppEvent()

    /** Token JWT expirou — forçar re-login */
    object TokenExpirado : AppEvent()
}

/**
 * Barramento global de eventos.
 * Usa SharedFlow — múltiplos emissores, múltiplos observadores, sem bloquear.
 */
object AppEvents {
    private val _eventos = MutableSharedFlow<AppEvent>(replay = 0, extraBufferCapacity = 16)

    /** Flow que qualquer composable pode collectar */
    val eventos: Flow<AppEvent> = _eventos.asSharedFlow()

    /** Último estado de conectividade — atualizado pelo NetworkMonitor, lido pelo Dashboard no início */
    @kotlin.concurrent.Volatile var online: Boolean = true

    /** Emite um evento para todos os observadores */
    fun emitir(evento: AppEvent) {
        if (evento is AppEvent.Conectividade) {
            online = evento.online
        }
        _eventos.tryEmit(evento)
    }
}
