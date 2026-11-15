package durhack26.music

import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.MIDDLE

/*
 * The instrument dropdown, beside the toolbar row it was opened from. Types are grouped
 * into columns by category, each with a small heading. From +, picking one adds it; from
 * an instrument, picking one switches it (its current type is highlighted), and a Delete
 * row runs along the bottom. It's styled to match the toolbar.
 */

private const val BOX_COLOR = ZINC_900
private const val HOVERED_COLOR = ZINC_700
private const val FONT = "13px system-ui, -apple-system, sans-serif"
private const val TEXT_COLOR = ZINC_400
private const val HOVERED_TEXT_COLOR = ZINC_100
private const val CURRENT_TEXT_COLOR = ZINC_100
private const val HEADING_FONT = "11px system-ui, -apple-system, sans-serif"
private const val HEADING_COLOR = ZINC_500

private const val BOX_RADIUS = 10.0
private const val PADDING = 4.0        // between the box edge and the rows
private const val ROW_RADIUS = BOX_RADIUS - PADDING
private const val ROW_HEIGHT = 32.0
private const val ROW_PADDING = 12.0   // either side of a row's label
private const val ROW_GAP = 4.0        // between neighbouring rows
private const val COLUMN_GAP = 4.0     // between neighbouring columns
private const val HEADING_HEIGHT = 24.0
private const val MENU_GAP = 8.0       // between the toolbar and the dropdown

private class Row(val label: String, val bounds: Rect, val current: Boolean = false, val onClick: () -> Unit)

private class Heading(val label: String, val x: Double, val y: Double)

private class Menu(val bounds: Rect, val rows: List<Row>, val headings: List<Heading>)

private fun layoutInstrumentMenu(ctx: CanvasRenderingContext2D): Menu? {
    if (!EditorState.instrumentMenuOpen) return null
    val universe = EditorState.selectedUniverse
    val editing = EditorState.editingShape
    val anchor = instrumentRowBounds(editing) ?: return null

    val types = if (editing == null) universe.availableInstrumentTypes() else InstrumentType.entries
    if (types.isEmpty()) return null
    val current = editing?.let { universe.instrumentTypes[it] }
    fun pick(type: InstrumentType) {
        if (editing == null) {
            universe.nextInstrumentShape()?.let { universe.instrumentTypes[it] = type }
        } else {
            universe.changeInstrumentType(editing, type)
        }
    }

    val left = anchor.x + anchor.width + PADDING + MENU_GAP
    val top = anchor.y
    val rows = mutableListOf<Row>()
    val headings = mutableListOf<Heading>()
    var x = left + PADDING
    var bottom = top
    for (category in InstrumentCategory.entries) {
        val inCategory = types.filter { it.category == category }
        if (inCategory.isEmpty()) continue
        ctx.font = FONT
        val width = inCategory.maxOf { ctx.measureText(it.label).width + ROW_PADDING * 2 }
        ctx.font = HEADING_FONT
        val columnWidth = maxOf(width, ctx.measureText(category.label).width + ROW_PADDING * 2)

        headings += Heading(category.label, x + ROW_PADDING, top + PADDING + HEADING_HEIGHT / 2)
        var y = top + PADDING + HEADING_HEIGHT
        for (type in inCategory) {
            rows += Row(type.label, Rect(x, y, columnWidth, ROW_HEIGHT), current = type == current) { pick(type) }
            y += ROW_HEIGHT + ROW_GAP
        }
        bottom = maxOf(bottom, y - ROW_GAP)
        x += columnWidth + COLUMN_GAP
    }
    val right = x - COLUMN_GAP

    if (editing != null) {
        rows += Row("Delete", Rect(left + PADDING, bottom + ROW_GAP, right - left - PADDING, ROW_HEIGHT)) {
            universe.removeInstrumentType(editing)
        }
        bottom += ROW_GAP + ROW_HEIGHT
    }
    return Menu(Rect(left, top, right + PADDING - left, bottom + PADDING - top), rows, headings)
}

/** Whether screen point (x, y) is over the open dropdown. */
fun isOverInstrumentMenu(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    return layoutInstrumentMenu(ctx)?.bounds?.contains(x, y) == true
}

/** Whether the dropdown is open and on screen (it can be open with nothing to show, e.g. after switching universe). */
fun isInstrumentMenuShown(): Boolean {
    val ctx = CanvasState.ctx ?: return false
    return layoutInstrumentMenu(ctx) != null
}

fun renderInstrumentMenu(ctx: CanvasRenderingContext2D) {
    val menu = layoutInstrumentMenu(ctx) ?: return
    val box = menu.bounds
    ctx.fillRoundedRect(box.x, box.y, box.width, box.height, BOX_RADIUS, BOX_COLOR)

    ctx.textBaseline = CanvasTextBaseline.MIDDLE
    ctx.font = HEADING_FONT
    ctx.fillStyle = HEADING_COLOR
    for (heading in menu.headings) ctx.fillText(heading.label, heading.x, heading.y)

    ctx.font = FONT
    for (row in menu.rows) {
        val r = row.bounds
        val hovered = r.contains(EditorState.pointerX, EditorState.pointerY)
        if (hovered) ctx.fillRoundedRect(r.x, r.y, r.width, r.height, ROW_RADIUS, HOVERED_COLOR)
        ctx.fillStyle = if (hovered) HOVERED_TEXT_COLOR else if (row.current) CURRENT_TEXT_COLOR else TEXT_COLOR
        ctx.fillText(row.label, r.x + ROW_PADDING, r.y + r.height / 2)
    }
}

/** Runs the clicked dropdown row and closes the dropdown. Returns true if a row was hit. */
fun processInstrumentMenuClick(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    val menu = layoutInstrumentMenu(ctx) ?: return false
    val row = menu.rows.firstOrNull { it.bounds.contains(x, y) } ?: return false
    row.onClick()
    EditorState.instrumentMenuOpen = false
    return true
}
