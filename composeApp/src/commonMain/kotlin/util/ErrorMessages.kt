package util

/**
 * Ponto único de tradução de erro técnico -> mensagem amigável pro motorista.
 *
 * Antes desse arquivo, cada tela reimplementava sua própria versão parcial
 * disso (MinhasViagensScreen.android.kt, IniciarViagemScreen.android.kt/.ios.kt,
 * SyncManager, etc.), cada uma cobrindo um subconjunto diferente de casos —
 * então a mesma exceção (ex: SocketTimeoutException) aparecia amigável numa
 * tela e crua ("Erro: java.net.SocketTimeoutException") em outra. Use esta
 * função em todo catch(e: Exception) cujo resultado vai direto pra UI.
 *
 * NÃO usar em cima de `response.mensagem` vindo da API com status != "ok" —
 * essas já são mensagens de regra de negócio escritas pelo backend (ex:
 * "Motorista não encontrado"), não exceções técnicas.
 */
fun mensagemErroAmigavel(erro: String?): String {
    val msg = erro ?: ""
    return when {
        msg.isEmpty() ->
            "Ocorreu um erro inesperado. Tente novamente."

        msg.contains("Unable to resolve host", ignoreCase = true) ||
        msg.contains("No address associated with hostname", ignoreCase = true) ||
        msg.contains("UnknownHostException", ignoreCase = true) ||
        msg.contains("Network is unreachable", ignoreCase = true) ||
        msg.contains("nodename nor servname", ignoreCase = true) ||
        msg.contains("NSURLErrorDomain", ignoreCase = true) && msg.contains("-1009") ->
            "Sem conexão com a internet. Verifique sua conexão e tente novamente."

        msg.contains("timeout", ignoreCase = true) ||
        msg.contains("timed out", ignoreCase = true) ||
        msg.contains("SocketTimeout", ignoreCase = true) ->
            "Sua conexão com a internet está lenta ou instável. Verifique a conexão e tente novamente."

        msg.contains("Connection refused", ignoreCase = true) ||
        msg.contains("Failed to connect", ignoreCase = true) ||
        msg.contains("ConnectException", ignoreCase = true) ->
            "Não foi possível conectar ao servidor. Tente novamente em instantes."

        msg.contains("SSLHandshake", ignoreCase = true) ||
        msg.contains("SSLException", ignoreCase = true) ||
        msg.contains("CertPath", ignoreCase = true) ->
            "Não foi possível estabelecer uma conexão segura. Verifique sua internet e tente novamente."

        msg.contains("401", ignoreCase = true) ||
        msg.contains("unauthorized", ignoreCase = true) ->
            "Sua sessão expirou. Faça login novamente."

        msg.contains("403", ignoreCase = true) || msg.contains("forbidden", ignoreCase = true) ->
            "Você não tem permissão para realizar esta ação."

        msg.contains("500", ignoreCase = true) ||
        msg.contains("502", ignoreCase = true) ||
        msg.contains("503", ignoreCase = true) ||
        msg.contains("504", ignoreCase = true) ->
            "O servidor está indisponível no momento. Tente novamente em instantes."

        msg.contains("JsonDecodingException", ignoreCase = true) ||
        msg.contains("SerializationException", ignoreCase = true) ||
        msg.contains("MissingFieldException", ignoreCase = true) ||
        msg.contains("Unexpected JSON", ignoreCase = true) ->
            // Sintoma típico de incompatibilidade entre a versão do app e a
            // API (campo novo que o app antigo não entende, ou campo que o
            // app espera e a API não manda mais) — mensagem diferenciada
            // porque "tente novamente" não resolve nada nesse caso; só
            // atualizar o app ou esperar a API voltar a ser compatível.
            "Não foi possível processar a resposta do servidor. Isso pode acontecer se o aplicativo estiver desatualizado — verifique se há uma atualização disponível na loja de aplicativos."

        msg.contains("network", ignoreCase = true) || msg.contains("conectividade", ignoreCase = true) ->
            "Problema de conexão. Verifique sua internet e tente novamente."

        else -> {
            // Exceção fora do catálogo conhecido — nunca mostra o texto
            // técnico cru pro motorista (poderia ser qualquer coisa: nome de
            // classe Java/Kotlin, stack trace parcial, etc.). Fica só no log.
            LogWriter.log("⚠️ mensagemErroAmigavel: sem tradução conhecida para: $msg")
            "Ocorreu um erro inesperado. Tente novamente."
        }
    }
}

/**
 * Parse defensivo de um valor numérico vindo do servidor (ex: ao abrir uma
 * tela de edição de um lançamento já salvo). Se vier corrompido/não
 * numérico, o comportamento visível pro motorista continua o mesmo de
 * antes ("0,00"/vazio) — mas agora fica registrado no log qual campo e
 * qual valor bruto falharam, em vez de mascarar silenciosamente como se
 * o valor real fosse zero.
 */
fun parseDoubleComLog(valor: String?, campo: String): Double {
    val v = valor?.toDoubleOrNull()
    if (v == null) {
        LogWriter.log("⚠️ Valor inválido ao editar campo '$campo': '$valor' — exibindo 0,00")
    }
    return v ?: 0.0
}

/**
 * true quando a mensagem indica token/sessão expirado ou inválido.
 *
 * Antes, essa detecção só existia dentro de SyncManager.comRetry (usada pra
 * emitir AppEvent.TokenExpirado, que força logout) — chamadas diretas de tela
 * (iniciar viagem, finalizar viagem, salvar abastecimento/arla/descarga/
 * manutenção antes de cair pro offline) não passavam por ali, então uma
 * sessão expirada durante uso ativo aparecia como erro de validação genérico
 * em vez de forçar novo login. Centralizado aqui pra ser usado nos dois
 * lugares.
 */
fun isErroDeAutenticacao(erro: String?): Boolean {
    val msg = erro ?: return false
    return msg.contains("401", ignoreCase = true) ||
            msg.contains("unauthorized", ignoreCase = true) ||
            (msg.contains("token", ignoreCase = true) && msg.contains("invalid", ignoreCase = true))
}

/**
 * true quando a mensagem indica claramente falta de conectividade (vs. um
 * erro de validação/regra de negócio) — usado por telas que decidem entre
 * "salvar local e sincronizar depois" ou "mostrar erro de validação".
 */
fun isErroDeConectividade(erro: String?): Boolean {
    val msg = erro ?: return false
    return msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("No address associated with hostname", ignoreCase = true) ||
            msg.contains("UnknownHostException", ignoreCase = true) ||
            msg.contains("Network is unreachable", ignoreCase = true) ||
            msg.contains("nodename nor servname", ignoreCase = true) ||
            msg.contains("Connection refused", ignoreCase = true) ||
            msg.contains("Failed to connect", ignoreCase = true) ||
            msg.contains("ConnectException", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("timed out", ignoreCase = true) ||
            msg.contains("SocketTimeout", ignoreCase = true)
}
