package durhack26.music

import durhack26.music.tone.Compressor
import durhack26.music.tone.Context
import durhack26.music.tone.StandardAudioContext
import durhack26.music.tone.ToneAudioNode
import durhack26.music.tone.getTransport
import durhack26.music.tone.now
import durhack26.music.tone.setContext
import durhack26.music.tone.start
import kotlinx.browser.window
import kotlin.math.roundToInt

/*
 * Plays every enabled universe's timeline with Tone.js.
 *
 * A callback runs once per timeline tick. Each universe loops its own timeline, so on every
 * tick the notes due at (tick mod that universe's loop length) are played. The timeline is
 * read fresh each tick (it's cached, so this is cheap), which means edits are heard live.
 *
 * Audio nodes are shared as much as possible, because every one costs the audio thread work
 * all the time, even when silent; one synth and effect set per note was enough to overload
 * it and make it crackle and cut out.
 * - Effects: every note inside exactly the same effects plays through one [Chain] of their
 *   nodes, innermost first, then to [master].
 * - Synths: notes of the same instrument (and drum) through the same chain share a [Pool]
 *   of synths, which only grows to as many notes as actually sound at once.
 * Pools and chains nothing has played through for a while are disposed.
 *
 * What each instrument and effect sounds like is defined on InstrumentType and EffectType.
 */

/** The slowest and fastest tempos allowed, in BPM. */
const val MIN_BPM = 60
const val MAX_BPM = 200

/** How long a beat lasts at the current tempo, in seconds. */
fun beatSeconds() = 60.0 / EditorState.bpm

/**
 * Sets the tempo, rounded to a whole BPM and kept within [MIN_BPM]..[MAX_BPM]. Playback
 * follows straight away: the timeline is clocked in beats, not seconds.
 */
fun setBpm(bpm: Double) {
    EditorState.bpm = bpm.roundToInt().coerceIn(MIN_BPM, MAX_BPM)
    getTransport().bpm.value = EditorState.bpm
    // Effects timed in beats change now, rather than when a note next plays through them.
    chains.values.forEach(::retime)
}

/**
 * The sample rate everything runs at. Browsers otherwise use the output device's, which
 * can be 96 kHz or more (e.g. SteelSeries Sonar): twice the work for no audible gain.
 */
private const val SAMPLE_RATE = 48000

/**
 * How far ahead notes are scheduled, in seconds. Gives the scheduler slack when the page
 * is busy, so notes don't arrive late and stutter.
 */
private const val LOOK_AHEAD_SECONDS = 0.15

/**
 * Sets up the audio context. Must run before anything else makes a sound. "balanced" asks
 * for a bigger audio buffer than the default "interactive", which stops dropouts on
 * machines (often Windows) that can't keep up with the smallest one.
 */
fun setupAudio() {
    val rawOptions: dynamic = js("({})")
    rawOptions.sampleRate = SAMPLE_RATE
    rawOptions.latencyHint = "balanced"
    val options: dynamic = js("({})")
    options.context = StandardAudioContext(rawOptions)
    options.latencyHint = "balanced"
    options.lookAhead = LOOK_AHEAD_SECONDS
    // Closes the context Tone.js makes as soon as it's loaded, at the device's sample rate.
    setContext(Context(options), disposeOld = true)
    getTransport().bpm.value = EditorState.bpm
}

/** How long a note is allowed to ring after it ends, in seconds, when its synth doesn't say. */
private const val RELEASE_SECONDS = 1.6

/** The most synths one pool keeps. Past it, the one that started longest ago is reused. */
private const val MAX_POLYPHONY = 8

/**
 * How long a pool or chain is kept after it last played, in seconds: longer than the longest
 * loop at the current tempo, so one note per loop doesn't rebuild it every time; that also
 * lets tails ring out.
 */
private fun idleSeconds() = TIMELINE_BEATS * beatSeconds() + 5

/** The effects a note is inside, innermost first, each with the type it had when the chain was built. */
private typealias ChainKey = List<Pair<Effect, EffectType>>

/** One set of effect nodes, wired innermost → outermost → master. */
private class Chain(val key: ChainKey, val nodes: List<EffectNode>) {
    val input get() = nodes.first().input

    /** How many pools play through it. At 0 it's disposed. */
    var users = 0

    /** The (amount, turn) last applied to each effect, so unchanged ones aren't touched. */
    var applied: List<Pair<Double, Double>> = emptyList()

    fun dispose() = nodes.forEach { it.dispose() }
}

private val chains = mutableMapOf<ChainKey, Chain>()

/** A synth in a [Pool], and the audio time it's sounding until. */
private class Slot(val sound: InstrumentSound) {
    var busyUntil = 0.0
}

private data class PoolKey(val type: InstrumentType, val synthKey: Int, val chain: ChainKey)

/** Synths for one instrument (and drum, see synthKey) through one chain (or none). */
private class Pool(val type: InstrumentType, val synthKey: Int, val chain: Chain?) {
    val slots = mutableListOf<Slot>()

    /** The audio time it last played. */
    var lastUsed = 0.0

