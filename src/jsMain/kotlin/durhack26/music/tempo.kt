package durhack26.music

import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextAlign
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.CENTER
import org.w3c.dom.LEFT
import org.w3c.dom.MIDDLE
import org.w3c.dom.ROUND
import org.w3c.dom.CanvasLineCap

/*
 * The tempo control: a box just left of the scale picker, styled like it.
 *
 *   [ −  124 BPM  +  Tap ]
 *
 * − and + nudge the tempo by 1 BPM, and the mouse wheel over the box does the same. Tap
 * (or the T key) sets it from the gaps between taps: tap along to a beat, and it follows.
 */

private const val BOX_COLOR = ZINC_900
private const val HOVERED_COLOR = ZINC_700
private const val TAP_FLASH_COLOR = VIOLET_500
private const val FONT = "13px system-ui, -apple-system, sans-serif"
private const val TEXT_COLOR = ZINC_400
private const val HOVERED_TEXT_COLOR = ZINC_100
private const val NUMBER_COLOR = ZINC_100
private const val UNIT_COLOR = ZINC_500

private const val GAP = 8.0             // between this and the scale picker
private const val TOP = 20.0            // same as the scale picker and play button
private const val HEIGHT = 40.0
private const val BOX_RADIUS = 10.0
private const val PADDING = 4.0
private const val ITEM_RADIUS = BOX_RADIUS - PADDING
private const val ITEM_HEIGHT = HEIGHT - PADDING * 2
private const val ITEM_GAP = 4.0
private const val STEP_WIDTH = 32.0     // the − and + buttons
private const val ICON_SIZE = 10.0      // the − and + strokes
private const val NUMBER_PADDING = 6.0  // either side of "124 BPM"
private const val TAP_PADDING = 12.0    // either side of "Tap"
private const val UNIT = " BPM"

/** How long the Tap button lights up after a tap, in ms. */
private const val TAP_FLASH_MS = 150.0

/** A pause this long between taps, in ms, starts a new count rather than averaging over it. */
private const val TAP_RESET_MS = 2000.0

/** How many recent taps are averaged. More is steadier, fewer follows changes faster. */
private const val MAX_TAPS = 8

/** How far the wheel has to scroll, in pixels, to move the tempo by 1 BPM. */
private const val WHEEL_STEP = 40.0

private class TempoLayout(val box: Rect, val minus: Rect, val number: Rect, val plus: Rect, val tap: Rect)

private fun layout(ctx: CanvasRenderingContext2D): TempoLayout {
    ctx.font = FONT
    // Sized for the widest tempo, so the box doesn't change size as the number does.
    val numberWidth = ctx.measureText("$MAX_BPM$UNIT").width + NUMBER_PADDING * 2
    val tapWidth = ctx.measureText("Tap").width + TAP_PADDING * 2
    val width = PADDING * 2 + STEP_WIDTH * 2 + numberWidth + tapWidth + ITEM_GAP * 3
    val right = scalePickerLeft(ctx) - GAP
    val box = Rect(right - width, TOP, width, HEIGHT)

    var x = box.x + PADDING
    val y = box.y + PADDING
    fun next(w: Double) = Rect(x, y, w, ITEM_HEIGHT).also { x += w + ITEM_GAP }
    return TempoLayout(box, next(STEP_WIDTH), next(numberWidth), next(STEP_WIDTH), next(tapWidth))
}

