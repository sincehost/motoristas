package util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LeitorKmTest {

    private fun ler(linhas: List<String>, ref: Double?, temDecimal: Boolean = false, limite: Double = 5000.0) =
        LeitorKm.escolher(LeitorKm.candidatos(linhas, temDecimal), ref, limite)

    @Test
    fun leNumeroSimples() = assertEquals(115672.0, ler(listOf("115672"), 115000.0))

    @Test
    fun juntaDigitosSeparadosPorEspaco() = assertEquals(115672.0, ler(listOf("115 672"), 115000.0))

    @Test
    fun ignoraUnidadeKm() = assertEquals(115672.0, ler(listOf("115672 km"), 115000.0))

    @Test
    fun ignoraUnidadeKmColada() = assertEquals(115672.0, ler(listOf("115672km"), 115000.0))

    @Test
    fun corrigeLetrasParecidas() = assertEquals(105672.0, ler(listOf("1O5672"), 105000.0))

    @Test
    fun painelComDecimalColado() = assertEquals(115672.5, ler(listOf("1156725"), 115600.0, temDecimal = true))

    @Test
    fun painelComDecimalSeparado() = assertEquals(115672.5, ler(listOf("115672,5"), 115600.0, temDecimal = true))

    @Test
    fun separadorDeMilhar() = assertEquals(115672.0, ler(listOf("115.672"), 115000.0))

    @Test
    fun ignoraHoraEOutrosNumeros() =
        assertEquals(115672.0, ler(listOf("14:35", "TRIP 342.1", "ODO 115672", "22°C"), 115000.0))

    @Test
    fun recusaKmMenorQueReferencia() = assertNull(ler(listOf("114999"), 115000.0))

    @Test
    fun recusaKmMuitoAcimaDoLimite() = assertNull(ler(listOf("125000"), 115000.0))

    @Test
    fun semReferenciaNaoSugere() = assertNull(ler(listOf("115672"), null))

    @Test
    fun escolheOMaisProximoDaReferencia() =
        assertEquals(115672.0, ler(listOf("115672", "118000"), 115000.0))

    @Test
    fun estabilizadorExigeLeiturasRepetidas() {
        val e = EstabilizadorKm()
        assertNull(e.registrar(115672.0))
        assertNull(e.registrar(115673.0))
        assertNull(e.registrar(115672.0))
        assertEquals(115672.0, e.registrar(115672.0))
    }

    @Test
    fun estabilizadorIgnoraQuadroSemLeitura() {
        val e = EstabilizadorKm()
        e.registrar(115672.0); e.registrar(null); e.registrar(115672.0)
        assertEquals(115672.0, e.registrar(115672.0))
    }

    @Test
    fun formataParaExibir() {
        assertEquals("115.672,5", formatarKmBR(115672.5, true))
        assertEquals("115.672", formatarKmBR(115672.0, false))
    }
}
