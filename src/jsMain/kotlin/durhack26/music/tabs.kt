package durhack26.music

import org.w3c.dom.CanvasLineCap
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.MIDDLE
import org.w3c.dom.ROUND
import kotlin.math.PI

private const val TABS_COLOR = ZINC_900
private const val TABS_HOVERED_COLOR = ZINC_700
private const val TABS_SELECTED_COLOR = ZINC_800
private const val TAB_FONT = "13px system-ui, -apple-system, sans-serif"
private const val TEXT_COLOR = ZINC_400
private const val SELECTED_TEXT_COLOR = ZINC_100
private const val TOGGLE_COLOR = VIOLET_500

private const val BOX_X = 20.0
private const val BOX_Y = 20.0
private const val BOX_HEIGHT = 40.0
private const val BOX_RADIUS = 10.0
private const val PADDING = 4.0       // between the box edge and the tabs
private const val TAB_RADIUS = BOX_RADIUS - PADDING
private const val TAB_PADDING = 12.0  // either side of a tab's name
private const val TAB_GAP = 4.0       // between neighbouring tabs
private const val TAB_HEIGHT = BOX_HEIGHT - PADDING * 2
private const val TAB_Y = BOX_Y + PADDING
private const val TOGGLE_SIZE = 10.0   // diameter of the enabled circle
private const val TOGGLE_GAP = 8.0    // between the toggle and the name
private const val CLOSE_SIZE = 8.0    // the × icon
private const val CLOSE_GAP = 10.0    // between the name and the ×

/** One item in the box: a universe's tab, or the + button (universe == null). */
private class Tab(val universe: Universe?, val x: Double, val width: Double) {
    fun contains(px: Double, py: Double) =
        px >= x && px < x + width && py >= TAB_Y && py < TAB_Y + TAB_HEIGHT

    /** Whether (px, py) is on the enabled toggle (the left part of the tab, up to the name). */
    fun toggleContains(px: Double, py: Double) =
        universe != null && contains(px, py) && px < x + TAB_PADDING + TOGGLE_SIZE + TOGGLE_GAP / 2

    /** Whether (px, py) is on the × (the right part of the tab, after the name). */
    fun closeContains(px: Double, py: Double) =
        universe != null && contains(px, py) && px >= x + width - TAB_PADDING - CLOSE_SIZE - CLOSE_GAP / 2
}

/** The × shows on the selected or hovered tab, unless it's the only universe left. */
private fun showsClose(tab: Tab) =
    tab.universe != null && EditorState.universes.size > 1 &&
        (tab.universe === EditorState.selectedUniverse || tab.contains(EditorState.pointerX, EditorState.pointerY))

/** Tabs left to right, each as wide as its name, with + at the end. */
private fun layoutTabs(ctx: CanvasRenderingContext2D): List<Tab> {
    ctx.font = TAB_FONT
    var x = BOX_X + PADDING
    val tabs = mutableListOf<Tab>()
    for (universe in EditorState.universes + null) {
        val label = universe?.name ?: "+"
        var width = ctx.measureText(label).width + TAB_PADDING * 2
        // Room for the toggle and × is always kept, so the name doesn't shift when the × appears.
        if (universe != null) width += TOGGLE_SIZE + TOGGLE_GAP + CLOSE_GAP + CLOSE_SIZE
        tabs += Tab(universe, x, width)
        x += width + TAB_GAP
    }
    return tabs
}

