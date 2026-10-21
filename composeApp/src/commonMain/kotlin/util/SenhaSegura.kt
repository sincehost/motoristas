package util

import kotlin.random.Random

/**
 * Guarda a senha do motorista no celular (desbloqueio sem internet / "usar
 * senha" na tela de digital) com PBKDF2-HMAC-SHA256 + sal aleatório.
 *
 * Antes era SHA-256 puro, sem sal (PasswordHasher): rápido de quebrar por
 * força bruta se alguém copiar o banco do celular — e é a mesma senha do
 * servidor. Formato gravado: "pbkdf2$<iterações>$<sal hex>$<hash hex>".
 * Hashes antigos continuam valendo (verificar() aceita os dois) e são
 * trocados pelo novo formato no próximo login com internet.
 *
 * Implementado em Kotlin puro pra funcionar igual no Android e no iPhone.
 */
object SenhaSegura {

    private const val PREFIXO = "pbkdf2"
    private const val ITERACOES = 20_000
    private const val TAMANHO_SAL = 16
    private const val TAMANHO_HASH = 32

    fun gerarHash(senha: String): String {
        val sal = Random.nextBytes(TAMANHO_SAL)
        val hash = pbkdf2Sha256(senha.encodeToByteArray(), sal, ITERACOES, TAMANHO_HASH)
        return "$PREFIXO\$$ITERACOES\$${sal.paraHex()}\$${hash.paraHex()}"
    }

    fun verificar(senha: String, armazenado: String): Boolean {
        if (!armazenado.startsWith("$PREFIXO\$")) {
            // Formato antigo (SHA-256 sem sal).
            return PasswordHasher.verify(senha, armazenado)
        }
        val partes = armazenado.split('$')
        if (partes.size != 4) return false
        val iteracoes = partes[1].toIntOrNull() ?: return false
        val sal = partes[2].deHex() ?: return false
        val esperado = partes[3].deHex() ?: return false
        val calculado = pbkdf2Sha256(senha.encodeToByteArray(), sal, iteracoes, esperado.size)
        return igualEmTempoConstante(calculado, esperado)
    }

    // ===============================
    // PBKDF2 / HMAC / SHA-256 (RFC 8018, RFC 2104, FIPS 180-4)
    // ===============================

    fun pbkdf2Sha256(senha: ByteArray, sal: ByteArray, iteracoes: Int, tamanho: Int): ByteArray {
        val chave = if (senha.size > 64) sha256(senha) else senha
        val ipad = ByteArray(64) { i -> ((if (i < chave.size) chave[i].toInt() else 0) xor 0x36).toByte() }
        val opad = ByteArray(64) { i -> ((if (i < chave.size) chave[i].toInt() else 0) xor 0x5c).toByte() }
        fun hmac(msg: ByteArray): ByteArray = sha256(opad + sha256(ipad + msg))

        val saida = ByteArray(tamanho)
        var bloco = 1
        var pos = 0
        while (pos < tamanho) {
            val contador = byteArrayOf((bloco ushr 24).toByte(), (bloco ushr 16).toByte(), (bloco ushr 8).toByte(), bloco.toByte())
            var u = hmac(sal + contador)
            val t = u.copyOf()
            for (i in 1 until iteracoes) {
                u = hmac(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val n = minOf(t.size, tamanho - pos)
            t.copyInto(saida, pos, 0, n)
            pos += n
            bloco++
        }
        return saida
    }

    // Constantes oficiais do SHA-256 (FIPS 180-4), em hexadecimal.
    private val K = hexParaInts(
        "428a2f98 71374491 b5c0fbcf e9b5dba5 3956c25b 59f111f1 923f82a4 ab1c5ed5 " +
        "d807aa98 12835b01 243185be 550c7dc3 72be5d74 80deb1fe 9bdc06a7 c19bf174 " +
        "e49b69c1 efbe4786 0fc19dc6 240ca1cc 2de92c6f 4a7484aa 5cb0a9dc 76f988da " +
        "983e5152 a831c66d b00327c8 bf597fc7 c6e00bf3 d5a79147 06ca6351 14292967 " +
        "27b70a85 2e1b2138 4d2c6dfc 53380d13 650a7354 766a0abb 81c2c92e 92722c85 " +
        "a2bfe8a1 a81a664b c24b8b70 c76c51a3 d192e819 d6990624 f40e3585 106aa070 " +
        "19a4c116 1e376c08 2748774c 34b0bcb5 391c0cb3 4ed8aa4a 5b9cca4f 682e6ff3 " +
        "748f82ee 78a5636f 84c87814 8cc70208 90befffa a4506ceb bef9a3f7 c67178f2"
    )
    private val H_INICIAL = hexParaInts("6a09e667 bb67ae85 3c6ef372 a54ff53a 510e527f 9b05688c 1f83d9ab 5be0cd19")

    private fun hexParaInts(texto: String): IntArray =
        texto.trim().split(' ').map { it.toLong(16).toInt() }.toIntArray()

    fun sha256(dados: ByteArray): ByteArray {
        val h = H_INICIAL.copyOf()
        val bits = dados.size.toLong() * 8
        val tamanhoComPad = ((dados.size + 9 + 63) / 64) * 64
        val msg = ByteArray(tamanhoComPad)
        dados.copyInto(msg)
        msg[dados.size] = 0x80.toByte()
        for (i in 0 until 8) msg[tamanhoComPad - 1 - i] = (bits ushr (8 * i)).toByte()

        val w = IntArray(64)
        var bloco = 0
        while (bloco < tamanhoComPad) {
            for (i in 0 until 16) {
                val b = bloco + i * 4
                w[i] = ((msg[b].toInt() and 0xff) shl 24) or ((msg[b + 1].toInt() and 0xff) shl 16) or
                    ((msg[b + 2].toInt() and 0xff) shl 8) or (msg[b + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
            bloco += 64
        }
        val saida = ByteArray(32)
        for (i in 0 until 8) {
            saida[i * 4] = (h[i] ushr 24).toByte()
            saida[i * 4 + 1] = (h[i] ushr 16).toByte()
            saida[i * 4 + 2] = (h[i] ushr 8).toByte()
            saida[i * 4 + 3] = h[i].toByte()
        }
        return saida
    }

    private fun igualEmTempoConstante(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diferenca = 0
        for (i in a.indices) diferenca = diferenca or (a[i].toInt() xor b[i].toInt())
        return diferenca == 0
    }

    fun ByteArray.paraHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun String.deHex(): ByteArray? {
        if (length % 2 != 0) return null
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: return null }
    }
}