fun renderTempo(ctx: CanvasRenderingContext2D) {
    val l = layout(ctx)
    val px = EditorState.pointerX
    val py = EditorState.pointerY
    ctx.fillRoundedRect(l.box.x, l.box.y, l.box.width, l.box.height, BOX_RADIUS, BOX_COLOR)

    for (button in listOf(l.minus, l.plus)) {
        val hovered = button.contains(px, py)
        if (hovered) ctx.fillRoundedRect(button.x, button.y, button.width, button.height, ITEM_RADIUS, HOVERED_COLOR)
        renderStepIcon(ctx, button, plus = button === l.plus, if (hovered) HOVERED_TEXT_COLOR else TEXT_COLOR)
    }

    val flashing = window.performance.now() - lastTapAt < TAP_FLASH_MS
    val tapHovered = l.tap.contains(px, py)
    if (flashing || tapHovered) {
        ctx.fillRoundedRect(
            l.tap.x, l.tap.y, l.tap.width, l.tap.height, ITEM_RADIUS,
            if (flashing) TAP_FLASH_COLOR else HOVERED_COLOR,
        )
    }

    ctx.font = FONT
    ctx.textBaseline = CanvasTextBaseline.MIDDLE
    ctx.textAlign = CanvasTextAlign.CENTER
    val midY = l.box.y + l.box.height / 2
    ctx.fillStyle = if (flashing || tapHovered) HOVERED_TEXT_COLOR else TEXT_COLOR
    ctx.fillText("Tap", l.tap.x + l.tap.width / 2, midY)

    // "124" bright and " BPM" dim, centred together.
    val number = EditorState.bpm.toString()
    val numberWidth = ctx.measureText(number).width
    val start = l.number.x + (l.number.width - ctx.measureText(number + UNIT).width) / 2
    ctx.textAlign = CanvasTextAlign.LEFT
    ctx.fillStyle = NUMBER_COLOR
    ctx.fillText(number, start, midY)
    ctx.fillStyle = UNIT_COLOR
    ctx.fillText(UNIT, start + numberWidth, midY)
}

/** A − (or + if [plus]) centred in [r]. */
private fun renderStepIcon(ctx: CanvasRenderingContext2D, r: Rect, plus: Boolean, color: String) {
    val cx = r.x + r.width / 2
    val cy = r.y + r.height / 2
    val half = ICON_SIZE / 2
    ctx.strokeStyle = color
    ctx.lineWidth = 2.0
    ctx.lineCap = CanvasLineCap.ROUND
    ctx.beginPath()
    ctx.moveTo(cx - half, cy)
    ctx.lineTo(cx + half, cy)
    if (plus) {
        ctx.moveTo(cx, cy - half)
        ctx.lineTo(cx, cy + half)
    }
    ctx.stroke()
}

fun isOverTempo(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    return layout(ctx).box.contains(x, y)
}

/** Handles a click on the tempo control. Returns true if it was hit, even between buttons. */
fun processTempoClick(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    val l = layout(ctx)
    when {
        l.minus.contains(x, y) -> setBpm(EditorState.bpm - 1.0)
        l.plus.contains(x, y) -> setBpm(EditorState.bpm + 1.0)
        l.tap.contains(x, y) -> tapTempo()
        else -> return l.box.contains(x, y)
    }
    return true
}

/** Wheel distance not yet turned into a whole BPM step. */
private var wheelRemainder = 0.0

/**
 * Scrolling over the tempo control changes it: up is faster. Returns true if the pointer was
 * over it (so the editor doesn't scroll as well).
 */
fun processTempoWheel(x: Double, y: Double, dy: Double): Boolean {
    if (!isOverTempo(x, y)) {
        wheelRemainder = 0.0
        return false
    }
    wheelRemainder -= dy
    val steps = (wheelRemainder / WHEEL_STEP).toInt()  // toward zero, so the rest carries over
    if (steps != 0) {
        wheelRemainder -= steps * WHEEL_STEP
        setBpm(EditorState.bpm + steps.toDouble())
    }
    return true
}

/** When each recent tap happened, on the `performance.now()` clock in ms, oldest first. */
private val taps = mutableListOf<Double>()

/** When the last tap happened, for the button's flash. */
private var lastTapAt = Double.NEGATIVE_INFINITY

/**
 * Registers a tap. From the second tap on, the tempo becomes the average gap between the
 * last [MAX_TAPS] taps. A pause of [TAP_RESET_MS] starts counting again.
 */
fun tapTempo() {
    val now = window.performance.now()
    if (taps.isNotEmpty() && now - taps.last() > TAP_RESET_MS) taps.clear()
    taps += now
    if (taps.size > MAX_TAPS) taps.removeAt(0)
    lastTapAt = now
    if (taps.size < 2) return
    val averageGapMs = (taps.last() - taps.first()) / (taps.size - 1)
    if (averageGapMs > 0) setBpm(60_000.0 / averageGapMs)
}
