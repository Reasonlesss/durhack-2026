package durhack26.music

import durhack26.music.tone.Chorus
import durhack26.music.tone.CrossFade
import durhack26.music.tone.Distortion
import durhack26.music.tone.FeedbackDelay
import durhack26.music.tone.Filter
import durhack26.music.tone.Gain
import durhack26.music.tone.Reverb
import durhack26.music.tone.ToneAudioNode
import durhack26.music.tone.Tremolo
import durhack26.music.tone.WaveShaper
import org.w3c.dom.CanvasRenderingContext2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Size of every effect shape at scale 1.0, in CSS pixels. */
const val EFFECT_SIZE = 80.0
private const val HALF = EFFECT_SIZE / 2

/**
 * An effect's outline in its own local space: centred on 0,0, EFFECT_SIZE across, before
 * rotation/scale. Everything about the shape is worked out from it:
 * - [trace] draws it, [contains] says whether a point is inside it (for overlap checks).
 * - [innerRadius] is the distance from the centre to the nearest point of the outline.
 * - [area] is the area inside it.
 */
sealed class EffectShape {
    abstract fun trace(ctx: CanvasRenderingContext2D)
    abstract fun contains(x: Double, y: Double): Boolean
    abstract val innerRadius: Double
    abstract val area: Double

    object Circle : EffectShape() {
        override fun trace(ctx: CanvasRenderingContext2D) = ctx.arc(0.0, 0.0, HALF, 0.0, 2 * PI)
        override fun contains(x: Double, y: Double) = x * x + y * y <= HALF * HALF
        override val innerRadius = HALF
        override val area = PI * HALF * HALF
    }

    /** Any polygon, as its corners in order. */
    class Polygon(private val points: List<Pair<Double, Double>>) : EffectShape() {
        private val edges = points.zip(points.drop(1) + points.first())

        override fun trace(ctx: CanvasRenderingContext2D) {
            points.forEachIndexed { i, (x, y) -> if (i == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y) }
            ctx.closePath()
        }

        /** Ray casting: a point is inside if a ray from it crosses the outline an odd number of times. */
        override fun contains(x: Double, y: Double): Boolean {
            var inside = false
            for ((a, b) in edges) {
                val (ax, ay) = a
                val (bx, by) = b
                if ((ay > y) != (by > y) && x < ax + (y - ay) * (bx - ax) / (by - ay)) inside = !inside
            }
            return inside
        }

        override val innerRadius = edges.minOf { (a, b) -> distanceToSegment(0.0, 0.0, a, b) }

        /** The shoelace formula. */
        override val area = abs(edges.sumOf { (a, b) -> a.first * b.second - b.first * a.second }) / 2
    }

    companion object {
        val SQUARE = Polygon(listOf(-HALF to -HALF, HALF to -HALF, HALF to HALF, -HALF to HALF))
        val TRIANGLE = Polygon(listOf(0.0 to -HALF, HALF to HALF, -HALF to HALF))  // pointing up
        val DIAMOND = Polygon(listOf(0.0 to -HALF, HALF to 0.0, 0.0 to HALF, -HALF to 0.0))
        val PENTAGON = regular(5)
        val HEXAGON = regular(6)
        val STAR = Polygon((0 until 10).map { i ->
            val radius = if (i % 2 == 0) HALF else HALF * 0.45
            val angle = -PI / 2 + i * PI / 5
            radius * cos(angle) to radius * sin(angle)
        })

        /** A regular [sides]-gon with a corner at the top. */
        private fun regular(sides: Int) = Polygon((0 until sides).map { i ->
            val angle = -PI / 2 + i * 2 * PI / sides
            HALF * cos(angle) to HALF * sin(angle)
        })
    }
}

