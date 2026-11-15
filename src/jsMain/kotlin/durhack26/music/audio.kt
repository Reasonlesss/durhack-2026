package durhack26.music

import durhack26.music.tone.Limiter
import durhack26.music.tone.ToneAudioNode
import durhack26.music.tone.getTransport
import durhack26.music.tone.now
import durhack26.music.tone.start
import kotlinx.browser.window
import kotlin.math.PI

/*
 * Plays every enabled universe's timeline with Tone.js.
 *
 * A callback runs once per timeline tick. Each universe loops its own timeline, so on every
 * tick the notes due at (tick mod that universe's loop length) are played. The timeline is
 * read fresh each tick (it's cached, so this is cheap), which means edits are heard live.
 *
 * Each note on the canvas gets its own voice: its instrument type's synth, wired through
 * its effect types' nodes, innermost first, then to the speakers. A voice is rebuilt only
 * when its type or set of effects changes; effect parameters are just updated.
 *
 * What each instrument and effect sounds like is defined on InstrumentType and EffectType.
 */

const val BPM = 124.0  // house tempo
const val BEAT_SECONDS = 60.0 / BPM
private const val TICK_SECONDS = BEAT_SECONDS / TICKS_PER_BEAT

/** How long a replaced voice is kept, so its reverb / delay tail can finish, in ms. */
private const val DISPOSE_DELAY_MS = 5000

private class Voice(
    val type: InstrumentType,
    /** The type's synthKey for the level this voice was made for. */
    val synthKey: Int,
    val effects: List<Effect>,
    val sound: InstrumentSound,
    val chain: List<ToneAudioNode>,
) {
    /** The (amount, turn) last applied to each effect, so unchanged ones aren't touched. */
    var applied: List<Pair<Double, Double>> = emptyList()

    fun dispose() {
        sound.nodes.forEach { it.dispose() }
        chain.forEach { it.dispose() }
    }
}

private val voices = mutableMapOf<Instrument, Voice>()

/**
 * Everything plays through this on its way to the speakers. Distorted and stacked sounds
 * get loud fast; it stops them clipping.
 */
private val master: ToneAudioNode by lazy { Limiter(js("({ threshold: -3 })")).toDestination() }
private var tick = 0
private var repeatId: Int? = null

fun togglePlayback() {
    if (EditorState.playing) stopPlayback() else startPlayback()
}

private fun startPlayback() {
    EditorState.playing = true
    // Audio can only start after a click or key press, which this always comes from.
    start().then {
        if (!EditorState.playing || repeatId != null) return@then
        tick = 0
        val transport = getTransport()
        repeatId = transport.scheduleRepeat(::onTick, TICK_SECONDS)
        transport.start()
    }
}

private fun stopPlayback() {
    EditorState.playing = false
    val transport = getTransport()
    repeatId?.let { transport.clear(it) }
    repeatId = null
    transport.stop()
    voices.values.forEach { it.dispose() }
    voices.clear()
}

/**
 * Browsers only let audio start from a click or key press, and a pointer move doesn't
 * count. So every press unlocks it, ready for any preview the drag that follows plays.
 */
fun unlockAudio() {
    start()
}

/** How long a preview's synth is kept before it's disposed, in ms. */
private const val PREVIEW_LIFETIME_MS = 3000

/**
 * Plays one [type] note at [level] straight away, with no effects: used while placing notes
 * so you hear where they are. Works whether or not the song is playing.
 */
fun previewNote(type: InstrumentType, level: Int) {
    // Audio was unlocked by the press that started the drag (see unlockAudio).
    start().then {
        val sound = type.createSound(level) ?: return@then
        sound.output.connect(master)
        type.play(sound, level, now())
        window.setTimeout({ sound.nodes.forEach { it.dispose() } }, PREVIEW_LIFETIME_MS)
    }
}

/** Plays every note due at the current tick, at audio time [time]. */
private fun onTick(time: Double) {
    val now = tick++
    for (universe in EditorState.universes) {
        if (!universe.enabled) continue
        val timeline = universe.timeline
        if (timeline.events.isEmpty()) continue
        for (event in timeline.eventsAt(now % timeline.loopTicks)) {
            val voice = voiceFor(event) ?: continue
            voice.type.play(voice.sound, event.instrument.level, time)
            pulseWhenHeard(event.instrument, time)
        }
    }
}

/**
 * Starts [note]'s pulse animation when it's actually heard. Notes are scheduled a little
 * ahead of time, so this waits until audio time [time] comes round.
 */
private fun pulseWhenHeard(note: Instrument, time: Double) {
    val delayMs = ((time - now()) * 1000).coerceAtLeast(0.0)
    window.setTimeout({ note.lastPlayedAt = window.performance.now() }, delayMs.toInt())
}

/** The voice for [event]'s note, rebuilt if its type or effects changed. Null if silent. */
private fun voiceFor(event: NoteEvent): Voice? {
    val note = event.instrument
    val effects = event.effects.map { it.effect }
    var voice = voices[note]

    val level = note.level
    val synthKey = note.type.synthKey(level)
    if (voice == null || voice.type != note.type || voice.synthKey != synthKey || !sameEffects(voice.effects, effects)) {
        voice?.let { old -> window.setTimeout({ old.dispose() }, DISPOSE_DELAY_MS) }
        voices.remove(note)
        val sound = note.type.createSound(level) ?: return null
        val chain = effects.map { it.type.createNode() }
        // instrument → innermost effect → … → outermost effect → limiter → speakers
        (listOf(sound.output) + chain).zipWithNext { from, to -> from.connect(to) }
        (chain.lastOrNull() ?: sound.output).connect(master)
        voice = Voice(note.type, synthKey, effects, sound, chain)
        voices[note] = voice
    }

    val params = event.effects.map { it.amount to it.turn }
    if (params != voice.applied) {
        event.effects.forEachIndexed { i, p ->
            val (amount, turn) = params[i]
            if (voice.applied.getOrNull(i) != params[i]) p.effect.type.applyParams(voice.chain[i], amount, turn)
        }
        voice.applied = params
    }
    return voice
}

private fun sameEffects(a: List<Effect>, b: List<Effect>) =
    a.size == b.size && a.indices.all { a[it] === b[it] && a[it].type == b[it].type }


