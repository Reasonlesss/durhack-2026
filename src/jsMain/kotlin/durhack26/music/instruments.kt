package durhack26.music

import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.MIDDLE
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Size of every instrument shape at scale 1.0, in CSS pixels. */
const val INSTRUMENT_SIZE = 20.0

/** Instruments are solid white, so they stand apart from the coloured effects. */
private const val INSTRUMENT_COLOR = WHITE
private const val HALF = INSTRUMENT_SIZE / 2

/**
 * The eight shapes an instrument type can take, all drawn solid white. Each type in a
 * universe gets its own shape, which also caps it at [entries].size types.
 *
 * Shapes are described in their own local space, centred on 0,0 and [INSTRUMENT_SIZE]
 * across, before any scaling.
 */
enum class InstrumentShape {
    CIRCLE {
        override fun tracePath(ctx: CanvasRenderingContext2D) {
            ctx.arc(0.0, 0.0, HALF, 0.0, 2 * PI)
        }
    },
    SQUARE {
        override fun tracePath(ctx: CanvasRenderingContext2D) {
            ctx.rect(-HALF, -HALF, INSTRUMENT_SIZE, INSTRUMENT_SIZE)
        }
    },
    TRIANGLE {
        override fun tracePath(ctx: CanvasRenderingContext2D) = polygon(ctx, 3, HALF, -PI / 2)
    },
    DIAMOND {
        override fun tracePath(ctx: CanvasRenderingContext2D) = polygon(ctx, 4, HALF, -PI / 2)
    },
    PENTAGON {
        override fun tracePath(ctx: CanvasRenderingContext2D) = polygon(ctx, 5, HALF, -PI / 2)
    },
    HEXAGON {
        override fun tracePath(ctx: CanvasRenderingContext2D) = polygon(ctx, 6, HALF, 0.0)
    },
    STAR {
        override fun tracePath(ctx: CanvasRenderingContext2D) = star(ctx, HALF, HALF * 0.45, 5, -PI / 2)
    },
    CROSS {
        override fun tracePath(ctx: CanvasRenderingContext2D) = cross(ctx)
    };

    abstract fun tracePath(ctx: CanvasRenderingContext2D)
}

