package util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CorrecaoUtilsTest {

    @Test
    fun referenciaDoErroDeViagem() = assertEquals(
        115670.5,
        kmReferenciaDoErro("KM de início (115600.0) não pode ser menor que o último km registrado deste veículo (115670.5).")
    )

    @Test
    fun referenciaDoErroDeChegada() = assertEquals(
        115600.0,
        kmReferenciaDoErro("KM de chegada (115500.0) deve ser maior ou igual ao KM de início (115600.0)")
    )

    @Test
    fun referenciaComNumeroInteiro() = assertEquals(
        98000.0,
        kmReferenciaDoErro("KM da troca de óleo não pode ser menor que o km atual do veículo (98000).")
    )

    @Test
    fun semReferenciaQuandoNaoEhErroDeKm() = assertNull(kmReferenciaDoErro("Forma de pagamento inválida"))

    @Test
    fun datas() {
        assertEquals("20/10/2026", dataApiParaBR("2026-10-20"))
        assertEquals("2026-10-20", dataBRParaApi("20/10/2026"))
        assertNull(dataBRParaApi("32/10/2026"))
        assertNull(dataBRParaApi("20/10/26"))
        assertEquals("20/10/2026", mascaraData("20102026"))
        assertEquals("20/1", mascaraData("201"))
    }

    @Test
    fun mascaras() {
        assertEquals("1.234,56", mascaraCentavos("123456"))
        assertEquals("0,05", mascaraCentavos("5"))
        assertEquals("1.234,56", mascaraCentavos("1.234,56"))
        assertEquals("", mascaraCentavos(""))
        assertEquals("25.000", mascaraMilhar("25000"))
    }
}
