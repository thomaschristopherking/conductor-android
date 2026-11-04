package build.conductor.android.client.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceOnsetRuleTest {
    @Test
    fun `a voice well above a quiet room starts after about a quarter of a second`() {
        val rule = VoiceOnsetRule()
        repeat(20) { assertFalse(rule.onFrame(QUIET_ROOM)) }

        val framesToOnset = (1..20).first { rule.onFrame(VOICE) }

        assertEquals(12, framesToOnset)
    }

    @Test
    fun `a short loud click is not a voice`() {
        val rule = VoiceOnsetRule()
        repeat(20) { rule.onFrame(QUIET_ROOM) }
        repeat(5) { assertFalse(rule.onFrame(VOICE)) }

        assertFalse((1..30).any { rule.onFrame(QUIET_ROOM) })
    }

    @Test
    fun `steady sound slowly becomes the quiet level, and a voice must be louder than it`() {
        val rule = VoiceOnsetRule()
        repeat(20) { rule.onFrame(QUIET_ROOM) }
        val steadyEcho = QUIET_ROOM + 10

        assertFalse((1..400).any { rule.onFrame(steadyEcho) })
        assertTrue((1..20).any { rule.onFrame(steadyEcho + 20) })
    }

    @Test
    fun `a stricter voice filter ignores a voice that is clear of the room but not loud`() {
        val farVoice = -40.0
        val anyVoice = VoiceOnsetRule(VoiceFilter.ANY.onsetMinDb)
        val nearVoice = VoiceOnsetRule(VoiceFilter.NEAR.onsetMinDb)
        repeat(20) { anyVoice.onFrame(QUIET_ROOM); nearVoice.onFrame(QUIET_ROOM) }

        assertTrue((1..20).any { anyVoice.onFrame(farVoice) })
        assertFalse((1..20).any { nearVoice.onFrame(farVoice) })
    }

    @Test
    fun `nothing counts while the rule measures the room`() {
        val rule = VoiceOnsetRule()

        assertFalse((1..15).any { rule.onFrame(VOICE) })
    }

    @Test
    fun `the level of full-scale and silent samples`() {
        assertEquals(0.0, VoiceOnsetRule.levelDb(ShortArray(4) { Short.MAX_VALUE }, 4), 0.01)
        assertEquals(-100.0, VoiceOnsetRule.levelDb(ShortArray(4), 4), 0.01)
    }

    private companion object {
        const val QUIET_ROOM = -60.0
        const val VOICE = -25.0
    }
}
