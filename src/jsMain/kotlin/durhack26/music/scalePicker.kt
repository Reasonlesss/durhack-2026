package durhack26.music

import org.w3c.dom.CENTER
import org.w3c.dom.CanvasLineCap
import org.w3c.dom.CanvasLineJoin
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextAlign
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.LEFT
import org.w3c.dom.MIDDLE
import org.w3c.dom.RIGHT
import org.w3c.dom.ROUND
import kotlin.math.max

/*
 * The scale picker: a button just left of the play button showing the current mood (and
 * key, if it isn't C). Clicking it opens a dropdown below it: every mood, then an advanced
 * "Key" row that opens a grid of the 12 keys. Styled like the tab box.
 *
 *   ┌────────────┐
 *   │ Happy      │
 *   │ Sad        │
 *   │ …          │
 *   ├────────────┤
 *   │ Key    A ▾ │
 *   │ C  C# D  D#│
 *   │ E  F  F# G │
 *   │ G# A  A# B │
 *   └────────────┘
 */

private const val BOX_COLOR = ZINC_900
private const val HOVERED_COLOR = ZINC_700
private const val SELECTED_KEY_COLOR = VIOLET_500
private const val DIVIDER_COLOR = ZINC_700
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
private const val SECTION_GAP = 6.0      // either side of the divider above the key row
private const val CHEVRON_SIZE = 8.0
private const val CHEVRON_GAP = 6.0      // between the key's name and the chevron
private const val KEY_COLUMNS = 4
private const val KEY_ROWS = KEY_COUNT / KEY_COLUMNS
private const val KEY_MIN_WIDTH = 36.0
private const val KEY_HEIGHT = 28.0
private const val KEY_GAP = 4.0

/** Whether the key grid is showing. Kept between openings of the dropdown. */
private var keysOpen = false

private class ScaleRow(val scale: Scale, val bounds: Rect)
private class KeyCell(val key: Int, val bounds: Rect)

private class ScaleMenu(
    val box: Rect,
    val rows: List<ScaleRow>,
    val dividerY: Double,
    val keyRow: Rect,
    /** Empty while the key grid is closed. */
    val keys: List<KeyCell>,
)

/** What the button says: the mood, plus the key unless it's C (the default). */
private fun buttonLabel() =
    if (EditorState.key == 0) EditorState.scale.label else "${EditorState.scale.label} · ${keyName(EditorState.key)}"

private fun buttonBounds(ctx: CanvasRenderingContext2D): Rect {
    ctx.font = FONT
    val width = ctx.measureText(buttonLabel()).width + TEXT_PADDING * 2 + PADDING * 2
    val right = CanvasState.width - MARGIN - PLAY_BUTTON_SIZE - GAP
    return Rect(right - width, MARGIN, width, HEIGHT)
}

/** The dropdown's parts, right-aligned under the button, or null if it's closed. */
private fun layoutMenu(ctx: CanvasRenderingContext2D): ScaleMenu? {
    if (!EditorState.scaleMenuOpen) return null
    val button = buttonBounds(ctx)
    ctx.font = FONT
    val moodWidth = Scale.entries.maxOf { ctx.measureText(it.label).width } + TEXT_PADDING * 2
    val keyRowWidth = ctx.measureText("Key ${keyName(1)}").width + TEXT_PADDING * 3 + CHEVRON_GAP + CHEVRON_SIZE
    val gridWidth = KEY_MIN_WIDTH * KEY_COLUMNS + KEY_GAP * (KEY_COLUMNS - 1)
    val rowWidth = max(max(moodWidth, keyRowWidth), gridWidth)
    val width = rowWidth + PADDING * 2
    val x = button.x + button.width - width
    val left = x + PADDING
    val top = button.y + button.height + GAP

    var y = top + PADDING
    val rows = Scale.entries.map { scale ->
        ScaleRow(scale, Rect(left, y, rowWidth, ROW_HEIGHT)).also { y += ROW_HEIGHT + ROW_GAP }
    }
    y += SECTION_GAP - ROW_GAP
    val dividerY = y
    y += 1 + SECTION_GAP
    val keyRow = Rect(left, y, rowWidth, ROW_HEIGHT)
    y += ROW_HEIGHT

    val keys = if (!keysOpen) emptyList() else {
        y += ROW_GAP
        val cellWidth = (rowWidth - KEY_GAP * (KEY_COLUMNS - 1)) / KEY_COLUMNS
        val gridTop = y
        y += KEY_ROWS * KEY_HEIGHT + (KEY_ROWS - 1) * KEY_GAP
        (0 until KEY_COUNT).map { key ->
            val column = key % KEY_COLUMNS
            val row = key / KEY_COLUMNS
            KeyCell(key, Rect(left + column * (cellWidth + KEY_GAP), gridTop + row * (KEY_HEIGHT + KEY_GAP), cellWidth, KEY_HEIGHT))
        }
    }
    return ScaleMenu(Rect(x, top, width, y + PADDING - top), rows, dividerY, keyRow, keys)
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
    ctx.fillText(buttonLabel(), button.x + PADDING + TEXT_PADDING, button.y + button.height / 2)
}

