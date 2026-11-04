@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.voice

import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.data.api.ApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class VoiceConversationTest {
    private val repository = FakeConductorRepository()
    private val speaker = FakeSpeaker()
    private val listener = FakeListener()
    private val onset = FakeOnsetDetector()
    private val conversation by lazy {
        VoiceConversation(TARGET, VoiceIo(speaker, listener, onset), AgentLink(repository, FakeConductorRepository.SESSION_ID, newMessageId = { "voice-1" }))
    }

    private val state: VoiceState
        get() = conversation.uiState.value

    private fun TestScope.start(): Job = backgroundScope.launch { conversation.run() }.also { runCurrent() }

    /** Moves virtual time in small steps until [condition] holds, so a test does not depend on exact timings. */
    private fun TestScope.advanceUntil(limitMillis: Long = 60_000, condition: () -> Boolean) {
        var waitedMillis = 0L
        while (!condition()) {
            check(waitedMillis < limitMillis) { "The condition did not hold within $limitMillis ms. State: $state, said: ${speaker.events}" }
            advanceTimeBy(STEP_MILLIS)
            runCurrent()
            waitedMillis += STEP_MILLIS
        }
    }

    private fun TestScope.awaitListening() = advanceUntil { state.phase == VoicePhase.LISTENING }

    @Test
    fun `the app greets the user with the session and the agent status, then gives the turn with a tone`() = runTest {
        start()
        awaitListening()

        assertEquals(listOf("Voice on. Fix CI.", "The agent is waiting for you.", "cue:YOUR_TURN"), speaker.events)
    }

    @Test
    fun `a spoken prompt goes to the agent after a short wait for more words`() = runTest {
        start()
        awaitListening()
        listener.enqueue(ListenScript.Speak("Fix the failing test"))

        advanceUntil { state.phase == VoicePhase.CONFIRMING }
        advanceTimeBy(2_000)
        assertTrue(repository.sentMessages.isEmpty())

        advanceUntil { repository.sentMessages.isNotEmpty() }
        assertEquals("Fix the failing test" to "voice-1", repository.sentMessages.single())
        advanceUntil { speaker.events.last() == "cue:SENT" }
    }

    @Test
    fun `words in the wait join the prompt, and cancel drops it`() = runTest {
        start()
        awaitListening()
        listener.enqueue(ListenScript.Speak("Add a test"), ListenScript.Speak("for the parser"))
        advanceUntil { repository.sentMessages.isNotEmpty() }
        assertEquals("Add a test for the parser", repository.sentMessages.single().first)

        awaitListening()
        listener.enqueue(ListenScript.Speak("Delete the branch"), ListenScript.Speak("scratch that"))
        advanceUntil { "Cancelled. I did not send it." in speaker.sentences }
        assertEquals(1, repository.sentMessages.size)
    }

    @Test
    fun `the app reads new agent text aloud, plays a tone at the end of the turn, and listens again`() = runTest {
        start()
        awaitListening()
        listener.enqueue(ListenScript.Speak("Fix the failing test"))
        advanceUntil { repository.sentMessages.isNotEmpty() }

        repository.sessionStatusValue = "working"
        repository.addAgentText("I fixed the test. All checks pass.")
        advanceUntil { "All checks pass." in speaker.sentences }
        repository.sessionStatusValue = "idle"
        repository.addTurnEnd()
        advanceUntil { speaker.events.last() == "cue:YOUR_TURN" && state.phase == VoicePhase.LISTENING }

        val afterSend = speaker.events.dropWhile { it != "cue:SENT" }
        assertEquals(listOf("cue:SENT", "I fixed the test.", "All checks pass.", "cue:AGENT_DONE", "cue:YOUR_TURN"), afterSend)
    }

    @Test
    fun `agent text that arrives before the user speaks closes the microphone`() = runTest {
        repository.sessionStatusValue = "working"
        start()
        awaitListening()

        val listenStartMillis = testScheduler.currentTime
        repository.addAgentText("Let me look at the logs.")
        advanceUntil { state.phase == VoicePhase.SPEAKING }

        assertEquals("Let me look at the logs.", state.caption)
        assertTrue(testScheduler.currentTime < listenStartMillis + FakeListener.SILENCE_MILLIS)
    }

    @Test
    fun `the user interrupts by voice, the prompt goes out, and continue reads the rest of the reply`() = runTest {
        repository.sessionStatusValue = "working"
        start()
        repository.addAgentText("One. Two. Three.")
        advanceUntil { state.phase == VoicePhase.SPEAKING && state.caption == "Two." }

        listener.enqueue(ListenScript.Speak("Use the staging database"))
        onset.speakOver()
        advanceUntil { repository.sentMessages.isNotEmpty() }
        assertEquals("Use the staging database", repository.sentMessages.single().first)
        assertEquals(1, speaker.sentences.count { it == "Two." })

        awaitListening()
        listener.enqueue(ListenScript.Speak("continue"))
        advanceUntil { "Three." in speaker.sentences }
        assertEquals(listOf("Two.", "Three."), speaker.sentences.takeLast(2))
        assertEquals(1, repository.sentMessages.size)
    }

    @Test
    fun `a tap interrupts, and silence after it resumes the same sentence`() = runTest {
        repository.sessionStatusValue = "working"
        start()
        repository.addAgentText("One. Two.")
        advanceUntil { state.phase == VoicePhase.SPEAKING && state.caption == "One." }

        conversation.tap()
        runCurrent()
        assertEquals(VoicePhase.LISTENING, state.phase)
        advanceUntil { "Two." in speaker.sentences }

        assertEquals(listOf("One.", "One.", "Two."), speaker.sentences.filter { it == "One." || it == "Two." })
    }

    @Test
    fun `two voice interruptions with no words turn voice interruption off`() = runTest {
        repository.sessionStatusValue = "working"
        start()
        repository.addAgentText("One. Two. Three. Four. Five. Six.")
        advanceUntil { state.phase == VoicePhase.SPEAKING && state.caption == "One." }
        onset.speakOver()
        advanceUntil { state.phase == VoicePhase.SPEAKING && state.caption == "Two." }
        onset.speakOver()

        advanceUntil { speaker.sentences.any { it.startsWith("I cannot tell your voice from mine") } }
        advanceUntil { state.caption == "Three." }
        assertFalse(onset.isWaiting)
    }

    @Test
    fun `words from a far voice do not go to the agent, and the app says why only once`() = runTest {
        start()
        awaitListening()
        listener.enqueue(ListenScript.SpeakFar("Buy now while stocks last"))
        advanceUntil { speaker.sentences.any { it.startsWith("I heard a voice far from the phone") } }

        awaitListening()
        listener.enqueue(ListenScript.SpeakFar("Call today"), ListenScript.Speak("Run the tests"))
        advanceUntil { repository.sentMessages.isNotEmpty() }

        assertEquals("Run the tests", repository.sentMessages.single().first)
        assertEquals(1, speaker.sentences.count { it.startsWith("I heard a voice far from the phone") })
    }

    @Test
    fun `stop cancels a working agent and says so`() = runTest {
        repository.sessionStatusValue = "working"
        start()
        awaitListening()
        listener.enqueue(ListenScript.Speak("Stop."))

        advanceUntil { "I stopped the agent." in speaker.sentences }
        assertEquals(1, repository.cancelCount)
        assertTrue(repository.sentMessages.isEmpty())
    }

    @Test
    fun `the app reads a question with its options, and the spoken reply goes out as the answers`() = runTest {
        repository.sessionStatusValue = "working"
        start()
        repository.addQuestion("t1")
        advanceUntil { state.hasOpenQuestion && state.phase == VoicePhase.LISTENING }

        assertTrue(speaker.sentences.containsAll(listOf("The agent asks: Which colour?", "Option 1, Red.", "Option 2, Green.", "You can choose more than one.")))
        listener.enqueue(ListenScript.Speak("Green and small"))
        advanceUntil { repository.sentMessages.isNotEmpty() }
        assertEquals("My answers to your questions:\nGreen and small", repository.sentMessages.single().first)
        assertFalse(state.hasOpenQuestion)
    }

    @Test
    fun `repeat reads the last reply from before voice mode started`() = runTest {
        repository.addUserMessage("Explain the bug", "c1")
        repository.addAgentText("The cache was stale.")
        start()
        awaitListening()
        assertTrue("Say repeat to hear its last message." in speaker.sentences)

        listener.enqueue(ListenScript.Speak("say that again"))
        advanceUntil { "The cache was stale." in speaker.sentences }
        assertTrue(repository.sentMessages.isEmpty())
    }

    @Test
    fun `a minute of silence pauses the microphone, and a tap opens it again`() = runTest {
        start()
        advanceUntil(limitMillis = 120_000) { state.phase == VoicePhase.PAUSED }
        assertTrue(speaker.sentences.last().startsWith("Voice paused."))
        val listensBeforeTap = listener.listenCount

        advanceTimeBy(30_000)
        assertEquals(listensBeforeTap, listener.listenCount)
        conversation.tap()
        awaitListening()
        assertEquals("cue:YOUR_TURN", speaker.events.last())
    }

    @Test
    fun `the end of a turn opens a paused microphone`() = runTest {
        repository.sessionStatusValue = "working"
        start()
        awaitListening()
        conversation.tap()
        advanceUntil { state.phase == VoicePhase.PAUSED }

        repository.sessionStatusValue = "idle"
        repository.addTurnEnd()
        advanceUntil { state.phase == VoicePhase.LISTENING }
        assertTrue("cue:AGENT_DONE" in speaker.events)
    }

    @Test
    fun `a lost connection is said once, and its return is said too`() = runTest {
        start()
        awaitListening()
        repository.messagesFailure = ApiException.Offline(IOException("down"))
        advanceTimeBy(40_000)
        runCurrent()
        assertEquals(1, speaker.sentences.count { it.startsWith("I lost the connection") })

        repository.messagesFailure = null
        advanceUntil { "The connection is back." in speaker.sentences }
    }

    @Test
    fun `end voice says goodbye and ends the conversation`() = runTest {
        val job = start()
        awaitListening()
        listener.enqueue(ListenScript.Speak("Okay, end voice mode please"))

        advanceUntil { job.isCompleted }
        assertEquals("Voice off.", speaker.sentences.last())
        assertEquals(VoicePhase.ENDED, state.phase)
    }

    @Test
    fun `a fatal listen failure ends voice mode with the reason`() = runTest {
        val job = start()
        awaitListening()
        listener.enqueue(ListenScript.Fail(Heard.Failure("Voice mode needs the microphone permission.", isFatal = true)))

        advanceUntil { job.isCompleted }
        assertEquals("Voice mode needs the microphone permission.", state.problem)
    }

    @Test
    fun `a phone with no text-to-speech engine ends voice mode at once`() = runTest {
        speaker.isEngineReady = false
        val job = start()

        assertTrue(job.isCompleted)
        assertEquals(VoicePhase.ENDED, state.phase)
        assertTrue(speaker.events.isEmpty())
    }

    private companion object {
        const val STEP_MILLIS = 50L
        val TARGET = VoiceTarget("w1", FakeConductorRepository.SESSION_ID, "Fix CI")
    }
}
