package screens

/**
 * Enum de todas as telas do app.
 */
enum class Screen {
    DASHBOARD,
    INICIAR_VIAGEM,
    MINHAS_VIAGENS,
    ADICIONAR_COMBUSTIVEL,
    ADICIONAR_ARLA,
    FINALIZAR_VIAGEM,
    MANUTENCAO,
    MINHAS_MANUTENCOES,
    RELATORIO_VIAGENS,
    ADICIONAR_DESCARGA,
    OUTRAS_DESPESAS,
    CHECKLIST_PRE_VIAGEM,
    CHECKLIST_POS_VIAGEM
}

/**
 * Tipo de mensagem para feedback ao usuário.
 * Substitui o uso de emojis (✓, ⚠, ❌) como prefixo.
 */
enum class TipoMensagem {
    SUCESSO,
    ERRO,
    AVISO,
    INFO
}

/**
 * Tuple genérica para desestruturação de 4 valores.
 */
data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