    /**
     * A synth to play a note at [time] on. Drums only ever get one, so each hit cuts off the
     * last, as on a drum machine. Anything else gets a synth that's finished, or a new one.
     */
    fun slotFor(level: Int, time: Double): Slot? {
        val free = if (type.category == InstrumentCategory.DRUMS) slots.firstOrNull() else slots.firstOrNull { it.busyUntil <= time }
        if (free != null) return free
        if (slots.size >= MAX_POLYPHONY) return slots.minBy { it.busyUntil }
        val sound = type.createSound(level) ?: return null
        sound.output.connect(chain?.input ?: master)
        return Slot(sound).also { slots += it }
    }

    fun dispose() = slots.forEach { slot -> slot.sound.nodes.forEach { it.dispose() } }
}

private val pools = mutableMapOf<PoolKey, Pool>()

/**
 * Everything plays through this on its way to the speakers. Distorted, resonant and stacked
 * sounds get loud fast; it keeps them under full scale, where they'd otherwise be hard-clipped
 * into crackle. Unlike Tone.js's Limiter (10 ms release, 30 dB knee), it catches fast peaks,
 * and releases slowly enough not to distort bass.
 */
private val master: ToneAudioNode by lazy {
    Compressor(js("({ threshold: -6, ratio: 20, knee: 0, attack: 0.001, release: 0.15 })")).toDestination()
}
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
        // One timeline tick is this many transport ticks, so it keeps time with the tempo.
        repeatId = transport.scheduleRepeat(::onTick, "${transport.ppq / TICKS_PER_BEAT}i")
        transport.start()
    }
}

private fun stopPlayback() {
    EditorState.playing = false
    val transport = getTransport()
    repeatId?.let { transport.clear(it) }
    repeatId = null
    transport.stop()
    pools.values.forEach { it.dispose() }
    pools.clear()
    chains.values.forEach { it.dispose() }
    chains.clear()
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
        for (event in timeline.eventsAt(now % timeline.loopTicks)) play(event, time)
    }
    if (now % TICKS_PER_BEAT == 0) disposeIdle(time)
}

/** Plays [event] at audio time [time] on a synth from its pool. */
private fun play(event: NoteEvent, time: Double) {
    val note = event.instrument
    if (note.type == InstrumentType.SILENT) return  // nothing to play, so don't build anything
    val level = note.level
    val key = PoolKey(note.type, note.type.synthKey(level), event.effects.map { it.effect to it.effect.type })
    val pool = pools.getOrPut(key) { Pool(key.type, key.synthKey, acquire(key.chain)) }
    pool.chain?.let { applyParams(it, event.effects) }

    val slot = pool.slotFor(level, time) ?: return
    slot.busyUntil = time + noteSeconds(note.type.duration) + releaseSeconds(slot.sound)
    pool.lastUsed = time
    note.type.play(slot.sound, level, time)
    pulseWhenHeard(note, time)
}

/**
 * How long [sound] rings after a note ends, from its envelope's release; [RELEASE_SECONDS]
 * if that's not a plain number of seconds. The shorter it is, the fewer synths a pool needs.
 */
private fun releaseSeconds(sound: InstrumentSound): Double {
    val synth = sound.synth.asDynamic()
    val release = synth.envelope?.release ?: synth.voice0?.envelope?.release
    return if (jsTypeOf(release) == "number") release.unsafeCast<Double>() else RELEASE_SECONDS
}

/** How long a note value like "8n" (an eighth note) lasts, in seconds. */
private fun noteSeconds(duration: String) =
    duration.removeSuffix("n").toDoubleOrNull()?.let { beatSeconds() * 4 / it } ?: beatSeconds()

/** Sets [chain]'s effects from [effects], skipping any that haven't changed. */
private fun applyParams(chain: Chain, effects: List<EffectParams>) {
    val params = effects.map { it.amount to it.turn }
    if (params == chain.applied) return
    params.forEachIndexed { i, (amount, turn) ->
        if (chain.applied.getOrNull(i) != params[i]) chain.key[i].second.applyParams(chain.nodes[i], amount, turn)
    }
    chain.applied = params
}

/** Re-applies [chain]'s tempo-dependent effects (delay, stutter) after a tempo change. */
private fun retime(chain: Chain) {
    chain.applied.forEachIndexed { i, (amount, turn) ->
        val type = chain.key[i].second
        if (type.tempoDependent) type.applyParams(chain.nodes[i], amount, turn)
    }
}

/** The chain for [key], built if there isn't one yet, with one more user. Null for no effects. */
private fun acquire(key: ChainKey): Chain? {
    if (key.isEmpty()) return null
    val chain = chains.getOrPut(key) {
        val nodes = key.map { (_, type) -> type.createNode() }
        // innermost effect → … → outermost effect → master → speakers
        nodes.zipWithNext { from, to -> from.output.connect(to.input) }
        nodes.last().output.connect(master)
        Chain(key, nodes)
    }
    chain.users++
    return chain
}

/** Disposes pools that haven't played for [idleSeconds], and any chain left with no pools. */
private fun disposeIdle(time: Double) {
    val idleSeconds = idleSeconds()
    val idle = pools.filterValues { time - it.lastUsed > idleSeconds }
    for ((key, pool) in idle) {
        pools.remove(key)
        pool.dispose()
        val chain = pool.chain ?: continue
        if (--chain.users == 0) {
            chains.remove(chain.key)
            chain.dispose()
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
