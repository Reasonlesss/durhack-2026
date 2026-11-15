package durhack26.music

import org.w3c.dom.CanvasRenderingContext2D
import kotlin.math.max
import kotlin.math.min

/*
 * Rounded-rectangle drawing, written as extensions on the canvas context so they read
 * like built-in canvas calls:
 *
 *     ctx.fillRoundedRect(x, y, w, h, radius = 6.0, colour = ZINC_700)
 *     ctx.fillRoundedRect(x, y, w, h, Corners.top(8.0), ZINC_700)
 *
 * All sizes are in CSS pixels. The fill/stroke helpers set the colour themselves,
 * so they leave fillStyle / strokeStyle / lineWidth changed afterwards.
 */

/** A screen-space rectangle, in CSS pixels. */
class Rect(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun contains(px: Double, py: Double) = px >= x && px < x + width && py >= y && py < y + height
}

/** Corner radii for a rounded rectangle, clockwise from the top-left. */
data class Corners(
    val topLeft: Double,
    val topRight: Double,
    val bottomRight: Double,
    val bottomLeft: Double,
) {
    constructor(all: Double) : this(all, all, all, all)

    companion object {
        /** Only the top corners rounded: handy for tabs. */
        fun top(radius: Double) = Corners(radius, radius, 0.0, 0.0)

        /** Only the bottom corners rounded. */
        fun bottom(radius: Double) = Corners(0.0, 0.0, radius, radius)
    }
}

/**
 * Starts a new path tracing a rounded rectangle. Fill or stroke it afterwards.
 * Radii larger than the shape allows are shrunk to fit.
 */
fun CanvasRenderingContext2D.roundedRectPath(
    x: Double, y: Double, width: Double, height: Double, corners: Corners,
) {
    val limit = max(0.0, min(width, height) / 2)
    val tl = corners.topLeft.coerceIn(0.0, limit)
    val tr = corners.topRight.coerceIn(0.0, limit)
    val br = corners.bottomRight.coerceIn(0.0, limit)
    val bl = corners.bottomLeft.coerceIn(0.0, limit)

    beginPath()
    moveTo(x + tl, y)
    lineTo(x + width - tr, y)
    arcTo(x + width, y, x + width, y + tr, tr)
    lineTo(x + width, y + height - br)
    arcTo(x + width, y + height, x + width - br, y + height, br)
    lineTo(x + bl, y + height)
    arcTo(x, y + height, x, y + height - bl, bl)
    lineTo(x, y + tl)
    arcTo(x, y, x + tl, y, tl)
    closePath()
}

fun CanvasRenderingContext2D.fillRoundedRect(
    x: Double, y: Double, width: Double, height: Double, radius: Double, colour: String,
) = fillRoundedRect(x, y, width, height, Corners(radius), colour)

fun CanvasRenderingContext2D.fillRoundedRect(
    x: Double, y: Double, width: Double, height: Double, corners: Corners, colour: String,
) {
    roundedRectPath(x, y, width, height, corners)
    fillStyle = colour
    fill()
}

fun CanvasRenderingContext2D.strokeRoundedRect(
    x: Double, y: Double, width: Double, height: Double, radius: Double,
    colour: String, lineWidth: Double = 1.0,
) = strokeRoundedRect(x, y, width, height, Corners(radius), colour, lineWidth)

/** The line is drawn inside the bounds, so the outline never spills past (x, y, width, height). */
fun CanvasRenderingContext2D.strokeRoundedRect(
    x: Double, y: Double, width: Double, height: Double, corners: Corners,
    colour: String, lineWidth: Double = 1.0,
) {
    val inset = lineWidth / 2
    roundedRectPath(
        x + inset, y + inset, width - lineWidth, height - lineWidth,
        Corners(
            corners.topLeft - inset, corners.topRight - inset,
            corners.bottomRight - inset, corners.bottomLeft - inset,
        ),
    )
    strokeStyle = colour
    this.lineWidth = lineWidth
    stroke()
}
