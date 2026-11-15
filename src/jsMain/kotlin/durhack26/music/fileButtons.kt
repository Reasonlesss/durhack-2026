package durhack26.music

import org.w3c.dom.CENTER
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextAlign
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.LEFT
import org.w3c.dom.MIDDLE

/*
 * The Help / Open / Save buttons, in a box in the bottom-right corner (clear of the sidebar),
 * styled like the top bar. ?, Ctrl+O and Ctrl+S do the same (see editor.kt). See help.kt and
 * songFile.kt for what they do.
 *
 * A button acts when it's released, not pressed: browsers only let a touch or pen open a
 * file dialog on release (a mouse can do either).
 */

private const val BOX_COLOR = ZINC_900
private const val HOVERED_COLOR = ZINC_700
private const val FONT = "13px system-ui, -apple-system, sans-serif"
private const val TEXT_COLOR = ZINC_400
private const val HOVERED_TEXT_COLOR = ZINC_100

private const val MARGIN = 20.0         // from the right and bottom of the window
private const val HEIGHT = 40.0
private const val BOX_RADIUS = 10.0
private const val PADDING = 4.0
private const val ITEM_RADIUS = BOX_RADIUS - PADDING
private const val ITEM_GAP = 4.0
private const val ITEM_PADDING = 12.0   // either side of a button's label

private class FileButton(val label: String, val action: () -> Unit)

private val buttons = listOf(
    FileButton("Help", ::showHelp),
    FileButton("Open", ::openSong),
    FileButton("Save", ::saveSong),
)

/** The button being pressed, which acts if the press is released over it. */
private var pressed: FileButton? = null

private fun layout(ctx: CanvasRenderingContext2D): Pair<Rect, List<Pair<FileButton, Rect>>> {
    ctx.font = FONT
    val widths = buttons.map { ctx.measureText(it.label).width + ITEM_PADDING * 2 }
    val boxWidth = widths.sum() + ITEM_GAP * (buttons.size - 1) + PADDING * 2
    val box = Rect(CanvasState.width - MARGIN - boxWidth, CanvasState.height - MARGIN - HEIGHT, boxWidth, HEIGHT)
    var x = box.x + PADDING
    val items = buttons.zip(widths) { button, width ->
        (button to Rect(x, box.y + PADDING, width, HEIGHT - PADDING * 2)).also { x += width + ITEM_GAP }
    }
    return box to items
}

fun renderFileButtons(ctx: CanvasRenderingContext2D) {
    val (box, items) = layout(ctx)
    ctx.fillRoundedRect(box.x, box.y, box.width, box.height, BOX_RADIUS, BOX_COLOR)
    ctx.font = FONT
    ctx.textBaseline = CanvasTextBaseline.MIDDLE
    ctx.textAlign = CanvasTextAlign.CENTER
    for ((button, r) in items) {
        val hovered = r.contains(EditorState.pointerX, EditorState.pointerY) || pressed === button
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

/** Starts pressing a button, if one is under (x, y). Returns true if the box was hit at all. */
fun processFileButtonsPress(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    val (box, items) = layout(ctx)
    pressed = items.firstOrNull { (_, r) -> r.contains(x, y) }?.first
    return box.contains(x, y)
}

/** Opens or saves if the button being pressed is released over itself. */
fun processFileButtonsRelease(x: Double, y: Double) {
    val button = pressed ?: return
    pressed = null
    val ctx = CanvasState.ctx ?: return
    if (layout(ctx).second.any { (b, r) -> b === button && r.contains(x, y) }) button.action()
}
