package build.conductor.android.client.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearVoiceGateTest {
    private fun gate(filter: VoiceFilter, background: List<Float>, speech: List<Float>) = NearVoiceGate(filter).apply {
        background.forEach(::onLevel)
        onSpeechStart()
        speech.forEach(::onLevel)
    }

    @Test
    fun `a voice well above the background is near`() {
        assertTrue(gate(VoiceFilter.NEAR, background = listOf(-2f, -1f, -2f), speech = listOf(3f, 8f, 6f)).isNear)
    }

    @Test
    fun `a voice a little above the background is far, unless the filter is off`() {
        val background = listOf(1f, 2f, 1f)
        val speech = listOf(3f, 5f, 4f)

        assertFalse(gate(VoiceFilter.NEAR, background, speech).isNear)
        assertTrue(gate(VoiceFilter.ANY, background, speech).isNear)
    }

    @Test
    fun `very near needs a larger rise than near`() {
        val background = listOf(-2f, -2f)
        val speech = listOf(5f)

        assertTrue(gate(VoiceFilter.NEAR, background, speech).isNear)
        assertFalse(gate(VoiceFilter.VERY_NEAR, background, speech).isNear)
    }

    @Test
    fun `a short loud background noise does not lift the background`() {
        assertTrue(gate(VoiceFilter.NEAR, background = listOf(-2f, 9f, -2f, -1f, -2f), speech = listOf(6f)).isNear)
    }

    @Test
    fun `with no loudness reports the gate lets the words through`() {
        assertTrue(gate(VoiceFilter.VERY_NEAR, background = emptyList(), speech = emptyList()).isNear)
        assertTrue(gate(VoiceFilter.VERY_NEAR, background = emptyList(), speech = listOf(1f)).isNear)
    }
}
