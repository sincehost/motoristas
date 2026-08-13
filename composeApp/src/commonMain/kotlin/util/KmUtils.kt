package util

import kotlin.math.abs
import kotlin.math.round

/**
 * Utilitário único de quilometragem/hodômetro do sistema. Android, iOS, a
 * API e o banco (colunas DECIMAL(10,1)) usam todos o mesmo padrão de saída:
 * "XXXXXX.X" — ponto como separador decimal, sempre exatamente 1 casa
 * decimal, sem separador de milhar. Ex.: 115670.0, 115670.5, 118210.0.
 * Usar essas funções em QUALQUER campo de km (KM de Início, KM de Chegada,
 * km_posto, km_troca_oleo, km_troca_pneu etc.) — nunca tratar km como
 * string em comparações, nunca truncar a casa decimal.
 */

/**
 * Formata o texto digitado a cada keystroke (usar no onValueChange, junto
 * com TextFieldValue + cursor no final, como os demais campos numéricos
 * mascarados do app). Motorista digita só dígitos corridos (sem separador)
 * e o último dígito digitado sempre vira a casa decimal automaticamente —
 * igual campo de dinheiro (centavos). Ex.: digitando "1","5","9","7","8",
 * "8","4","9" mostra progressivamente "0.1" → "1.5" → "15.9" → ... →
 * "1597884.9". Evita o risco de esquecer o "." e salvar errado.
 */
fun formatarKmInput(entrada: String): String {
    // 9 dígitos = 8 inteiros + 1 decimal, cobre o range inteiro de um
    // DECIMAL(10,1) (até 99999999.9) sem truncar valor real no round-trip.
    val digitos = entrada.filter { it.isDigit() }.take(9)
    if (digitos.isEmpty()) return ""

    val decimal = digitos.last()
    var inteiro = digitos.dropLast(1).trimStart('0')
    if (inteiro.isEmpty()) inteiro = "0"

    return "$inteiro.$decimal"
}

/**
 * Normaliza o valor final pra envio/comparação/gravação: garante sempre
 * exatamente 1 casa decimal. Chamar antes de comparar, calcular ou enviar
 * ao servidor.
 */
fun normalizarKmParaEnvio(entrada: String): String {
    if (entrada.isBlank()) return "0.0"
    val formatado = formatarKmInput(entrada)
    return formatado.ifEmpty { "0.0" }
}

/** Converte pra Double pra comparações/cálculos numéricos — nunca comparar km como String. */
fun kmParaDouble(km: String?): Double =
    normalizarKmParaEnvio(km ?: "").toDoubleOrNull() ?: 0.0

/**
 * Formata um Double no padrão de exibição/envio do app: "XXXXXX.X".
 * Usa aritmética inteira (décimos) em vez de Double.toString() pra evitar
 * artefatos de ponto flutuante (ex.: "1597884.8999999999") — mesmo padrão
 * já usado nos campos de dinheiro do app (multiplica, arredonda pra Long,
 * separa inteiro/decimal na mão).
 */
fun formatarKmExibicao(valor: Double): String {
    val decimosTotal = round(valor * 10).toLong()
    val negativo = decimosTotal < 0
    val absDecimos = abs(decimosTotal)
    val inteiro = absDecimos / 10
    val decimal = absDecimos % 10
    return (if (negativo) "-" else "") + "$inteiro.$decimal"
}