private fun distanceToSegment(px: Double, py: Double, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val (ax, ay) = a
    val (bx, by) = b
    val dx = bx - ax
    val dy = by - ay
    val t = (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

/** A handle's label and how to show its value (turn or amount, 0..1) as text. */
class EffectControl(val label: String, val describe: (Double) -> String)

/**
 * The hardcoded effects: a coloured shape each, and how it sounds. [createNode] makes its
 * Tone.js effect, and [applyParams] sets it from the shape (see EffectParams in timeline.kt):
 * - amount, 0..1: the excess as a fraction of the inner radius. More room = stronger.
 * - turn, 0..1: the rotation as a fraction of a full turn.
 *
 * [rotationControl] and [scaleControl] name what turning and resizing do, and show the
 * current value, next to the handles. An effect that doesn't need one sets it to null:
 * it gets no handle for it, and [applyParams] ignores that input. Reverb is a circle, so
 * turning it wouldn't look like anything; it has no rotate handle and a fixed mix.
 *
 *   Reverb      amount → decay (0.5 to 8.5 s)          (no rotation: wet fixed at 50%)
 *   Delay       amount → feedback (0 to 85%)           turn → delay time (1/8 to 1 beat)
 *   Distortion  amount → distortion (0 to 1)           turn → wet (30% to 100%)
 *   Filter      amount → lowers the cutoff (12.8 kHz down to 100 Hz)   turn → resonance
 *   Bitcrush    amount → fewer bits (8 down to 2)      turn → wet (30% to 100%)
 *   Chorus      amount → depth (0 to 1)                turn → wobble speed (0.5 to 8 Hz)
 *   Stutter     amount → depth (0 to 1)                turn → speed (1/4 to 1/32 notes)
 */
enum class EffectType(
    val label: String,
    val color: String,
    val shape: EffectShape,
    /** What turning it does, or null if it has no rotate handle. Takes the turn, 0..1. */
    val rotationControl: EffectControl?,
    /** What making it roomier does, or null if it has no scale handle. Takes the amount, 0..1. */
    val scaleControl: EffectControl?,
) {
    REVERB(
        "Reverb", SKY_500, EffectShape.Circle,
        rotationControl = null,
        scaleControl = EffectControl("Room size") { "${reverbDecay(it).fixed(1)}s" },
    ) {
        override fun createNode() = EffectNode(Reverb(js("({ decay: 1.5, wet: 0.5 })")))  // wet stays 50%
        override fun applyParams(node: EffectNode, amount: Double, turn: Double) {
            // Changing the decay re-renders the whole impulse response, so only on a real change.
            val decay = reverbDecay(amount)
            if (node.control.asDynamic().decay != decay) node.control.asDynamic().decay = decay
        }
    },
    DELAY(
        "Delay", EMERALD_500, EffectShape.SQUARE,
        rotationControl = EffectControl("Echo gap") { "${delayBeats(it).fixed(2)} beats" },
        scaleControl = EffectControl("Repeats") { percent(delayFeedback(it)) },
    ) {
        override fun createNode() = EffectNode(FeedbackDelay(js("({ delayTime: 0.25, feedback: 0.4 })")))
        override fun applyParams(node: EffectNode, amount: Double, turn: Double) {
            val fx = node.control.asDynamic()
            glide(fx.feedback, delayFeedback(amount))
            glide(fx.delayTime, beatSeconds() * delayBeats(turn))
        }
    },
    DISTORTION(
        "Distortion", ROSE_500, EffectShape.TRIANGLE,
        rotationControl = EffectControl("Mix") { percent(mixOf(it)) },
        scaleControl = EffectControl("Crunch") { percent(it) },
    ) {
        override fun createNode() = EffectNode(Distortion(js("({ distortion: 0.4 })")))
        override fun applyParams(node: EffectNode, amount: Double, turn: Double) {
            val fx = node.control.asDynamic()
            fx.distortion = amount
            glide(fx.wet, mixOf(turn))
        }
    },
    FILTER(
        "Filter", AMBER_500, EffectShape.DIAMOND,
        rotationControl = EffectControl("Ring") { percent(it) },
        scaleControl = EffectControl("Muffle") { percent(it) },
    ) {
        override fun createNode() = EffectNode(Filter(js("({ frequency: 1000, type: 'lowpass' })")))
        override fun applyParams(node: EffectNode, amount: Double, turn: Double) {
            val fx = node.control.asDynamic()
            glide(fx.frequency, filterCutoff(amount))
            glide(fx.Q, filterResonance(turn))
        }
    },
    BITCRUSH(
        "Bitcrush", LIME_500, EffectShape.HEXAGON,
        rotationControl = EffectControl("Mix") { percent(mixOf(it)) },
        scaleControl = EffectControl("Crush") { "${crushBits(it)}-bit" },
    ) {
        override fun createNode() = NativeBitCrusher().node
        override fun applyParams(node: EffectNode, amount: Double, turn: Double) {
            val fx = node.control as NativeBitCrusher
            fx.bits = crushBits(amount)  // whole steps: nothing to glide between
            glide(fx.mix.fade, mixOf(turn))
        }
    },
    CHORUS(
        "Chorus", FUCHSIA_500, EffectShape.PENTAGON,
        rotationControl = EffectControl("Wobble speed") { "${chorusRate(it).fixed(1)} Hz" },
        scaleControl = EffectControl("Width") { percent(it) },
    ) {
        override fun createNode() = EffectNode(Chorus(js("({ frequency: 1.5, delayTime: 3.5, depth: 0.7, wet: 0.6 })")).also {
            it.asDynamic().start()  // its wobble runs on an LFO that has to be started
        })
        override fun applyParams(node: EffectNode, amount: Double, turn: Double) {
            val fx = node.control.asDynamic()
            fx.depth = amount
            glide(fx.frequency, chorusRate(turn))
        }
    },
    STUTTER(
        "Stutter", PINK_500, EffectShape.STAR,
        rotationControl = EffectControl("Speed") { "1/${stutterDivision(it)}" },
        scaleControl = EffectControl("Depth") { percent(it) },
    ) {
        // Chops the sound on and off in time with the beat.
        override fun createNode() = EffectNode(Tremolo(js("({ frequency: 4, type: 'square', depth: 1, spread: 0 })")).also {
            it.asDynamic().start()
        })
        override fun applyParams(node: EffectNode, amount: Double, turn: Double) {
            val fx = node.control.asDynamic()
            glide(fx.depth, amount)
            // A 1/4 note is one beat, so 1/n notes come n/4 times a beat.
            glide(fx.frequency, stutterDivision(turn) / 4.0 / beatSeconds())
        }
    };

    val innerRadius get() = shape.innerRadius
    val area get() = shape.area
    fun tracePath(ctx: CanvasRenderingContext2D) = shape.trace(ctx)
    fun containsLocal(x: Double, y: Double) = shape.contains(x, y)

    abstract fun createNode(): EffectNode
    abstract fun applyParams(node: EffectNode, amount: Double, turn: Double)
}

/**
 * One effect's nodes. Sound goes into [input] and comes out of [output]; [control] is what
 * applyParams adjusts. For most effects all three are the same Tone.js effect.
 */
class EffectNode(
    val input: ToneAudioNode,
    val output: ToneAudioNode = input,
    private val parts: List<ToneAudioNode> = listOf(input),
    val control: Any = input,
) {
    fun dispose() = parts.forEach { it.dispose() }
}

/** How many points the bitcrusher's staircase is worked out at: enough that every step is sharp. */
private const val CRUSH_CURVE_LENGTH = 8192

/** Rounds every sample to one of 2^[bits] levels, as Tone.js's BitCrusher does. */
private fun staircase(bits: Int): (Double) -> Double {
    val step = 0.5.pow(bits - 1)
    return { x -> step * floor(x / step + 0.5) }
}

/**
 * A bitcrusher made of a built-in WaveShaper. Tone.js's BitCrusher runs JavaScript on the
 * audio thread (an AudioWorklet), which costs far more and can stall it.
 */
fun crushShaper(bits: Int) = WaveShaper(staircase(bits), CRUSH_CURVE_LENGTH)

/** [crushShaper] with a wet / dry mix, for the Bitcrush effect. */
private class NativeBitCrusher {
    private val input = Gain()
    private val shaper = crushShaper(8)
    val mix = CrossFade(1.0)
    val node = EffectNode(input, mix, listOf(input, shaper, mix), this)

    var bits = 8
        set(value) {
            if (value == field) return
            field = value
            shaper.setMap(staircase(value), CRUSH_CURVE_LENGTH)
        }

    init {
        input.connect(mix.a)
        input.connect(shaper)
        shaper.connect(mix.b)
    }
}

/** How long a parameter takes to move to a new value, in seconds. */
private const val GLIDE_SECONDS = 0.05

/**
 * Moves Tone.js [param] to [value] over [GLIDE_SECONDS]. Jumping straight there makes a
 * click, and dragging a shape changes its parameters every tick.
 */
private fun glide(param: dynamic, value: Double) {
    param.rampTo(value, GLIDE_SECONDS)
}

// How the 0..1 inputs map to sound. Shared by applyParams and the handle labels, so the
// numbers shown always match what's heard.
/** In half-second steps: each change re-renders the reverb, which is too slow to do every tick. */
private fun reverbDecay(amount: Double) = 0.5 + (amount * 16).roundToInt() / 2.0
private fun delayBeats(turn: Double) = 0.125 + turn * 0.875
private fun delayFeedback(amount: Double) = amount * 0.85
private fun mixOf(turn: Double) = 0.3 + turn * 0.7
private fun filterCutoff(amount: Double) = 12800.0 / 2.0.pow(amount * 7)
private fun filterResonance(turn: Double) = 0.5 + turn * 12
private fun crushBits(amount: Double) = (8 - amount * 6).roundToInt()
private fun chorusRate(turn: Double) = 0.5 + turn * 7.5
private val STUTTER_DIVISIONS = listOf(4, 8, 16, 32)
private fun stutterDivision(turn: Double) =
    STUTTER_DIVISIONS[min((turn * STUTTER_DIVISIONS.size).toInt(), STUTTER_DIVISIONS.size - 1)]

private fun percent(fraction: Double) = "${(fraction * 100).roundToInt()}%"
private fun Double.fixed(decimals: Int): String = asDynamic().toFixed(decimals) as String

/** Draws every effect in [universe], positioned relative to its camera. */
fun renderEffects(ctx: CanvasRenderingContext2D, universe: Universe) {
    for (effect in universe.effects) {
        ctx.save()
        ctx.translate(effect.x - universe.cameraX, effect.y - universe.cameraY)
        ctx.rotate(effect.rotation)
        ctx.scale(effect.scale, effect.scale)

        ctx.beginPath()
        effect.type.tracePath(ctx)
        ctx.globalAlpha = 0.2
        ctx.fillStyle = effect.type.color
        ctx.fill()
        ctx.globalAlpha = 1.0
        ctx.strokeStyle = effect.type.color
        ctx.lineWidth = 2.0 / effect.scale // keep the outline 2px whatever the scale
        ctx.stroke()

        ctx.restore()
    }
}

/** Draws [type]'s shape filled, [size] across, centred on (cx, cy). Used for toolbar icons. */
fun renderEffectIcon(ctx: CanvasRenderingContext2D, type: EffectType, cx: Double, cy: Double, size: Double) {
    ctx.save()
    ctx.translate(cx, cy)
    ctx.scale(size / EFFECT_SIZE, size / EFFECT_SIZE)
    ctx.beginPath()
    type.tracePath(ctx)
    ctx.fillStyle = type.color
    ctx.fill()
    ctx.restore()
}
