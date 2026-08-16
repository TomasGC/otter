package app.otter.unit.domain.model

import app.otter.domain.model.FileCategoryFilterState
import app.otter.domain.model.next
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileCategoryFilterStateTest {

    @Test
    fun `null cycles to INCLUDE`() {
        val result: FileCategoryFilterState? = null
        assertEquals(FileCategoryFilterState.INCLUDE, result.next())
    }

    @Test
    fun `INCLUDE cycles to EXCLUDE`() {
        assertEquals(FileCategoryFilterState.EXCLUDE, FileCategoryFilterState.INCLUDE.next())
    }

    @Test
    fun `EXCLUDE cycles to null`() {
        assertNull(FileCategoryFilterState.EXCLUDE.next())
    }
}
