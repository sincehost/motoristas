package util

import kotlin.math.abs
import kotlin.math.round

/**
 * Interpreta o texto lido da câmera (OCR) e decide qual número é o KM do
 * hodômetro. Usado pelo scanner ao vivo (Android) e pela leitura da foto
 * (Android galeria / iPhone).
 *
 * Regra de ouro: só sugere um KM quando dá pra CONFERIR — precisa de um KM de
 * referência (último KM da placa / KM de início da viagem) e o valor lido tem
 * que ficar entre a referência e referência + limite. Sem referência, ou sem
 * nenhum número plausível, devolve null e o motorista digita. Leitura errada
 * nunca entra sem ele ver: o valor sempre volta pro campo pra conferir.
 */
object LeitorKm {

    /** Números candidatos a KM encontrados nas linhas de texto lidas. */
    fun candidatos(linhas: List<String>, temDecimal: Boolean): List<Double> {
        val resultado = mutableListOf<Double>()
        for (linha in linhas) {
            // Hora (12:45), data (20/10) e afins não são KM.
            if (linha.contains(':') || linha.contains('/')) continue
            for (grupo in gruposNumericos(linha)) {
                val digitos = grupo.filter { it.isDigit() }
                if (digitos.length !in 3..8) continue
                val inteiro = digitos.toLongOrNull() ?: continue
                // Separador com 1 dígito depois ("115672,5") = casa decimal explícita.
                val temSeparadorDecimal = grupo.length >= 2 && (grupo[grupo.length - 2] == ',' || grupo[grupo.length - 2] == '.')
                if (temSeparadorDecimal) {
                    resultado += inteiro / 10.0
                } else {
                    resultado += inteiro.toDouble()
                    // Painel com casa decimal: o último dígito costuma vir colado
                    // ("1156725" = 115672.5) — testa as duas leituras.
                    if (temDecimal) resultado += inteiro / 10.0
                }
            }
        }
        return resultado.distinct()
    }

    /**
     * Escolhe o KM entre os candidatos: o mais próximo da referência, desde
     * que >= referência e <= referência + limite. null = nada confiável.
     */
    fun escolher(candidatos: List<Double>, referencia: Double?, limite: Double): Double? {
        if (referencia == null || referencia <= 0.0) return null
        return candidatos
            .filter { it >= referencia && it <= referencia + limite }
            .minByOrNull { it - referencia }
    }

    /**
     * Junta os pedaços numéricos de uma linha: hodômetro costuma aparecer com
     * espaço entre os dígitos ("115 672") e o OCR confunde letras parecidas
     * com números (O→0, I→1, S→5, B→8). Tokens com letras de verdade ("km",
     * "ODO", "TRIP") quebram o grupo.
     */
    private fun gruposNumericos(linha: String): List<String> {
        val grupos = mutableListOf<String>()
        val atual = StringBuilder()
        for (bruto in linha.split(' ', '\t')) {
            // "115672km" — unidade colada no número.
            val token = if (bruto.endsWith("km", ignoreCase = true)) bruto.dropLast(2) else bruto
            if (token.isEmpty()) continue
            val convertido = token.map { trocarLetraParecida(it) }.joinToString("")
            val ehNumerico = convertido.isNotEmpty() &&
                convertido.all { it.isDigit() || it == '.' || it == ',' } &&
                convertido.count { it.isDigit() } >= 1 &&
                // Token com letra original demais não é número (ex.: "BOS").
                token.count { it.isDigit() } * 2 >= token.length
            if (ehNumerico) {
                atual.append(convertido)
            } else if (atual.isNotEmpty()) {
                grupos += atual.toString(); atual.clear()
            }
        }
        if (atual.isNotEmpty()) grupos += atual.toString()
        return grupos.map { it.trim('.', ',') }
    }

    private fun trocarLetraParecida(c: Char): Char = when (c) {
        'O', 'o', 'D', 'Q' -> '0'
        'I', 'l', '|', 'i' -> '1'
        'S', 's' -> '5'
        'B' -> '8'
        'Z', 'z' -> '2'
        else -> c
    }
}

/**
 * Leitura ao vivo: só considera o KM "firme" quando o mesmo número aparece em
 * [necessarias] das últimas [janela] leituras — elimina quadro tremido,
 * reflexo e dígito trocado num quadro só.
 */
class EstabilizadorKm(private val necessarias: Int = 3, private val janela: Int = 5) {
    private val ultimas = ArrayDeque<Double?>()

    fun registrar(km: Double?): Double? {
        ultimas.addLast(km)
        while (ultimas.size > janela) ultimas.removeFirst()
        val valores = ultimas.filterNotNull()
        return valores.firstOrNull { v -> valores.count { abs(it - v) < 0.05 } >= necessarias }
    }

    fun limpar() = ultimas.clear()
}

/** 115672.5 → "115.672,5" (ou "115.672" sem casa decimal) — só pra exibir. */
fun formatarKmBR(km: Double, temDecimal: Boolean): String {
    val decimos = round(km * 10).toLong()
    val inteiro = (decimos / 10).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (temDecimal) "$inteiro,${decimos % 10}" else inteiro
}