/** Drawn last, so it sits on top of everything. */
fun renderScaleMenu(ctx: CanvasRenderingContext2D) {
    val menu = layoutMenu(ctx) ?: return
    val px = EditorState.pointerX
    val py = EditorState.pointerY
    val box = menu.box
    ctx.fillRoundedRect(box.x, box.y, box.width, box.height, BOX_RADIUS, BOX_COLOR)
    ctx.font = FONT
    ctx.textBaseline = CanvasTextBaseline.MIDDLE

    for (row in menu.rows) {
        val r = row.bounds
        val hovered = r.contains(px, py)
        if (hovered) ctx.fillRoundedRect(r.x, r.y, r.width, r.height, ROW_RADIUS, HOVERED_COLOR)
        ctx.fillStyle = when {
            row.scale == EditorState.scale -> SELECTED_TEXT_COLOR
            hovered -> HOVERED_TEXT_COLOR
            else -> TEXT_COLOR
        }
        ctx.fillText(row.scale.label, r.x + TEXT_PADDING, r.y + r.height / 2)
    }

    ctx.fillStyle = DIVIDER_COLOR
    ctx.fillRect(box.x + PADDING + TEXT_PADDING, menu.dividerY, box.width - (PADDING + TEXT_PADDING) * 2, 1.0)

    // "Key" on the left; the current key and a chevron (down when open) on the right.
    val r = menu.keyRow
    val keyRowHovered = r.contains(px, py)
    if (keyRowHovered) ctx.fillRoundedRect(r.x, r.y, r.width, r.height, ROW_RADIUS, HOVERED_COLOR)
    val textColor = if (keyRowHovered || keysOpen) HOVERED_TEXT_COLOR else TEXT_COLOR
    val midY = r.y + r.height / 2
    ctx.fillStyle = textColor
    ctx.fillText("Key", r.x + TEXT_PADDING, midY)
    val chevronX = r.x + r.width - TEXT_PADDING - CHEVRON_SIZE / 2
    ctx.textAlign = CanvasTextAlign.RIGHT
    ctx.fillStyle = SELECTED_TEXT_COLOR
    ctx.fillText(keyName(EditorState.key), chevronX - CHEVRON_SIZE / 2 - CHEVRON_GAP, midY)
    ctx.textAlign = CanvasTextAlign.LEFT
    renderChevron(ctx, chevronX, midY, down = keysOpen, textColor)

    ctx.textAlign = CanvasTextAlign.CENTER
    for (cell in menu.keys) {
        val c = cell.bounds
        val selected = cell.key == EditorState.key
        val hovered = c.contains(px, py)
        if (selected || hovered) {
            ctx.fillRoundedRect(c.x, c.y, c.width, c.height, ROW_RADIUS, if (selected) SELECTED_KEY_COLOR else HOVERED_COLOR)
        }
        ctx.fillStyle = if (selected || hovered) SELECTED_TEXT_COLOR else TEXT_COLOR
        ctx.fillText(keyName(cell.key), c.x + c.width / 2, c.y + c.height / 2)
    }
    ctx.textAlign = CanvasTextAlign.LEFT
}

/** A small chevron centred on (cx, cy), pointing down if [down], else right. */
private fun renderChevron(ctx: CanvasRenderingContext2D, cx: Double, cy: Double, down: Boolean, color: String) {
    val h = CHEVRON_SIZE / 2
    ctx.strokeStyle = color
    ctx.lineWidth = 1.5
    ctx.lineCap = CanvasLineCap.ROUND
    ctx.lineJoin = CanvasLineJoin.ROUND
    ctx.beginPath()
    if (down) {
        ctx.moveTo(cx - h, cy - h / 2)
        ctx.lineTo(cx, cy + h / 2)
        ctx.lineTo(cx + h, cy - h / 2)
    } else {
        ctx.moveTo(cx - h / 2, cy - h)
        ctx.lineTo(cx + h / 2, cy)
        ctx.lineTo(cx - h / 2, cy + h)
    }
    ctx.stroke()
}

/** The left edge of the scale picker's button, so the tempo control can sit just left of it. */
fun scalePickerLeft(ctx: CanvasRenderingContext2D) = buttonBounds(ctx).x

fun isOverScalePicker(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    return buttonBounds(ctx).contains(x, y) || layoutMenu(ctx)?.box?.contains(x, y) == true
}

/**
 * Handles a click on the scale picker. Picking a mood closes the dropdown; the key row and
 * keys leave it open, so keys can be tried one after another. The button toggles it.
 * Returns true if anything was hit.
 */
fun processScalePickerClick(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    val menu = layoutMenu(ctx)
    if (menu != null) {
        val row = menu.rows.firstOrNull { it.bounds.contains(x, y) }
        if (row != null) {
            EditorState.scale = row.scale
            EditorState.scaleMenuOpen = false
            // The note range changed, so re-clamp every universe's camera to it.
            EditorState.universes.forEach { it.panBy(0.0, 0.0) }
            return true
        }
        if (menu.keyRow.contains(x, y)) {
            keysOpen = !keysOpen
            return true
        }
        val cell = menu.keys.firstOrNull { it.bounds.contains(x, y) }
        if (cell != null) {
            EditorState.key = cell.key
            return true
        }
        // Between rows: still on the dropdown, so it stays open.
        if (menu.box.contains(x, y)) return true
    }
    if (buttonBounds(ctx).contains(x, y)) {
        EditorState.scaleMenuOpen = !EditorState.scaleMenuOpen
        return true
    }
    return false
}
