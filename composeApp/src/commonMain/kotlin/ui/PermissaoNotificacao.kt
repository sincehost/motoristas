package ui

import androidx.compose.runtime.Composable
import database.AppRepository

/**
 * Pede a permissão de notificação depois do login, com uma explicação, uma
 * única vez. Antes o Android pedia logo na primeira abertura do app, sem
 * contexto, e muita gente recusava — perdendo o lembrete de lançamentos não
 * enviados. No iPhone o pedido continua no AppDelegate (sem mudança).
 */
@Composable
expect fun PedidoPermissaoNotificacao(repository: AppRepository)
