package durhack26.music

import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.MIDDLE
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private const val HALF = EFFECT_SIZE / 2
private const val HANDLE_RADIUS = 5.0
private const val HANDLE_HIT_RADIUS = 9.0
private const val ROTATE_HANDLE_GAP = 20.0  // between the top of the effect and its rotate handle
private const val HANDLE_COLOR = ZINC_100
private const val HANDLE_LINE_COLOR = ZINC_400
private const val LABEL_FONT = "11px system-ui, -apple-system, sans-serif"
private const val LABEL_COLOR = ZINC_300
private const val LABEL_GAP = 6.0  // between a handle and its label
private const val MIN_SCALE = 0.25
private const val MAX_SCALE = 16.0

/** The selected effect's handles: one above it to rotate, one on its corner to scale. */
enum class EffectHandle { ROTATE, SCALE }

/** Where [handle] sits on this effect, in world coordinates. It turns with the effect. */
fun Effect.handlePosition(handle: EffectHandle): Pair<Double, Double> {
    val half = HALF * scale
    val (localX, localY) = when (handle) {
        EffectHandle.ROTATE -> 0.0 to -(half + ROTATE_HANDLE_GAP)
        EffectHandle.SCALE -> half to half
    }
    return x + localX * cos(rotation) - localY * sin(rotation) to
        y + localX * sin(rotation) + localY * cos(rotation)
}

/** What [handle] controls on this effect, or null if its type opted out of it. */
fun Effect.control(handle: EffectHandle): EffectControl? = when (handle) {
    EffectHandle.ROTATE -> type.rotationControl
    EffectHandle.SCALE -> type.scaleControl
}

/** The handles this effect has: only the ones its type uses. */
fun Effect.handles(): List<EffectHandle> = EffectHandle.entries.filter { control(it) != null }

/** The handle of [this] effect at world point (px, py), or null. */
fun Effect.handleAt(px: Double, py: Double): EffectHandle? = handles().firstOrNull {
    val (hx, hy) = handlePosition(it)
    hypot(px - hx, py - hy) <= HANDLE_HIT_RADIUS
}

/** Turns or resizes [this] effect so [handle] follows world point (px, py). */
fun Effect.dragHandle(handle: EffectHandle, px: Double, py: Double) {
    when (handle) {
        // The rotate handle sits straight up (-90°) when unrotated.
        EffectHandle.ROTATE -> rotation = atan2(py - y, px - x) + PI / 2
        // The scale handle is a corner, HALF * sqrt(2) from the centre at scale 1.
        EffectHandle.SCALE -> scale = (hypot(px - x, py - y) / (HALF * sqrt(2.0)))
            .coerceIn(MIN_SCALE, MAX_SCALE)
    }
}

/** Draws the selected effect's outline box and handles, if it's in [universe]. */
fun renderEffectHandles(ctx: CanvasRenderingContext2D, universe: Universe) {
    val effect = EditorState.selectedEffect ?: return
    val camX = universe.cameraX
    val camY = universe.cameraY
    val half = HALF * effect.scale

    ctx.save()
    ctx.translate(effect.x - camX, effect.y - camY)
    ctx.rotate(effect.rotation)
    ctx.beginPath()
    ctx.rect(-half, -half, half * 2, half * 2)
    if (EffectHandle.ROTATE in effect.handles()) {
        ctx.moveTo(0.0, -half)
        ctx.lineTo(0.0, -half - ROTATE_HANDLE_GAP)
    }
    ctx.strokeStyle = HANDLE_LINE_COLOR
    ctx.lineWidth = 1.0
    ctx.stroke()
    ctx.restore()

    val params = universe.timeline.paramsOf(effect)
    for (handle in effect.handles()) {
        val control = effect.control(handle) ?: continue
        val (hx, hy) = effect.handlePosition(handle)
        ctx.beginPath()
        ctx.arc(hx - camX, hy - camY, HANDLE_RADIUS, 0.0, 2 * PI)
        ctx.fillStyle = HANDLE_COLOR
        ctx.fill()
        ctx.strokeStyle = HANDLE_LINE_COLOR
        ctx.stroke()

        // What this handle changes and its current value, e.g. "Room size 4.2s".
        val value = params?.let { if (handle == EffectHandle.ROTATE) it.turn else it.amount }
        val label = if (value == null) control.label else "${control.label} ${control.describe(value)}"
        ctx.font = LABEL_FONT
        ctx.textBaseline = CanvasTextBaseline.MIDDLE
        ctx.fillStyle = LABEL_COLOR
        ctx.fillText(label, hx - camX + HANDLE_RADIUS + LABEL_GAP, hy - camY)
    }
}
