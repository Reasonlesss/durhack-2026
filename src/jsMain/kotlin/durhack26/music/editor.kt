package durhack26.music

import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent

fun render(ctx: CanvasRenderingContext2D) {
    ctx.fillStyle = ZINC_800
    ctx.fillRect(0.0, 0.0, CanvasState.width, CanvasState.height)

    renderGrid(ctx, EditorState.selectedUniverse)
    renderEffects(ctx, EditorState.selectedUniverse)
    renderEdges(ctx, EditorState.selectedUniverse)
    renderInstruments(ctx, EditorState.selectedUniverse)
    renderEffectHandles(ctx, EditorState.selectedUniverse)
    renderUniverseTabs(ctx)
    renderPlayButton(ctx)
    renderScalePicker(ctx)
    renderTempo(ctx)
    renderToolbar(ctx)
    renderInstrumentMenu(ctx)
    renderScaleMenu(ctx)
}

fun onResize(width: Double, height: Double) {
}

fun onPointerDown(e: PointerEvent) {
    EditorState.pointerX = e.offsetX
    EditorState.pointerY = e.offsetY
    unlockAudio()
    try {
        handlePointerDown(e)
    } finally {
        // Whatever is being placed now starts at its current level: no preview until it moves.
        EditorState.previewLevel = EditorState.placingNote()?.second
    }
}

private fun handlePointerDown(e: PointerEvent) {
    if (e.button.toInt() == 0) {
        if (EditorState.scaleMenuOpen) {
            // Picks a scale or key if one was clicked; any click outside just closes the dropdown.
            if (!processScalePickerClick(e.offsetX, e.offsetY)) EditorState.scaleMenuOpen = false
            return
        }
        if (processInstrumentMenuClick(e.offsetX, e.offsetY)) return
        val menuShown = isInstrumentMenuShown()
        EditorState.instrumentMenuOpen = false
        // A click outside the open dropdown only closes it.
        if (menuShown) return
        if (processScalePickerClick(e.offsetX, e.offsetY)) return
        if (processTempoClick(e.offsetX, e.offsetY)) return
        if (processPlayButtonClick(e.offsetX, e.offsetY)) return
        if (processTabClick(e.offsetX, e.offsetY)) return
        if (processToolbarClick(e.offsetX, e.offsetY)) {
            EditorState.selectedEffect = EditorState.dragging as? Effect
            return
        }

        val universe = EditorState.selectedUniverse
        val worldX = e.offsetX + universe.cameraX
        val worldY = e.offsetY + universe.cameraY

        val handle = EditorState.selectedEffect?.handleAt(worldX, worldY)
        if (handle != null) {
            EditorState.transforming = handle
            return
        }

        val hit = universe.movableAt(worldX, worldY)
        EditorState.selectedEffect = hit as? Effect
        // Pressing in the outline just around an instrument (not on it) starts an edge.
        val near = universe.instrumentNear(worldX, worldY)
        if (hit !is Instrument && near != null) {
            EditorState.linkingFrom = near
            return
        }
        if (hit is Instrument) {
            EditorState.dragging = hit
            return
        }
        // Clicking an edge steps its delay through SEGMENT_OPTIONS.
        val edge = universe.edgeAt(worldX, worldY)
        if (edge != null) {
            EditorState.selectedEffect = null
            edge.cycleSegments()
            universe.markChanged()
            return
        }
        if (hit != null) {
            EditorState.dragging = hit
            return
        }
    }

    if (e.button.toInt() == 2) {
        // Right-clicking an instrument or effect deletes it.
        val universe = EditorState.selectedUniverse
        when (val hit = universe.movableAt(e.offsetX + universe.cameraX, e.offsetY + universe.cameraY)) {
            is Instrument -> { universe.removeInstrument(hit); return }
            is Effect -> { universe.effects.remove(hit); universe.markChanged(); return }
            else -> {}
        }
    }

    // Nothing else was clicked: deselect and drag the background to pan.
    if (e.button.toInt() == 0) EditorState.selectedEffect = null
    EditorState.panning = true
}