/** Traces a regular [sides]-gon of [radius], its first point at [startAngle]. */
private fun polygon(ctx: CanvasRenderingContext2D, sides: Int, radius: Double, startAngle: Double) {
    for (i in 0 until sides) {
        val angle = startAngle + i * 2 * PI / sides
        val x = radius * cos(angle)
        val y = radius * sin(angle)
        if (i == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
    }
    ctx.closePath()
}

/** Traces an [points]-pointed star alternating between [outer] and [inner] radii. */
private fun star(
    ctx: CanvasRenderingContext2D,
    outer: Double,
    inner: Double,
    points: Int,
    startAngle: Double,
) {
    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = startAngle + i * PI / points
        val x = radius * cos(angle)
        val y = radius * sin(angle)
        if (i == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
    }
    ctx.closePath()
}

/** Traces a plus sign: an [arm] half-width running the full [HALF] in both directions. */
private fun cross(ctx: CanvasRenderingContext2D) {
    val arm = HALF * 0.38
    ctx.moveTo(arm, -HALF)
    ctx.lineTo(arm, -arm)
    ctx.lineTo(HALF, -arm)
    ctx.lineTo(HALF, arm)
    ctx.lineTo(arm, arm)
    ctx.lineTo(arm, HALF)
    ctx.lineTo(-arm, HALF)
    ctx.lineTo(-arm, arm)
    ctx.lineTo(-HALF, arm)
    ctx.lineTo(-HALF, -arm)
    ctx.lineTo(-arm, -arm)
    ctx.lineTo(-arm, -HALF)
    ctx.closePath()
}

private const val OUTLINE_COLOR = ZINC_400
private const val OUTLINE_WIDTH = 1.5
private const val OUTLINE_DASH = 3.0

/**
 * Draws [universe]'s instruments, then a dotted outline around the ones the pointer is
 * near (and the one an edge is being dragged from), relative to the universe's camera.
 */
fun renderInstruments(ctx: CanvasRenderingContext2D, universe: Universe) {
    val camX = universe.cameraX
    val camY = universe.cameraY
    val pointerX = EditorState.pointerX + camX
    val pointerY = EditorState.pointerY + camY

    val linkingFrom = EditorState.linkingFrom
    val now = window.performance.now()
    for (instrument in universe.instruments) {
        // 0 just as it plays, rising to 1 when the pulse is over.
        val pulse = ((now - instrument.lastPlayedAt) / PULSE_MS).coerceIn(0.0, 1.0)

        ctx.save()
        ctx.translate(instrument.x - universe.cameraX, instrument.y - universe.cameraY)
        // Quieter notes are fainter, but never invisible.
        val opacity = MIN_OPACITY + (1.0 - MIN_OPACITY) * instrument.volume

        if (pulse < 1.0) {
            // A ring of the shape that grows outward and fades.
            val ringScale = 1.0 + pulse * PULSE_RING_GROWTH
            ctx.save()
            ctx.scale(ringScale, ringScale)
            ctx.beginPath()
            instrument.shape.tracePath(ctx)
            ctx.globalAlpha = (1.0 - pulse) * opacity
            ctx.strokeStyle = INSTRUMENT_COLOR
            ctx.lineWidth = PULSE_RING_WIDTH / ringScale
            ctx.stroke()
            ctx.restore()
        }

        // The shape itself pops up and eases back down to its normal size.
        val pop = 1.0 + PULSE_POP * (1.0 - pulse) * (1.0 - pulse)
        ctx.scale(pop, pop)
        ctx.beginPath()
        instrument.shape.tracePath(ctx)
        ctx.globalAlpha = opacity
        ctx.fillStyle = INSTRUMENT_COLOR
        ctx.fill()

        ctx.restore()
    }

    val hovered = universe.instrumentNear(pointerX, pointerY)
    if (EditorState.dragging == null) {
        for (instrument in setOfNotNull(hovered, linkingFrom)) {
            renderOutline(ctx, instrument, camX, camY)
        }
    }

    // Name what the hovered or dragged instrument will play, e.g. "E4" or "Snare".
    val labelled = EditorState.dragging as? Instrument ?: hovered
    if (labelled != null) renderSoundLabel(ctx, labelled, camX, camY)

    // Dragging a new note out of an existing one: name what it would play, at the pointer.
    val placing = EditorState.placingNote()
    if (linkingFrom != null && placing != null) {
        val (type, level) = placing
        renderLabel(ctx, type.describe(level), EditorState.pointerX + LABEL_GAP * 2, EditorState.pointerY)
    }
}

/** How see-through a silent note is drawn; louder ones are more solid, up to fully at full volume. */
private const val MIN_OPACITY = 0.3

private const val PULSE_MS = 400.0         // how long a note's pulse lasts after it plays
private const val PULSE_POP = 0.35         // how much bigger the shape gets as it plays
private const val PULSE_RING_GROWTH = 1.5  // how far the ring grows, as a multiple of the shape
private const val PULSE_RING_WIDTH = 1.5

private const val LABEL_FONT = "11px system-ui, -apple-system, sans-serif"
private const val LABEL_COLOR = ZINC_300
private const val LABEL_GAP = 6.0  // between the outline and the label

/**
 * Draws what [instrument] plays just to its right, with its volume while it's held (when
 * scrolling changes it) or when it isn't at full.
 */
private fun renderSoundLabel(ctx: CanvasRenderingContext2D, instrument: Instrument, camX: Double, camY: Double) {
    val sound = instrument.type.describe(instrument.level)
    val held = EditorState.dragging === instrument
    val percent = (instrument.volume * 100).roundToInt()
    renderLabel(
        ctx,
        if (held || percent < 100) "$sound · $percent%" else sound,
        instrument.x - camX + INSTRUMENT_SIZE / 2 + LINK_MARGIN + LABEL_GAP,
        instrument.y - camY,
    )
}

private fun renderLabel(ctx: CanvasRenderingContext2D, text: String, x: Double, y: Double) {
    ctx.save()
    ctx.font = LABEL_FONT
    ctx.textBaseline = CanvasTextBaseline.MIDDLE
    ctx.fillStyle = LABEL_COLOR
    ctx.fillText(text, x, y)
    ctx.restore()
}

/** A dotted copy of [instrument]'s shape, [LINK_MARGIN] bigger all round. */
private fun renderOutline(ctx: CanvasRenderingContext2D, instrument: Instrument, camX: Double, camY: Double) {
    val scale = (INSTRUMENT_SIZE / 2 + LINK_MARGIN) / (INSTRUMENT_SIZE / 2)
    ctx.save()
    ctx.translate(instrument.x - camX, instrument.y - camY)
    ctx.scale(scale, scale)
    ctx.beginPath()
    instrument.shape.tracePath(ctx)
    // Line width and dashes are divided by the scale so they come out at their real size.
    ctx.lineWidth = OUTLINE_WIDTH / scale
    ctx.setLineDash(arrayOf(OUTLINE_DASH / scale, OUTLINE_DASH / scale))
    ctx.strokeStyle = OUTLINE_COLOR
    ctx.stroke()
    ctx.restore()
}

/** Draws [shape] filled, [size] across, centred on (cx, cy). Used for toolbar icons. */
fun renderInstrumentIcon(
    ctx: CanvasRenderingContext2D,
    shape: InstrumentShape,
    cx: Double,
    cy: Double,
    size: Double,
) {
    ctx.save()
    ctx.translate(cx, cy)
    ctx.scale(size / INSTRUMENT_SIZE, size / INSTRUMENT_SIZE)
    ctx.beginPath()
    shape.tracePath(ctx)
    ctx.fillStyle = INSTRUMENT_COLOR
    ctx.fill()
    ctx.restore()
}
