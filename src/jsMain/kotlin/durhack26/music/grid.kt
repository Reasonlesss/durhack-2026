package durhack26.music

import org.w3c.dom.CanvasRenderingContext2D
import kotlin.math.PI

/*
 * The editor background: a dot grid, plus guide lines for the note levels (see levels.kt)
 * while a note is being dragged. Dots are half a level apart, so every other row of dots is a level.
 */

private const val DOT_SPACING = LEVEL_HEIGHT / 2
private const val DOT_RADIUS = 1.0
private const val DOT_COLOR = ZINC_700

private const val GUIDE_COLOR = ZINC_600
private const val NEAREST_GUIDE_COLOR = ZINC_500
private const val GUIDE_REACH = 5  // levels shown either side of the dragged note
private const val GUIDE_OPACITY = 0.6  // of the nearest line; the rest fade from there

fun renderGrid(ctx: CanvasRenderingContext2D, universe: Universe) {
    renderDots(ctx, universe)
    renderLevelGuides(ctx, universe)
}

/**
 * A grid of faint dots that moves with the camera. Only the dots on screen are drawn:
 * the pattern repeats every DOT_SPACING, so it's shifted by the camera position modulo
 * the spacing.
 */
private fun renderDots(ctx: CanvasRenderingContext2D, universe: Universe) {
    val startX = -universe.cameraX.mod(DOT_SPACING)
    val startY = -universe.cameraY.mod(DOT_SPACING)

    ctx.fillStyle = DOT_COLOR
    ctx.beginPath()
    var y = startY
    while (y < CanvasState.height + DOT_SPACING) {
        var x = startX
        while (x < CanvasState.width + DOT_SPACING) {
            ctx.moveTo(x + DOT_RADIUS, y)
            ctx.arc(x, y, DOT_RADIUS, 0.0, 2 * PI)
            x += DOT_SPACING
        }
        y += DOT_SPACING
    }
    ctx.fill()
}

/**
 * While a note is being placed (dragged, or pulled out of another as a new one), lines across the screen for the levels near it, fading
 * out with distance. The one it'll play (the nearest) is brightest.
 */
private fun renderLevelGuides(ctx: CanvasRenderingContext2D, universe: Universe) {
    val nearest = EditorState.placingNote()?.second ?: return
    ctx.save()
    ctx.lineWidth = 1.0
    for (level in (nearest - GUIDE_REACH)..(nearest + GUIDE_REACH)) {
        if (!isLevel(level)) continue
        val distance = kotlin.math.abs(level - nearest)
        ctx.globalAlpha = GUIDE_OPACITY * (1.0 - distance.toDouble() / (GUIDE_REACH + 1))
        ctx.strokeStyle = if (level == nearest) NEAREST_GUIDE_COLOR else GUIDE_COLOR
        // +0.5 puts the 1px line on a pixel row instead of blurring across two.
        val y = levelY(level) - universe.cameraY + 0.5
        ctx.beginPath()
        ctx.moveTo(0.0, y)
        ctx.lineTo(CanvasState.width, y)
        ctx.stroke()
    }
    ctx.restore()
}
