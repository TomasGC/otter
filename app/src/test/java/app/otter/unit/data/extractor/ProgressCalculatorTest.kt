package app.otter.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressCalculatorTest {

    // ===== StandardProgressCalculator =====

    @Test
    fun `StandardProgressCalculator - 5 of 10 returns 0 point 5`() {
        val calc = StandardProgressCalculator()
        assertEquals(0.5f, calc.calculate(5, 10), 0.001f)
    }

    @Test
    fun `StandardProgressCalculator - 0 of 10 returns 0`() {
        val calc = StandardProgressCalculator()
        assertEquals(0f, calc.calculate(0, 10), 0.001f)
    }

    @Test
    fun `StandardProgressCalculator - 10 of 10 returns 1 point 0`() {
        val calc = StandardProgressCalculator()
        assertEquals(1.0f, calc.calculate(10, 10), 0.001f)
    }

    @Test
    fun `StandardProgressCalculator - totalCount 0 returns 0 (div-by-zero guard)`() {
        val calc = StandardProgressCalculator()
        assertEquals(0f, calc.calculate(5, 0), 0.001f)
    }

    @Test
    fun `StandardProgressCalculator - totalCount negative returns 0`() {
        val calc = StandardProgressCalculator()
        assertEquals(0f, calc.calculate(5, -1), 0.001f)
    }

    // ===== IndeterminateProgressCalculator =====

    @Test
    fun `IndeterminateProgressCalculator - extractedCount 1 returns 0 point 5`() {
        val calc = IndeterminateProgressCalculator()
        // 1 / (1 + 1) = 0.5
        assertEquals(0.5f, calc.calculate(1, -1), 0.001f)
    }

    @Test
    fun `IndeterminateProgressCalculator - extractedCount 100 is close to 1 but below`() {
        val calc = IndeterminateProgressCalculator()
        val value = calc.calculate(100, -1)
        assertTrue("Should be > 0.98", value > 0.98f)
        assertTrue("Should never reach 1.0", value < 1.0f)
    }

    @Test
    fun `IndeterminateProgressCalculator - never returns 1 point 0`() {
        val calc = IndeterminateProgressCalculator()
        for (n in listOf(1, 10, 100, 1000, 10000)) {
            assertTrue("Progress must be < 1.0 for n=$n", calc.calculate(n, -1) < 1.0f)
        }
    }

    @Test
    fun `IndeterminateProgressCalculator - progress increases as extractedCount grows`() {
        val calc = IndeterminateProgressCalculator()
        val p1 = calc.calculate(1, -1)
        val p10 = calc.calculate(10, -1)
        val p100 = calc.calculate(100, -1)
        assertTrue("p10 > p1", p10 > p1)
        assertTrue("p100 > p10", p100 > p10)
    }

    // ===== SingleFileProgressCalculator =====

    @Test
    fun `SingleFileProgressCalculator - extractedCount 0 returns 0`() {
        val calc = SingleFileProgressCalculator()
        assertEquals(0f, calc.calculate(0, 1), 0.001f)
    }

    @Test
    fun `SingleFileProgressCalculator - extractedCount 1 returns 1 point 0`() {
        val calc = SingleFileProgressCalculator()
        assertEquals(1.0f, calc.calculate(1, 1), 0.001f)
    }

    @Test
    fun `SingleFileProgressCalculator - extractedCount 5 returns 1 point 0`() {
        val calc = SingleFileProgressCalculator()
        assertEquals(1.0f, calc.calculate(5, 1), 0.001f)
    }
}
