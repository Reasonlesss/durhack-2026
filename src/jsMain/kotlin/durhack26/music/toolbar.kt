package durhack26.music

import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextAlign
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.CENTER
import org.w3c.dom.MIDDLE

/*
 * Two vertical toolbars on the left, one above the other:
 * - Instruments: the instrument types added to this universe, with + to add a type.
 * - Effects: every effect type.
 * Rows show an icon with the name as a hover tooltip. Dragging an instrument or effect out
 * of the sidebar drops a new one where it's released. Clicking an instrument opens its
 * dropdown (see menu.kt).
 */

private const val BOX_COLOR = ZINC_900
private const val HOVERED_COLOR = ZINC_700
private const val FONT = "13px system-ui, -apple-system, sans-serif"
private const val TEXT_COLOR = ZINC_400
private const val HOVERED_TEXT_COLOR = ZINC_100

private const val TOOLTIP_FONT = "12px system-ui, -apple-system, sans-serif"
private const val TOOLTIP_COLOR = BOX_COLOR
private const val TOOLTIP_TEXT_COLOR = ZINC_100
private const val TOOLTIP_GAP = 8.0       // between the toolbar box and the tooltip
private const val TOOLTIP_PADDING = 8.0  // either side of the tooltip's text
private const val TOOLTIP_HEIGHT = 26.0
private const val TOOLTIP_RADIUS = 6.0

private const val BOX_X = 20.0
private const val INSTRUMENTS_Y = 76.0 // just below the tab box
private const val TOOLBAR_GAP = 12.0   // between the two toolbars
private const val BOX_RADIUS = 10.0
private const val PADDING = 4.0        // between the box edge and the items
private const val ITEM_RADIUS = BOX_RADIUS - PADDING
private const val ITEM_HEIGHT = 32.0
private const val ITEM_PADDING = 12.0  // either side of an item's content
private const val ITEM_GAP = 4.0       // between neighbouring items
private const val ICON_SIZE = 14.0
private const val ICON_GAP = 8.0       // between an icon and its label

/** What a toolbar row shows and does. */
private class Entry(
    val label: String,
    val icon: EffectType? = null,
    val shape: InstrumentShape? = null,
    val showLabel: Boolean = true,
    /** For rows that can be dragged out: adds a new item at world point (x, y) and returns it. */
    val create: ((x: Double, y: Double) -> Movable)? = null,
    val onClick: () -> Unit = {},
)

/** A row with its position worked out. */
private class Item(val entry: Entry, val x: Double, val y: Double, val width: Double) {
    fun contains(px: Double, py: Double) =
        px >= x && px < x + width && py >= y && py < y + ITEM_HEIGHT
}

private class Toolbar(val items: List<Item>, val top: Double) {
    val width = items.first().width + PADDING * 2
    val bottom = items.last().y + ITEM_HEIGHT + PADDING

    fun contains(px: Double, py: Double) =
        px >= BOX_X && px < BOX_X + width && py >= top && py < bottom
}

/** Lays [entries] out top to bottom from [top], all as wide as the widest one. */
private fun layout(ctx: CanvasRenderingContext2D, top: Double, entries: List<Entry>): Toolbar {
    ctx.font = FONT
    val width = entries.maxOf { entry ->
        if ((entry.icon != null || entry.shape != null) && !entry.showLabel) {
            ITEM_HEIGHT  // icon-only rows are square
        } else {
            contentWidth(ctx, entry) + ITEM_PADDING * 2
        }
    }.coerceAtLeast(ITEM_HEIGHT)
    var y = top + PADDING
    val items = entries.map { Item(it, BOX_X + PADDING, y, width).also { y += ITEM_HEIGHT + ITEM_GAP } }
    return Toolbar(items, top)
}

private fun contentWidth(ctx: CanvasRenderingContext2D, entry: Entry): Double {
    val text = if (entry.showLabel) ctx.measureText(entry.label).width else 0.0
    return if (entry.icon != null || entry.shape != null) {
        ICON_SIZE + if (text > 0.0) ICON_GAP + text else 0.0
    } else {
        text
    }
}

private fun instrumentEntries(): List<Entry> {
    val types = EditorState.selectedUniverse.instrumentTypes
    val rows = types.map { (shape, type) ->
        Entry(type.label, shape = shape, showLabel = false, create = { x, y ->
            Instrument(type, shape, x, y).also { EditorState.selectedUniverse.instruments += it }
        })
    }
    return if (EditorState.selectedUniverse.availableInstrumentTypes().isNotEmpty() || rows.isEmpty()) {
        rows + Entry("+") { EditorState.openInstrumentMenu(null) }
    } else {
        rows
    }
}

private fun effectEntries(): List<Entry> = EffectType.entries.map { type ->
    Entry(type.label, icon = type, showLabel = false, create = { x, y ->
        Effect(x, y, type).also { EditorState.selectedUniverse.effects += it }
    })
}

private fun layoutToolbars(ctx: CanvasRenderingContext2D): List<Toolbar> {
    val instruments = layout(ctx, INSTRUMENTS_Y, instrumentEntries())
    val effects = layout(ctx, instruments.bottom + TOOLBAR_GAP, effectEntries())
    return listOf(instruments, effects)
}

