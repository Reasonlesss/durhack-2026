package durhack26.music

import kotlin.math.hypot

/*
 * The timeline: which notes play when, and what effects they go through.
 *
 * Instruments with no incoming edges play at beat 0. Each edge then plays its target
 * 1 / segments of a beat after its source, so the graph is walked outward from those
 * starting notes. Loops would run forever, so the walk stops at [TIMELINE_BEATS].
 *
 * Time is counted in ticks, [TICKS_PER_BEAT] to a beat. Every allowed segment count divides
 * it, so all times are whole ticks and identical times compare exactly.
 *
 * A universe builds its timeline lazily and caches it (see Universe.timeline).
 */

const val TICKS_PER_BEAT = 24
const val TIMELINE_BEATS = 16

/**
 * An effect as applied to notes.
 * - [excess]: how much room the shape has to spare, in world pixels: the distance from its
 *   centre to the nearest point of its outline, minus the distance to the furthest note
 *   inside it. 0 when the notes reach past that nearest point (e.g. into a corner).
 * - [rotation]: the effect's rotation in radians, in 0 until 2π.
 */
class EffectParams(val effect: Effect, val excess: Double, val rotation: Double) {
    /** The excess as a fraction of the inner radius, 0..1. More room = stronger effect. */
    val amount get() = (excess / effect.innerRadius).coerceIn(0.0, 1.0)

    /** The rotation as a fraction of a full turn, 0..1. */
    val turn get() = rotation / (2 * kotlin.math.PI)
}

/** [instrument] playing at [tick], through [effects] in order: innermost first. */
class NoteEvent(val tick: Int, val instrument: Instrument, val effects: List<EffectParams>) {
    val beat get() = tick.toDouble() / TICKS_PER_BEAT
}

/** Every note event, ordered by time, and the parameters of every effect. */
class Timeline(val events: List<NoteEvent>, private val effectParams: Map<Effect, EffectParams>) {
    fun paramsOf(effect: Effect): EffectParams? = effectParams[effect]

    /**
     * How long the timeline lasts before it repeats: whole beats, with the beat holding the
     * last note included (so a note one beat after the start is heard before it loops).
     */
    val loopTicks = ((events.maxOfOrNull { it.tick } ?: 0) / TICKS_PER_BEAT + 1) * TICKS_PER_BEAT

    private val eventsByTick = events.groupBy { it.tick }

    fun eventsAt(tick: Int): List<NoteEvent> = eventsByTick[tick].orEmpty()
}

fun buildTimeline(universe: Universe): Timeline {
    val notesInside = universe.effects.associateWith { effect ->
        universe.instruments.filter { effect.contains(it.x, it.y) }
    }
    val params = effectParams(notesInside)
    val effectsByNote = effectsByNote(universe, notesInside, params)

    val outgoing = universe.edges.groupBy { it.from }
    val starts = universe.instruments.filter { note -> universe.edges.none { it.to === note } }
    val end = TIMELINE_BEATS * TICKS_PER_BEAT

    // Breadth-first from the starting notes. A note reached twice at the same tick (two
    // paths of equal length) only plays once.
    val queue = ArrayDeque(starts.map { it to 0 })
    val seen = HashSet<Pair<Instrument, Int>>()
    val events = mutableListOf<NoteEvent>()
    while (queue.isNotEmpty()) {
        val (note, tick) = queue.removeFirst()
        if (tick >= end || !seen.add(note to tick)) continue
        events += NoteEvent(tick, note, effectsByNote.getValue(note))
        for (edge in outgoing[note].orEmpty()) {
            queue += edge.to to tick + TICKS_PER_BEAT / edge.segments
        }
    }
    return Timeline(events.sortedBy { it.tick }, params)
}

/** Each effect's parameters, from the notes inside it. See [EffectParams]. */
private fun effectParams(notesInside: Map<Effect, List<Instrument>>): Map<Effect, EffectParams> =
    notesInside.mapValues { (effect, notes) ->
        val furthest = notes.maxOfOrNull { hypot(it.x - effect.x, it.y - effect.y) } ?: 0.0
        EffectParams(
            effect = effect,
            excess = (effect.innerRadius - furthest).coerceAtLeast(0.0),
            rotation = effect.rotation.mod(2 * kotlin.math.PI),
        )
    }

/**
 * For each note, the effects it's inside, smallest first: an effect that encloses another
 * is bigger, so it's applied after (around) the one inside it.
 */
private fun effectsByNote(
    universe: Universe,
    notesInside: Map<Effect, List<Instrument>>,
    params: Map<Effect, EffectParams>,
): Map<Instrument, List<EffectParams>> {
    return universe.instruments.associateWith { note ->
        universe.effects
            .filter { note in notesInside.getValue(it) }
            .sortedBy { it.area }
            .map { params.getValue(it) }
    }
}