fun onPointerMove(e: PointerEvent) {
    // Moving or reshaping something on the canvas changes what plays.
    if (EditorState.dragging != null || EditorState.transforming != null) {
        EditorState.selectedUniverse.markChanged()
    }
    val dragging = EditorState.dragging
    val handle = EditorState.transforming
    val effect = EditorState.selectedEffect
    if (handle != null && effect != null) {
        val universe = EditorState.selectedUniverse
        effect.dragHandle(handle, e.offsetX + universe.cameraX, e.offsetY + universe.cameraY)
    } else if (dragging != null) {
        dragging.x += e.offsetX - EditorState.pointerX
        dragging.y += e.offsetY - EditorState.pointerY
    } else if (EditorState.panning) {
        // Dragging right moves the view left, so the content follows the pointer.
        EditorState.selectedUniverse.panBy(EditorState.pointerX - e.offsetX, EditorState.pointerY - e.offsetY)
    }
    EditorState.pointerX = e.offsetX
    EditorState.pointerY = e.offsetY
    previewOnLevelChange()
}

/** Plays the note being placed whenever it crosses onto a different level. */
private fun previewOnLevelChange() {
    val placing = EditorState.placingNote()
    val level = placing?.second
    if (placing != null && level != EditorState.previewLevel) previewNote(placing.first, placing.second)
    EditorState.previewLevel = level
}

/** The pointer left the canvas (e.g. moved out of the window). */
fun onPointerLeave(e: PointerEvent) {
    EditorState.pointerX = -1.0
    EditorState.pointerY = -1.0
}

fun onPointerUp(e: PointerEvent) {
    EditorState.panning = false
    processToolbarDrop(e.offsetX, e.offsetY)

    val linkingFrom = EditorState.linkingFrom
    if (linkingFrom != null) {
        val universe = EditorState.selectedUniverse
        val worldX = e.offsetX + universe.cameraX
        val worldY = e.offsetY + universe.cameraY
        val target = universe.instrumentNear(worldX, worldY)
        if (target != null) {
            universe.connect(linkingFrom, target)
        } else if (!isOverUi(e.offsetX, e.offsetY)) {
            // Released on empty canvas: make a new instrument of the same type there, linked to.
            val copy = Instrument(linkingFrom.type, linkingFrom.shape, worldX, worldY)
            universe.instruments += copy
            universe.connect(linkingFrom, copy)
        }
        EditorState.linkingFrom = null
    }
    EditorState.dragging = null
    EditorState.transforming = null
    EditorState.previewLevel = null
    // Covers anything a click added, removed or changed (edges, effects, instrument types).
    EditorState.selectedUniverse.markChanged()
}

/** Whether screen point (x, y) is over the sidebar, a dropdown, the play button, the scale picker or the tempo control. */
private fun isOverUi(x: Double, y: Double) =
    isOverToolbar(x, y) || isOverInstrumentMenu(x, y) || isOverPlayButton(x, y) || isOverScalePicker(x, y) ||
        isOverTempo(x, y)

fun onWheel(e: WheelEvent) {
    // Line-based wheels (some mice) report lines rather than pixels.
    val unit = if (e.deltaMode == WheelEvent.DOM_DELTA_LINE) 16.0 else 1.0
    var dx = e.deltaX * unit
    var dy = e.deltaY * unit
    // Shift + wheel scrolls sideways on mice without horizontal scrolling.
    if (e.shiftKey && dx == 0.0) { dx = dy; dy = 0.0 }
    // Over the tempo, the wheel changes it instead of scrolling.
    if (processTempoWheel(e.offsetX, e.offsetY, dy)) return
    EditorState.selectedUniverse.panBy(dx, dy)
}

fun onKeyDown(e: KeyboardEvent) {
    // Space plays / stops.
    if (e.key == " " && !e.repeat) {
        e.preventDefault()
        togglePlayback()
    }
    // T taps the tempo.
    if ((e.key == "t" || e.key == "T") && !e.repeat && !e.ctrlKey && !e.metaKey && !e.altKey) tapTempo()
}

fun onKeyUp(e: KeyboardEvent) {
}
