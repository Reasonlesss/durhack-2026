package durhack26.music

import org.w3c.dom.CanvasRenderingContext2D

/*
 * The play / stop button, floating in the top-right corner. Styled like the tab box.
 */

private const val BOX_COLOR = ZINC_900
private const val HOVERED_COLOR = ZINC_700
private const val ICON_COLOR = ZINC_100

private const val MARGIN = 20.0   // from the top and right of the window
private const val SIZE = 40.0     // same height as the tab box
private const val BOX_RADIUS = 10.0
private const val PADDING = 4.0
private const val ICON_SIZE = 12.0

private fun buttonBounds() = Rect(CanvasState.width - MARGIN - SIZE, MARGIN, SIZE, SIZE)

fun isOverPlayButton(x: Double, y: Double) = buttonBounds().contains(x, y)

fun renderPlayButton(ctx: CanvasRenderingContext2D) {
    val box = buttonBounds()
    ctx.fillRoundedRect(box.x, box.y, box.width, box.height, BOX_RADIUS, BOX_COLOR)
    if (box.contains(EditorState.pointerX, EditorState.pointerY)) {
        ctx.fillRoundedRect(
            box.x + PADDING, box.y + PADDING, box.width - PADDING * 2, box.height - PADDING * 2,
            BOX_RADIUS - PADDING, HOVERED_COLOR,
        )
    }

    val cx = box.x + box.width / 2
    val cy = box.y + box.height / 2
    val half = ICON_SIZE / 2
    ctx.fillStyle = ICON_COLOR
    ctx.beginPath()
    if (EditorState.playing) {
        ctx.rect(cx - half, cy - half, ICON_SIZE, ICON_SIZE)  // stop
    } else {
        // Play: a triangle pointing right, nudged right so it looks centred.
        ctx.moveTo(cx - half + 1, cy - half)
        ctx.lineTo(cx + half + 1, cy)
        ctx.lineTo(cx - half + 1, cy + half)
        ctx.closePath()
    }
    ctx.fill()
}

/** Plays or stops if the button was clicked. Returns true if it was hit. */
fun processPlayButtonClick(x: Double, y: Double): Boolean {
    if (!isOverPlayButton(x, y)) return false
    togglePlayback()
    return true
}