fun renderToolbar(ctx: CanvasRenderingContext2D) {
    val toolbars = layoutToolbars(ctx)
    for (toolbar in toolbars) renderToolbar(ctx, toolbar)

    val hovered = toolbars.flatMap { it.items }.firstOrNull {
        !it.entry.showLabel && it.contains(EditorState.pointerX, EditorState.pointerY)
    }
    if (hovered != null) renderTooltip(ctx, hovered)
}

private fun renderToolbar(ctx: CanvasRenderingContext2D, toolbar: Toolbar) {
    ctx.fillRoundedRect(BOX_X, toolbar.top, toolbar.width, toolbar.bottom - toolbar.top, BOX_RADIUS, BOX_COLOR)

    ctx.textBaseline = CanvasTextBaseline.MIDDLE
    for (item in toolbar.items) {
        val hovered = item.contains(EditorState.pointerX, EditorState.pointerY)
        if (hovered) {
            ctx.fillRoundedRect(item.x, item.y, item.width, ITEM_HEIGHT, ITEM_RADIUS, HOVERED_COLOR)
        }

        val centerY = item.y + ITEM_HEIGHT / 2
        val content = contentWidth(ctx, item.entry)
        var x = if (item.entry.label == "+" || !item.entry.showLabel) {
            item.x + (item.width - content) / 2  // centre icon-only rows and +
        } else {
            item.x + ITEM_PADDING
        }

        val icon = item.entry.icon
        val shape = item.entry.shape
        if (icon != null) {
            renderEffectIcon(ctx, icon, x + ICON_SIZE / 2, centerY, ICON_SIZE)
            x += ICON_SIZE + ICON_GAP
        } else if (shape != null) {
            renderInstrumentIcon(ctx, shape, x + ICON_SIZE / 2, centerY, ICON_SIZE)
            x += ICON_SIZE + ICON_GAP
        }

        if (item.entry.showLabel) {
            ctx.fillStyle = if (hovered) HOVERED_TEXT_COLOR else TEXT_COLOR
            ctx.fillText(item.entry.label, x, centerY)
        }
    }
}

/** Draws [item]'s name as a small rounded badge just to the right of the toolbar box. */
private fun renderTooltip(ctx: CanvasRenderingContext2D, item: Item) {
    ctx.save()
    ctx.font = TOOLTIP_FONT
    ctx.textBaseline = CanvasTextBaseline.MIDDLE
    ctx.textAlign = CanvasTextAlign.CENTER

    val width = maxOf(ctx.measureText(item.entry.label).width + TOOLTIP_PADDING * 2, TOOLTIP_HEIGHT)
    val x = item.x + item.width + PADDING + TOOLTIP_GAP
    val y = item.y + (ITEM_HEIGHT - TOOLTIP_HEIGHT) / 2

    ctx.fillRoundedRect(x, y, width, TOOLTIP_HEIGHT, TOOLTIP_RADIUS, TOOLTIP_COLOR)
    ctx.fillStyle = TOOLTIP_TEXT_COLOR
    ctx.fillText(item.entry.label, x + width / 2, y + TOOLTIP_HEIGHT / 2)
    ctx.restore()
}

/**
 * Runs the clicked row's action. Rows that can be dragged out create their item under the
 * pointer and start dragging it. Returns true if a row was hit.
 */
fun processToolbarClick(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    val item = layoutToolbars(ctx).flatMap { it.items }.firstOrNull { it.contains(x, y) } ?: return false
    val create = item.entry.create
    if (create != null) {
        val universe = EditorState.selectedUniverse
        EditorState.dragging = create(x + universe.cameraX, y + universe.cameraY)
        EditorState.draggingFromToolbar = true
    } else {
        item.entry.onClick()
    }
    return true
}

/**
 * Called when a drag ends. An item pulled out of the sidebar but released back over it was
 * only clicked: it's removed again, and for an instrument its edit dropdown opens.
 */
fun processToolbarDrop(x: Double, y: Double) {
    if (!EditorState.draggingFromToolbar) return
    EditorState.draggingFromToolbar = false
    val item = EditorState.dragging ?: return
    val ctx = CanvasState.ctx ?: return
    if (layoutToolbars(ctx).none { it.contains(x, y) }) return

    val universe = EditorState.selectedUniverse
    when (item) {
        is Instrument -> {
            universe.removeInstrument(item)
            EditorState.openInstrumentMenu(item.shape)
        }
        is Effect -> universe.effects.remove(item)
    }
}

/** Whether screen point (x, y) is over either toolbar box. */
fun isOverToolbar(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    return layoutToolbars(ctx).any { it.contains(x, y) }
}

/**
 * Where the instruments toolbar row for [shape] is on screen (the + row if null), or null
 * if there's no such row. The dropdown opens beside it.
 */
fun instrumentRowBounds(shape: InstrumentShape?): Rect? {
    val ctx = CanvasState.ctx ?: return null
    val rows = layoutToolbars(ctx).first().items
    val row = if (shape == null) rows.lastOrNull { it.entry.label == "+" } else rows.firstOrNull { it.entry.shape == shape }
    return row?.let { Rect(it.x, it.y, it.width, ITEM_HEIGHT) }
}
