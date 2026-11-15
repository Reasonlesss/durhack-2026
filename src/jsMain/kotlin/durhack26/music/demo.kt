package durhack26.music

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The demo song: a two-bar (8-beat) dance loop in the "Sad" (minor) mood, built out of
 * the same pieces a user would place by hand. One universe per part.
 *
 * Chords, two beats each: C minor → A flat → E flat → B flat (the i–VI–III–VII
 * progression behind a lot of dance anthems).
 *
 *   Drums  four-on-the-floor kick, claps on 2 and 4, open hi-hats on every offbeat
 *   Bass   offbeat bass bouncing between each chord's root and the octave above
 *   Hook   a supersaw riff in a 3-3-2 rhythm: two repeated notes, then a leap,
 *          once per chord, with reverb and a dotted-eighth echo
 *   Pad    the chords held underneath, voice-led so each note moves as little as possible
 *
 * Everything that isn't a root is reached by an edge, so the timing is all in the graph:
 * the drums hang every clap and hat off a kick, and rests are silent notes. Every part's
 * last note is in beat 8, so they all loop at exactly 8 beats and stay locked together.
 *
 * Notes are laid out left to right by when they play, BEAT_WIDTH per beat, so the picture
 * reads like the music.
 */

private const val START_X = 200.0
private const val BEAT_WIDTH = 110.0

/** Replaces the open universes with the demo song and switches to its mood. */
fun loadDemoSong() {
    EditorState.scale = Scale.SAD
    val parts = listOf(drums(), bass(), hook(), pad())
    EditorState.universes.clear()
    EditorState.universes += parts
    EditorState.selectedUniverse = parts.first()
}

// Levels in the minor scale (see levels.kt): 7 = C4, 8 = D4, 9 = Eb4, 10 = F4, 11 = G4,
// 12 = Ab4, 13 = Bb4, 14 = C5 and so on; 5 = Ab3, 6 = Bb3.
// Drums pick their sound by level instead: 7 = kick, 9 = clap, 13 = open hat.
private const val KICK = 7
private const val CLAP = 9
private const val OPEN_HAT = 13

/** Each chord's root, as a level, in order. Each lasts two beats. */
private val ROOTS = listOf(7, 5, 9, 6)  // C, Ab, Eb, Bb

private fun drums() = part("Drums") {
    val kit = InstrumentType.DRUMS
    val kicks = chain(kit, (0 until 8).map { it.toDouble() to KICK })
    kicks.forEachIndexed { beat, kick ->
        // A hat half a beat after every kick lands on the offbeat: "boots and cats".
        branch(kick, kit, beat + 0.5, OPEN_HAT)
        // A clap a beat after every other kick lands on 2 and 4.
        if (beat % 2 == 0) branch(kick, kit, beat + 1.0, CLAP)
    }
}

private fun bass() = part("Bass") {
    // A silent note on beat 1 so the bass can start on the offbeat after it.
    val steps = mutableListOf(InstrumentType.SILENT to (0.0 to ROOTS[0]))
    ROOTS.forEachIndexed { chord, root ->
        val beat = chord * 2.0
        steps += InstrumentType.BASS to (beat + 0.5 to root)
        steps += InstrumentType.BASS to (beat + 1.5 to root + 7)  // up an octave
    }
    chain(steps)
}

private fun hook() = part("Hook") {
    // Per chord: (repeated note, leap note). Repeat, repeat, leap is what makes it stick.
    val phrases = listOf(
        16 to 18,  // Eb5 Eb5 G5   over C minor
        16 to 14,  // Eb5 Eb5 C5   over A flat
        13 to 16,  // Bb4 Bb4 Eb5  over E flat
        15 to 17,  // D5  D5  F5   over B flat
    )
    val lead = InstrumentType.SUPERSAW
    val rest = InstrumentType.SILENT
    val steps = mutableListOf<Pair<InstrumentType, Pair<Double, Int>>>()
    phrases.forEachIndexed { chord, (repeated, leap) ->
        // 3-3-2: hits on 0, 0.75 and 1.5 of each two beats. 0.75 beats isn't one edge
        // long, so a silent note half a beat in splits each gap (0.5 + 0.25).
        val beat = chord * 2.0
        steps += lead to (beat to repeated)
        steps += rest to (beat + 0.5 to repeated)
        steps += lead to (beat + 0.75 to repeated)
        steps += rest to (beat + 1.25 to repeated)
        steps += lead to (beat + 1.5 to leap)
    }
    chain(steps)

    // Around every note: a little reverb, and an echo three quarters of a beat behind.
    effect(EffectType.REVERB, beat = 3.75, level = 16, scale = 12.0, turn = 0.4)
    effect(EffectType.DELAY, beat = 3.75, level = 16, scale = 14.0, turn = 0.714)
}

