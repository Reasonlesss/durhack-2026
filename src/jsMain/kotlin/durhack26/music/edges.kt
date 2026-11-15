package durhack26.music

import org.w3c.dom.CanvasRenderingContext2D
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val EDGE_COLOR = ZINC_400
private const val EDGE_WIDTH = 2.0
private const val ARROW_LENGTH = 8.0
private const val ARROW_WIDTH = 8.0
private const val ARROW_GAP = 3.0  // between an arrow and the instruments at its ends
private const val SEGMENT_GAP = 10.0 // between the segments of a split edge

/**
 * Draws [universe]'s edges as arrows, plus the one being dragged out from an instrument,
 * relative to the universe's camera.
 */
fun renderEdges(ctx: CanvasRenderingContext2D, universe: Universe) {
    val camX = universe.cameraX
    val camY = universe.cameraY
    for (edge in universe.edges) {
        renderArrow(
            ctx,
            edge.from.x - camX, edge.from.y - camY,
            edge.to.x - camX, edge.to.y - camY,
            INSTRUMENT_SIZE / 2 + ARROW_GAP,
            edge.segments,
        )
    }
    val linkingFrom = EditorState.linkingFrom
    if (linkingFrom != null) {
        renderArrow(ctx, linkingFrom.x - camX, linkingFrom.y - camY, EditorState.pointerX, EditorState.pointerY, 0.0, 1)
    }
}

/**
 * A line from (x1, y1) to (x2, y2), split into [segments] equal pieces, with an arrowhead.
 * It starts just outside the instrument at (x1, y1) and stops [inset] short of (x2, y2).
 */
private fun renderArrow(
    ctx: CanvasRenderingContext2D,
    x1: Double, y1: Double,
    x2: Double, y2: Double,
    inset: Double,
    segments: Int,
) {
    val startInset = INSTRUMENT_SIZE / 2 + ARROW_GAP
    val length = hypot(x2 - x1, y2 - y1)
    val lineLength = length - startInset - inset - ARROW_LENGTH
    if (lineLength <= 0.0) return  // too short to draw
    val angle = atan2(y2 - y1, x2 - x1)
    val dirX = cos(angle)
    val dirY = sin(angle)
    val tipX = x2 - dirX * inset
    val tipY = y2 - dirY * inset
    val baseX = tipX - dirX * ARROW_LENGTH
    val baseY = tipY - dirY * ARROW_LENGTH

    // On short lines the gaps shrink so the pieces never get smaller than the gaps.
    val gap = minOf(SEGMENT_GAP, lineLength / (segments * 2 - 1))
    val segmentLength = (lineLength - gap * (segments - 1)) / segments
    ctx.beginPath()
    for (i in 0 until segments) {
        val start = startInset + i * (segmentLength + gap)
        ctx.moveTo(x1 + dirX * start, y1 + dirY * start)
        ctx.lineTo(x1 + dirX * (start + segmentLength), y1 + dirY * (start + segmentLength))
    }
    ctx.strokeStyle = EDGE_COLOR
    ctx.lineWidth = EDGE_WIDTH
    ctx.stroke()

    ctx.beginPath()
    ctx.moveTo(tipX, tipY)
    ctx.lineTo(baseX - dirY * ARROW_WIDTH / 2, baseY + dirX * ARROW_WIDTH / 2)
    ctx.lineTo(baseX + dirY * ARROW_WIDTH / 2, baseY - dirX * ARROW_WIDTH / 2)
    ctx.closePath()
    ctx.fillStyle = EDGE_COLOR
    ctx.fill()
}
