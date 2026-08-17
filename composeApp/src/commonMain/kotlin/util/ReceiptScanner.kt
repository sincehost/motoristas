package util

/**
 * Resultado de escanear um cupom fiscal de abastecimento (foto + OCR +
 * QR Code/código de barras, quando disponíveis).
 *
 * Tudo aqui é "melhor esforço" — nenhuma leitura de imagem é 100% garantida
 * (papel térmico desbota, foto pode sair torta, QR pode estar rasgado).
 * Por isso o motorista SEMPRE revisa os campos pré-preenchidos na tela
 * normal antes de salvar; isso só evita digitar tudo na mão quando a
 * leitura sai limpa.
 */
data class ReceiptScanResult(
    val ocrText: String,
    val qrCodeText: String? = null,
    val barcodeText: String? = null,
    /** Valor total em formato decimal puro ("123.45"), pronto pra alimentar a máscara BR do campo. */
    val valorDetectado: String? = null,
    /** Litros em formato decimal puro ("45.20"). */
    val litrosDetectado: String? = null,
    /** Data no formato "dd/MM/yyyy", já no padrão usado pelo campo Data da tela. */
    val dataDetectada: String? = null,
    /** Nome do posto — só um palpite (normalmente a primeira linha do cupom). */
    val postoDetectado: String? = null
)

/**
 * Extrai valor, litros, data e nome do posto de um texto de cupom fiscal já
 * reconhecido por OCR. Puramente heurístico (regex sobre o texto), sem
 * depender de nenhum serviço externo — funciona offline.
 */
fun analisarTextoCupom(ocrText: String, qrCodeText: String? = null, barcodeText: String? = null): ReceiptScanResult {
    val linhas = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
    val textoUpper = ocrText.uppercase()

    return ReceiptScanResult(
        ocrText = ocrText,
        qrCodeText = qrCodeText,
        barcodeText = barcodeText,
        valorDetectado = extrairValorTotal(linhas, textoUpper),
        litrosDetectado = extrairLitros(linhas, textoUpper),
        dataDetectada = extrairData(ocrText),
        postoDetectado = extrairNomePosto(linhas)
    )
}

/** Todo número em formato BR de dinheiro encontrado na linha: "1.234,56" ou "45,20". */
private val REGEX_VALOR_BR = Regex("""(\d{1,3}(?:\.\d{3})*,\d{2})""")
private val REGEX_DATA = Regex("""(\d{2})[/.\-](\d{2})[/.\-](\d{4})""")

/**
 * Procura o valor total: prioriza linhas com palavras-chave típicas de
 * "total a pagar" num cupom de posto; se não achar nenhuma, usa o maior
 * valor em R$ encontrado no texto inteiro (normalmente é o total mesmo,
 * já que os subtotais/valor-por-litro costumam ser menores).
 */
private fun extrairValorTotal(linhas: List<String>, textoUpper: String): String? {
    val palavrasChave = listOf("VALOR TOTAL", "TOTAL A PAGAR", "TOTAL GERAL", "VALOR A PAGAR", "TOTAL R$", "TOTAL:")
    for (linha in linhas) {
        val linhaUpper = linha.uppercase()
        if (palavrasChave.any { linhaUpper.contains(it) }) {
            val match = REGEX_VALOR_BR.find(linha)
            if (match != null) return brParaDecimalPuro(match.value)
        }
    }
    // Fallback: linha que contenha "TOTAL" isolado (sem ser "subtotal"/"total litros")
    for (linha in linhas) {
        val linhaUpper = linha.uppercase()
        if (linhaUpper.contains("TOTAL") && !linhaUpper.contains("SUBTOTAL") && !linhaUpper.contains("LITRO")) {
            val match = REGEX_VALOR_BR.find(linha)
            if (match != null) return brParaDecimalPuro(match.value)
        }
    }
    // Último fallback: maior valor em R$ do cupom inteiro
    val todosValores = REGEX_VALOR_BR.findAll(linhas.joinToString(" ")).map { it.value }.toList()
    if (todosValores.isEmpty()) return null
    return todosValores
        .map { brParaDecimalPuro(it) }
        .maxByOrNull { it.toDoubleOrNull() ?: 0.0 }
}

/** Litros abastecidos: procura "LITRO(S)" na linha e pega o número decimal mais próximo. */
private fun extrairLitros(linhas: List<String>, textoUpper: String): String? {
    for (linha in linhas) {
        val linhaUpper = linha.uppercase()
        if (linhaUpper.contains("LITRO") || Regex("""\bLTS?\b""").containsMatchIn(linhaUpper)) {
            // Litros geralmente vem com menos casas de milhar que dinheiro — aceita
            // tanto "45,20" quanto "45.20" (alguns modelos de bomba usam ponto).
            val match = Regex("""(\d{1,4}[.,]\d{2,3})""").find(linha)
            if (match != null) {
                val numero = match.value.replace(",", ".")
                // Litros abastecidos num caminhão raramente passa de ~2000L —
                // descarta leituras absurdas (provavelmente pegou outro número).
                val valor = numero.toDoubleOrNull()
                if (valor != null && valor in 0.1..3000.0) return numero
            }
        }
    }
    return null
}

/** Data do cupom: primeira data válida (dd/mm/aaaa) encontrada no texto. */
private fun extrairData(ocrText: String): String? {
    val match = REGEX_DATA.find(ocrText) ?: return null
    val dia = match.groupValues[1]
    val mes = match.groupValues[2]
    val ano = match.groupValues[3]
    // Validação simples de faixa, pra não aceitar "99/99/9999" lido errado.
    val diaN = dia.toIntOrNull() ?: return null
    val mesN = mes.toIntOrNull() ?: return null
    if (diaN !in 1..31 || mesN !in 1..12) return null
    return "$dia/$mes/$ano"
}

/**
 * Nome do posto: normalmente a razão social vem nas primeiras linhas do
 * cupom. Pega a primeira linha "razoável" (não vazia, não só números/pontuação,
 * com mais de 3 caracteres) — é só um palpite, o motorista confere.
 */
private fun extrairNomePosto(linhas: List<String>): String? {
    for (linha in linhas.take(5)) {
        val temLetra = linha.any { it.isLetter() }
        if (temLetra && linha.length > 3) {
            return linha
        }
    }
    return null
}

/** "1.234,56" -> "1234.56" (formato decimal puro, ponto como separador decimal). */
private fun brParaDecimalPuro(valorBr: String): String {
    return valorBr.replace(".", "").replace(",", ".")
}
