package util

import util.SenhaSegura.paraHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SenhaSeguraTest {

    // Vetores oficiais (FIPS 180-2 / NIST).
    @Test
    fun sha256Vazio() = assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        SenhaSegura.sha256(ByteArray(0)).paraHex()
    )

    @Test
    fun sha256Abc() = assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        SenhaSegura.sha256("abc".encodeToByteArray()).paraHex()
    )

    @Test
    fun sha256DoisBlocos() = assertEquals(
        "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
        SenhaSegura.sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).paraHex()
    )

    // Vetor oficial PBKDF2-HMAC-SHA256 (RFC 7914, seção 11).
    @Test
    fun pbkdf2Rfc7914() = assertEquals(
        "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
            "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
        SenhaSegura.pbkdf2Sha256("passwd".encodeToByteArray(), "salt".encodeToByteArray(), 1, 64).paraHex()
    )

    @Test
    fun verificaSenhaCertaEErrada() {
        val hash = SenhaSegura.gerarHash("minhaSenha123")
        assertTrue(hash.startsWith("pbkdf2$"))
        assertTrue(SenhaSegura.verificar("minhaSenha123", hash))
        assertFalse(SenhaSegura.verificar("minhaSenha124", hash))
    }

    @Test
    fun mesmaSenhaGeraHashesDiferentes() =
        assertNotEquals(SenhaSegura.gerarHash("abc"), SenhaSegura.gerarHash("abc"))

    @Test
    fun aceitaHashAntigoSemSal() {
        // Formato antigo: SHA-256 hex da senha (PasswordHasher).
        val antigo = SenhaSegura.sha256("1234".encodeToByteArray()).paraHex()
        assertTrue(SenhaSegura.verificar("1234", antigo))
        assertFalse(SenhaSegura.verificar("4321", antigo))
    }
}
