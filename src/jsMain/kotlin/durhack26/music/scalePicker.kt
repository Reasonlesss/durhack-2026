package durhack26.music

import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.MIDDLE

/*
 * The scale picker: a button just left of the play button showing the current scale.
 * Clicking it opens a dropdown of every scale below it. Styled like the tab box.
 */

private const val BOX_COLOR = ZINC_900
private const val HOVERED_COLOR = ZINC_700
private const val FONT = "13px system-ui, -apple-system, sans-serif"
private const val TEXT_COLOR = ZINC_400
private const val HOVERED_TEXT_COLOR = ZINC_100
private const val SELECTED_TEXT_COLOR = ZINC_100

private const val MARGIN = 20.0          // from the top and right of the window
private const val PLAY_BUTTON_SIZE = 40.0
private const val GAP = 8.0              // between this and the play button, and above the dropdown
private const val HEIGHT = 40.0
private const val BOX_RADIUS = 10.0
private const val PADDING = 4.0
private const val ROW_RADIUS = BOX_RADIUS - PADDING
private const val ROW_HEIGHT = 32.0
private const val ROW_GAP = 4.0
private const val TEXT_PADDING = 12.0

private class ScaleRow(val scale: Scale, val bounds: Rect)

private fun buttonBounds(ctx: CanvasRenderingContext2D): Rect {
    ctx.font = FONT
    val width = ctx.measureText(EditorState.scale.label).width + TEXT_PADDING * 2 + PADDING * 2
    val right = CanvasState.width - MARGIN - PLAY_BUTTON_SIZE - GAP
    return Rect(right - width, MARGIN, width, HEIGHT)
}

/** The dropdown's box and rows, right-aligned under the button, or null if it's closed. */
private fun layoutMenu(ctx: CanvasRenderingContext2D): Pair<Rect, List<ScaleRow>>? {
    if (!EditorState.scaleMenuOpen) return null
    val button = buttonBounds(ctx)
    ctx.font = FONT
    val rowWidth = Scale.entries.maxOf { ctx.measureText(it.label).width } + TEXT_PADDING * 2
    val width = rowWidth + PADDING * 2
    val x = button.x + button.width - width
    val top = button.y + button.height + GAP

    var y = top + PADDING
    val rows = Scale.entries.map { ScaleRow(it, Rect(x + PADDING, y, rowWidth, ROW_HEIGHT)).also { y += ROW_HEIGHT + ROW_GAP } }
    return Rect(x, top, width, y - ROW_GAP + PADDING - top) to rows
}

fun renderScalePicker(ctx: CanvasRenderingContext2D) {
    val button = buttonBounds(ctx)
    val pointerX = EditorState.pointerX
    val pointerY = EditorState.pointerY
    ctx.fillRoundedRect(button.x, button.y, button.width, button.height, BOX_RADIUS, BOX_COLOR)
    val hovered = button.contains(pointerX, pointerY)
    if (hovered || EditorState.scaleMenuOpen) {
        ctx.fillRoundedRect(
            button.x + PADDING, button.y + PADDING, button.width - PADDING * 2, button.height - PADDING * 2,
            ROW_RADIUS, HOVERED_COLOR,
        )
    }
    ctx.font = FONT
    ctx.textBaseline = CanvasTextBaseline.MIDDLE
    ctx.fillStyle = if (hovered || EditorState.scaleMenuOpen) HOVERED_TEXT_COLOR else TEXT_COLOR
    ctx.fillText(EditorState.scale.label, button.x + PADDING + TEXT_PADDING, button.y + button.height / 2)
}

/** Drawn last, so it sits on top of everything. */
fun renderScaleMenu(ctx: CanvasRenderingContext2D) {
    val (box, rows) = layoutMenu(ctx) ?: return
    ctx.fillRoundedRect(box.x, box.y, box.width, box.height, BOX_RADIUS, BOX_COLOR)
    ctx.font = FONT
    ctx.textBaseline = CanvasTextBaseline.MIDDLE
    for (row in rows) {
        val r = row.bounds
        val hovered = r.contains(EditorState.pointerX, EditorState.pointerY)
        if (hovered) ctx.fillRoundedRect(r.x, r.y, r.width, r.height, ROW_RADIUS, HOVERED_COLOR)
        ctx.fillStyle = when {
            row.scale == EditorState.scale -> SELECTED_TEXT_COLOR
            hovered -> HOVERED_TEXT_COLOR
            else -> TEXT_COLOR
        }
        ctx.fillText(row.scale.label, r.x + TEXT_PADDING, r.y + r.height / 2)
    }
}

fun isOverScalePicker(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    return buttonBounds(ctx).contains(x, y) || layoutMenu(ctx)?.first?.contains(x, y) == true
}

/**
 * Picks the clicked scale, or opens the dropdown if the button was clicked.
 * Returns true if either was hit.
 */
fun processScalePickerClick(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    val row = layoutMenu(ctx)?.second?.firstOrNull { it.bounds.contains(x, y) }
    if (row != null) {
        EditorState.scale = row.scale
        EditorState.scaleMenuOpen = false
        // The note range changed, so re-clamp every universe's camera to it.
        EditorState.universes.forEach { it.panBy(0.0, 0.0) }
        return true
    }
    if (buttonBounds(ctx).contains(x, y)) {
        EditorState.scaleMenuOpen = true
        return true
    }
    return false
}
