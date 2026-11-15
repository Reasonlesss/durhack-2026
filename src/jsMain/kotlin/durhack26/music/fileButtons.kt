package durhack26.music

import org.w3c.dom.CENTER
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextAlign
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.LEFT
import org.w3c.dom.MIDDLE

/*
 * The Open / Save buttons, in a box in the bottom-left corner, styled like the top bar.
 * Ctrl+O and Ctrl+S do the same (see editor.kt). See songFile.kt for what they do.
 */

private const val BOX_COLOR = ZINC_900
private const val HOVERED_COLOR = ZINC_700
private const val FONT = "13px system-ui, -apple-system, sans-serif"
private const val TEXT_COLOR = ZINC_400
private const val HOVERED_TEXT_COLOR = ZINC_100

private const val MARGIN = 20.0         // from the left and bottom of the window
private const val HEIGHT = 40.0
private const val BOX_RADIUS = 10.0
private const val PADDING = 4.0
private const val ITEM_RADIUS = BOX_RADIUS - PADDING
private const val ITEM_GAP = 4.0
private const val ITEM_PADDING = 12.0   // either side of a button's label

private class FileButton(val label: String, val action: () -> Unit)

private val buttons = listOf(
    FileButton("Open", ::openSong),
    FileButton("Save", ::saveSong),
)

private fun layout(ctx: CanvasRenderingContext2D): Pair<Rect, List<Pair<FileButton, Rect>>> {
    ctx.font = FONT
    var x = MARGIN + PADDING
    val y = CanvasState.height - MARGIN - HEIGHT + PADDING
    val items = buttons.map { button ->
        val width = ctx.measureText(button.label).width + ITEM_PADDING * 2
        (button to Rect(x, y, width, HEIGHT - PADDING * 2)).also { x += width + ITEM_GAP }
    }
    val box = Rect(MARGIN, CanvasState.height - MARGIN - HEIGHT, x - ITEM_GAP + PADDING - MARGIN, HEIGHT)
    return box to items
}

fun renderFileButtons(ctx: CanvasRenderingContext2D) {
    val (box, items) = layout(ctx)
    ctx.fillRoundedRect(box.x, box.y, box.width, box.height, BOX_RADIUS, BOX_COLOR)
    ctx.font = FONT
    ctx.textBaseline = CanvasTextBaseline.MIDDLE
    ctx.textAlign = CanvasTextAlign.CENTER
    for ((button, r) in items) {
        val hovered = r.contains(EditorState.pointerX, EditorState.pointerY)
        if (hovered) ctx.fillRoundedRect(r.x, r.y, r.width, r.height, ITEM_RADIUS, HOVERED_COLOR)
        ctx.fillStyle = if (hovered) HOVERED_TEXT_COLOR else TEXT_COLOR
        ctx.fillText(button.label, r.x + r.width / 2, r.y + r.height / 2)
    }
    ctx.textAlign = CanvasTextAlign.LEFT
}

fun isOverFileButtons(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    return layout(ctx).first.contains(x, y)
}

/** Opens or saves if a button was clicked. Returns true if the box was hit at all. */
fun processFileButtonsClick(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    val (box, items) = layout(ctx)
    items.firstOrNull { (_, r) -> r.contains(x, y) }?.first?.action?.invoke()
    return box.contains(x, y)
}
