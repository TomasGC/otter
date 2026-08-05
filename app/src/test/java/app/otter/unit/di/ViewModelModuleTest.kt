package app.otter.di

import org.junit.Assert.assertNull
import org.junit.Test

class ViewModelModuleTest {

    @Test
    fun `provideStartPath returns null`() {
        assertNull(ViewModelModule.provideStartPath())
    }
}
