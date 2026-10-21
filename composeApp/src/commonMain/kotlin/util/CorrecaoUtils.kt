package util

/**
 * Apoio da tela "Corrigir lançamento" (Pendências): máscaras dos campos e
 * leitura do valor de referência que o servidor manda na mensagem de erro.
 * Tudo em Kotlin puro (comum a Android e iPhone).
 */

/**
 * KM de referência dentro da mensagem de erro do servidor — o ÚLTIMO número
 * entre parênteses. Ex.: "KM de início (115600.0) não pode ser menor que o
 * último km registrado deste veículo (115670.5)." → 115670.5. A API sempre
 * formata com ponto decimal e sem milhar (formatarKm / cast float no PHP).
 */
fun kmReferenciaDoErro(erro: String?): Double? {
    if (erro == null || !erro.contains("KM", ignoreCase = true)) return null
    val numero = Regex("""\((\d+(?:[.,]\d+)?)\)""").findAll(erro).lastOrNull()?.groupValues?.get(1) ?: return null
    return numero.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
}

/** "2026-10-20" (ou com hora) → "20/10/2026". Outro formato volta como está. */
fun dataApiParaBR(data: String): String {
    val p = data.trim().split(" ").first().split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else data
}

/** "20/10/2026" → "2026-10-20". null se a data não for válida. */
fun dataBRParaApi(data: String): String? {
    val p = data.split("/")
    if (p.size != 3 || p[0].length != 2 || p[1].length != 2 || p[2].length != 4) return null
    val dia = p[0].toIntOrNull() ?: return null
    val mes = p[1].toIntOrNull() ?: return null
    val ano = p[2].toIntOrNull() ?: return null
    if (mes !in 1..12 || dia !in 1..31 || ano !in 2000..2100) return null
    return "${p[2]}-${p[1]}-${p[0]}"
}

/** Máscara de data enquanto digita: "20102026" → "20/10/2026". */
fun mascaraData(entrada: String): String {
    val d = entrada.filter { it.isDigit() }.take(8)
    return buildString {
        d.forEachIndexed { i, c ->
            if (i == 2 || i == 4) append('/')
            append(c)
        }
    }
}

/** Máscara de dinheiro/litros (centavos): "123456" → "1.234,56". */
fun mascaraCentavos(entrada: String): String {
    val d = entrada.filter { it.isDigit() }.trimStart('0').take(12)
    if (d.isEmpty()) return ""
    val cheio = d.padStart(3, '0')
    val inteiro = cheio.dropLast(2).reversed().chunked(3).joinToString(".").reversed()
    return "$inteiro,${cheio.takeLast(2)}"
}

/** Máscara de inteiro com milhar (peso): "25000" → "25.000". */
fun mascaraMilhar(entrada: String): String {
    val d = entrada.filter { it.isDigit() }.trimStart('0').take(9)
    if (d.isEmpty()) return ""
    return d.reversed().chunked(3).joinToString(".").reversed()
}
