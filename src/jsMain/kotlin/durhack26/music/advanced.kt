package durhack26.music

import kotlinx.browser.window
import org.w3c.dom.CENTER
import org.w3c.dom.CanvasLineCap
import org.w3c.dom.CanvasLineJoin
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextAlign
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.LEFT
import org.w3c.dom.MIDDLE
import org.w3c.dom.ROUND

/*
 * The Advanced box, in the bottom-left corner, styled like the Open / Save box:
 *
 *   [ ☐ Advanced ]                                  ticked:
 *   [ ☑ Advanced   Import preset   Import sample ]
 *
 * Ticking it shows the options that aren't for beginners: importing Sylenth1 / Serum
 * presets and audio samples (see imports.kt). Like Open / Save, the import buttons act on
 * release, since browsers only let a touch open a file dialog then.
 */

private const val BOX_COLOR = ZINC_900
private const val HOVERED_COLOR = ZINC_700
private const val CHECKED_COLOR = VIOLET_500
private const val FONT = "13px system-ui, -apple-system, sans-serif"
private const val TEXT_COLOR = ZINC_400
private const val HOVERED_TEXT_COLOR = ZINC_100
private const val STATUS_FONT = "12px system-ui, -apple-system, sans-serif"
private const val STATUS_COLOR = ZINC_300

private const val MARGIN = 20.0         // from the left and bottom of the window
private const val HEIGHT = 40.0
private const val BOX_RADIUS = 10.0
private const val PADDING = 4.0
private const val ITEM_RADIUS = BOX_RADIUS - PADDING
private const val ITEM_GAP = 4.0
private const val ITEM_PADDING = 12.0   // either side of an item's content
private const val CHECK_SIZE = 14.0
private const val CHECK_GAP = 8.0       // between the checkbox and "Advanced"
private const val STATUS_GAP = 8.0      // between the box and the status line above it

/** How long a status line (e.g. "Imported …") stays up, in ms. */
private const val STATUS_MS = 5000.0

private enum class AdvancedItem(val label: String) {
    TOGGLE("Advanced"),
    IMPORT_PRESET("Import preset"),
    IMPORT_SAMPLE("Import sample"),
}

/** The import button being pressed, which acts if the press is released over it. */
private var pressed: AdvancedItem? = null

private var status: String? = null
private var statusAt = Double.NEGATIVE_INFINITY

/** Shows [message] just above the box for a few seconds. */
fun showImportStatus(message: String) {
    status = message
    statusAt = window.performance.now()
}

private fun layout(ctx: CanvasRenderingContext2D): Pair<Rect, List<Pair<AdvancedItem, Rect>>> {
    ctx.font = FONT
    val shown = if (EditorState.advanced) AdvancedItem.entries else listOf(AdvancedItem.TOGGLE)
    val widths = shown.map {
        ctx.measureText(it.label).width + ITEM_PADDING * 2 + if (it == AdvancedItem.TOGGLE) CHECK_SIZE + CHECK_GAP else 0.0
    }
    val boxWidth = widths.sum() + ITEM_GAP * (shown.size - 1) + PADDING * 2
    val box = Rect(MARGIN, CanvasState.height - MARGIN - HEIGHT, boxWidth, HEIGHT)
    var x = box.x + PADDING
    val items = shown.zip(widths) { item, width ->
        (item to Rect(x, box.y + PADDING, width, HEIGHT - PADDING * 2)).also { x += width + ITEM_GAP }
    }
    return box to items
}

fun renderAdvanced(ctx: CanvasRenderingContext2D) {
    val (box, items) = layout(ctx)
    ctx.fillRoundedRect(box.x, box.y, box.width, box.height, BOX_RADIUS, BOX_COLOR)
    ctx.font = FONT
    ctx.textBaseline = CanvasTextBaseline.MIDDLE
    for ((item, r) in items) {
        val hovered = r.contains(EditorState.pointerX, EditorState.pointerY) || pressed === item
        if (hovered) ctx.fillRoundedRect(r.x, r.y, r.width, r.height, ITEM_RADIUS, HOVERED_COLOR)
        val color = if (hovered) HOVERED_TEXT_COLOR else TEXT_COLOR
        val midY = r.y + r.height / 2
        if (item == AdvancedItem.TOGGLE) {
            val checkX = r.x + ITEM_PADDING
            renderCheckbox(ctx, checkX, midY - CHECK_SIZE / 2, EditorState.advanced, color)
            ctx.textAlign = CanvasTextAlign.LEFT
            ctx.fillStyle = if (EditorState.advanced) HOVERED_TEXT_COLOR else color
            ctx.fillText(item.label, checkX + CHECK_SIZE + CHECK_GAP, midY)
        } else {
            ctx.textAlign = CanvasTextAlign.CENTER
            ctx.fillStyle = color
            ctx.fillText(item.label, r.x + r.width / 2, midY)
        }
    }
    ctx.textAlign = CanvasTextAlign.LEFT

    val message = status
    if (message != null && window.performance.now() - statusAt < STATUS_MS) {
        ctx.font = STATUS_FONT
        ctx.fillStyle = STATUS_COLOR
        ctx.textBaseline = CanvasTextBaseline.MIDDLE
        ctx.fillText(message, box.x + PADDING, box.y - STATUS_GAP - 6)
    }
}

/** A rounded checkbox with its top-left at (x, y): filled with a tick when [checked]. */
private fun renderCheckbox(ctx: CanvasRenderingContext2D, x: Double, y: Double, checked: Boolean, color: String) {
    if (checked) {
        ctx.fillRoundedRect(x, y, CHECK_SIZE, CHECK_SIZE, 3.0, CHECKED_COLOR)
        ctx.strokeStyle = WHITE
        ctx.lineWidth = 2.0
        ctx.lineCap = CanvasLineCap.ROUND
        ctx.lineJoin = CanvasLineJoin.ROUND
        ctx.beginPath()
        ctx.moveTo(x + CHECK_SIZE * 0.25, y + CHECK_SIZE * 0.52)
        ctx.lineTo(x + CHECK_SIZE * 0.43, y + CHECK_SIZE * 0.7)
        ctx.lineTo(x + CHECK_SIZE * 0.76, y + CHECK_SIZE * 0.32)
        ctx.stroke()
    } else {
        ctx.strokeRoundedRect(x, y, CHECK_SIZE, CHECK_SIZE, 3.0, color, 1.5)
    }
}

fun isOverAdvanced(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    return layout(ctx).first.contains(x, y)
}

/**
 * The checkbox toggles straight away; an import button starts being pressed. Returns true
 * if the box was hit at all.
 */
fun processAdvancedPress(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    val (box, items) = layout(ctx)
    val item = items.firstOrNull { (_, r) -> r.contains(x, y) }?.first
    if (item == AdvancedItem.TOGGLE) {
        EditorState.advanced = !EditorState.advanced
        pressed = null
    } else {
        pressed = item
    }
    return box.contains(x, y)
}

/** Opens an import dialog if the button being pressed is released over itself. */
fun processAdvancedRelease(x: Double, y: Double) {
    val item = pressed ?: return
    pressed = null
    val ctx = CanvasState.ctx ?: return
    if (layout(ctx).second.none { (i, r) -> i === item && r.contains(x, y) }) return
    when (item) {
        AdvancedItem.IMPORT_PRESET -> importPreset()
        AdvancedItem.IMPORT_SAMPLE -> importSample()
        AdvancedItem.TOGGLE -> {}
    }
}