private fun pad() = part("Pad") {
    // Three voices; each column is one chord: Cm, Ab, Eb, Bb.
    val voices = listOf(
        listOf(11, 12, 11, 10),  // G   Ab  G   F
        listOf(9, 9, 9, 8),      // Eb  Eb  Eb  D
        listOf(7, 7, 6, 6),      // C   C   Bb  Bb
    )
    for (voice in voices) {
        val steps = mutableListOf<Pair<InstrumentType, Pair<Double, Int>>>()
        voice.forEachIndexed { chord, level ->
            // Each chord is held for two beats: the pad, then a silent note to wait on.
            steps += InstrumentType.PAD to (chord * 2.0 to level)
            steps += InstrumentType.SILENT to (chord * 2.0 + 1 to level)
        }
        chain(steps)
    }
    effect(EffectType.CHORUS, beat = 3.5, level = 9, scale = 16.0, turn = 0.2)
}

/** Builds one universe with [build], then scrolls it so its notes are in view. */
private fun part(name: String, build: PartBuilder.() -> Unit): Universe {
    val builder = PartBuilder(Universe(name))
    builder.build()
    builder.centreCamera()
    return builder.universe
}

private class PartBuilder(val universe: Universe) {
    /** The beat each note was placed on, for working out edge lengths. */
    private val beats = mutableMapOf<Instrument, Double>()

    /** Notes of one type, played one after another at the given (beat, level)s. */
    fun chain(type: InstrumentType, steps: List<Pair<Double, Int>>) = chain(steps.map { type to it })

    /** Like the other [chain], but each step picks its own type: e.g. silent notes as rests. */
    fun chain(steps: List<Pair<InstrumentType, Pair<Double, Int>>>): List<Instrument> {
        val notes = steps.map { (type, step) -> note(type, step.first, step.second) }
        notes.zipWithNext { from, to -> link(from, to) }
        return notes
    }

    /** A note at [beat] played from [from] by its own edge, alongside whatever else [from] leads to. */
    fun branch(from: Instrument, type: InstrumentType, beat: Double, level: Int): Instrument =
        note(type, beat, level).also { link(from, it) }

    fun effect(type: EffectType, beat: Double, level: Int, scale: Double, turn: Double = 0.0) {
        universe.effects += Effect(x = xOf(beat), y = levelY(level), type = type, rotation = turn * 2 * PI, scale = scale)
    }

    fun centreCamera() {
        val notes = universe.instruments
        if (notes.isEmpty()) return
        val centreX = (notes.minOf { it.x } + notes.maxOf { it.x }) / 2
        val centreY = (notes.minOf { it.y } + notes.maxOf { it.y }) / 2
        universe.panBy(
            centreX - CanvasState.width / 2 - universe.cameraX,
            centreY - CanvasState.height / 2 - universe.cameraY,
        )
    }

    private fun note(type: InstrumentType, beat: Double, level: Int): Instrument {
        val instrument = Instrument(type, shapeFor(type), xOf(beat), levelY(level))
        universe.instruments += instrument
        beats[instrument] = beat
        return instrument
    }

    private fun link(from: Instrument, to: Instrument) {
        universe.connect(from, to)
        universe.edges.last().segments = segmentsFor(beats.getValue(to) - beats.getValue(from))
    }

    /** The toolbar shape for [type], adding it to the toolbar the first time it's used. */
    private fun shapeFor(type: InstrumentType): InstrumentShape {
        universe.instrumentTypes.entries.firstOrNull { it.value == type }?.let { return it.key }
        val shape = checkNotNull(universe.nextInstrumentShape()) { "a part can only use 8 instrument types" }
        universe.instrumentTypes[shape] = type
        return shape
    }

    private fun xOf(beat: Double) = START_X + beat * BEAT_WIDTH

    /** The edge setting for a gap of [beats]: 1 beat = 1 segment, half a beat = 2, and so on. */
    private fun segmentsFor(beats: Double): Int {
        val segments = (1 / beats).roundToInt()
        require(segments in SEGMENT_OPTIONS && abs(1.0 / segments - beats) < 1e-9) {
            "notes $beats beats apart can't be joined by one edge"
        }
        return segments
    }
}