fun renderUniverseTabs(ctx: CanvasRenderingContext2D) {
    val tabs = layoutTabs(ctx)
    val boxWidth = tabs.last().let { it.x + it.width } + PADDING - BOX_X
    ctx.fillRoundedRect(BOX_X, BOX_Y, boxWidth, BOX_HEIGHT, BOX_RADIUS, TABS_COLOR)

    ctx.textBaseline = CanvasTextBaseline.MIDDLE
    for (tab in tabs) {
        val selected = tab.universe != null && tab.universe === EditorState.selectedUniverse
        val hovered = tab.contains(EditorState.pointerX, EditorState.pointerY)

        if (selected || hovered) {
            val color = if (selected) TABS_SELECTED_COLOR else TABS_HOVERED_COLOR
            ctx.fillRoundedRect(tab.x, TAB_Y, tab.width, TAB_HEIGHT, TAB_RADIUS, color)
        }

        var textX = tab.x + TAB_PADDING
        if (tab.universe != null) {
            renderToggle(ctx, textX, tab.universe.enabled)
            textX += TOGGLE_SIZE + TOGGLE_GAP
        }

        ctx.fillStyle = if (selected || hovered) SELECTED_TEXT_COLOR else TEXT_COLOR
        ctx.fillText(tab.universe?.name ?: "+", textX, TAB_Y + TAB_HEIGHT / 2)

        if (showsClose(tab)) {
            val closeHovered = tab.closeContains(EditorState.pointerX, EditorState.pointerY)
            renderClose(ctx, tab.x + tab.width - TAB_PADDING - CLOSE_SIZE, if (closeHovered) SELECTED_TEXT_COLOR else TEXT_COLOR)
        }
    }
}

private fun renderClose(ctx: CanvasRenderingContext2D, x: Double, color: String) {
    val y = TAB_Y + (TAB_HEIGHT - CLOSE_SIZE) / 2
    ctx.beginPath()
    ctx.moveTo(x, y)
    ctx.lineTo(x + CLOSE_SIZE, y + CLOSE_SIZE)
    ctx.moveTo(x + CLOSE_SIZE, y)
    ctx.lineTo(x, y + CLOSE_SIZE)
    ctx.strokeStyle = color
    ctx.lineWidth = 1.5
    ctx.lineCap = CanvasLineCap.ROUND
    ctx.stroke()
}

/** A circle: filled when enabled, just an outline when disabled. */
private fun renderToggle(ctx: CanvasRenderingContext2D, x: Double, on: Boolean) {
    val radius = TOGGLE_SIZE / 2
    ctx.beginPath()
    if (on) {
        ctx.arc(x + radius, TAB_Y + TAB_HEIGHT / 2, radius, 0.0, 2 * PI)
        ctx.fillStyle = TOGGLE_COLOR
        ctx.fill()
    } else {
        // Inset by half the line width so both states are the same size.
        ctx.arc(x + radius, TAB_Y + TAB_HEIGHT / 2, radius - 0.75, 0.0, 2 * PI)
        ctx.strokeStyle = TOGGLE_COLOR
        ctx.lineWidth = 1.5
        ctx.stroke()
    }
}

/**
 * Flips a universe's toggle, closes it via its ×, selects the clicked tab,
 * or adds a universe if + was clicked.
 * Returns true if a tab was hit.
 */
fun processTabClick(x: Double, y: Double): Boolean {
    val ctx = CanvasState.ctx ?: return false
    val tab = layoutTabs(ctx).firstOrNull { it.contains(x, y) } ?: return false

    if (tab.toggleContains(x, y)) {
        tab.universe!!.enabled = !tab.universe.enabled
    } else if (showsClose(tab) && tab.closeContains(x, y)) {
        closeUniverse(tab.universe!!)
    } else if (tab.universe != null) {
        EditorState.selectedUniverse = tab.universe
    } else {
        // Lowest unused number, so closing tabs doesn't lead to duplicate names.
        val names = EditorState.universes.map { it.name }
        val number = generateSequence(1) { it + 1 }.first { "Universe $it" !in names }
        val universe = Universe("Universe $number")
        EditorState.universes += universe
        EditorState.selectedUniverse = universe
    }
    return true
}

/** Removes [universe]. If it was selected, its right neighbour (or left, if it was last) is selected. */
private fun closeUniverse(universe: Universe) {
    val universes = EditorState.universes
    if (universes.size <= 1) return
    val index = universes.indexOf(universe)
    universes.removeAt(index)
    if (EditorState.selectedUniverse === universe) {
        EditorState.selectedUniverse = universes[minOf(index, universes.size - 1)]
    }
}
