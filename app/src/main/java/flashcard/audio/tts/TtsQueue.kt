package flashcard.audio.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class TtsQueue(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val counter = AtomicLong(0)

    private var currentTargetUttId: String? = null
    private var onCurrentPairDone: (() -> Unit)? = null

    private val tts = TextToSpeech(context) { }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}

            override fun onDone(utteranceId: String) {
                if (utteranceId == currentTargetUttId) {
                    val cb = onCurrentPairDone
                    // clear first to avoid double-calls
                    currentTargetUttId = null
                    onCurrentPairDone = null
                    if (cb != null) main.post { cb() }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                // Treat error as done to avoid autoplay hanging
                if (utteranceId == currentTargetUttId) {
                    val cb = onCurrentPairDone
                    currentTargetUttId = null
                    onCurrentPairDone = null
                    if (cb != null) main.post { cb() }
                }
            }
        })
    }

    fun shutdown() = tts.shutdown()

    fun stop() {
        currentTargetUttId = null
        onCurrentPairDone = null
        tts.stop()
    }

    /**
     * Speak pair in sequence:
     * - Source: FLUSH
     * - Silence: ADD
     * - Target: ADD
     *
     * Calls onDone when TARGET finished (or errored).
     */
    fun speakPair(
        sourceText: String,
        sourceLocale: Locale,
        sourceRate: Float,
        pauseMs: Long,
        targetText: String,
        targetLocale: Locale,
        targetRate: Float = 1.0f,
        onDone: (() -> Unit)? = null
    ) {
        val n = counter.incrementAndGet()
        val sourceId = "utt_source_$n"
        val silenceId = "utt_silence_$n"
        val targetId = "utt_target_$n"

        currentTargetUttId = targetId
        onCurrentPairDone = onDone

        // 1) Source (flush)
        tts.language = sourceLocale
        tts.setSpeechRate(sourceRate)
        tts.setPitch(1.0f)
        tts.speak(sourceText, TextToSpeech.QUEUE_FLUSH, Bundle(), sourceId)

        // 2) Silence (add)
        tts.playSilentUtterance(pauseMs, TextToSpeech.QUEUE_ADD, silenceId)

        // 3) Target (add)
        tts.language = targetLocale
        tts.setSpeechRate(targetRate)
        tts.setPitch(1.0f)
        tts.speak(targetText, TextToSpeech.QUEUE_ADD, Bundle(), targetId)
    }
}
