package com.aura.app.dreams

import org.junit.Assert.assertThrows
import org.junit.Test

class DreamModelsTest {
    @Test
    fun `dream window rejects empty and reversed intervals`() {
        assertThrows(IllegalArgumentException::class.java) { DreamWindow(10L, 10L) }
        assertThrows(IllegalArgumentException::class.java) { DreamWindow(20L, 10L) }
    }

    @Test
    fun `dream window rejects negative start`() {
        assertThrows(IllegalArgumentException::class.java) { DreamWindow(-1L, 10L) }
    }
}
