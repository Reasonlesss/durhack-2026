package durhack26.music

import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.math.roundToInt

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
    renderFileButtons(ctx)
    placeRenameInput()
    renderAdvanced(ctx)
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
        if (processFileButtonsPress(e.offsetX, e.offsetY)) return
        if (processAdvancedPress(e.offsetX, e.offsetY)) return
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
        // Right-clicking an instrument, edge or effect deletes it. Checked in drawing order,
        // topmost first, so an edge running across an effect goes rather than the effect.
        val universe = EditorState.selectedUniverse
        val worldX = e.offsetX + universe.cameraX
        val worldY = e.offsetY + universe.cameraY
        val hit = universe.movableAt(worldX, worldY)
        if (hit is Instrument) {
            universe.removeInstrument(hit)
            return
        }
        val edge = universe.edgeAt(worldX, worldY)
        if (edge != null) {
            universe.removeEdge(edge)
            return
        }
        if (hit is Effect) {
            universe.effects.remove(hit)
            universe.markChanged()
            return
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
    if (placing != null && level != EditorState.previewLevel) previewNote(placing.first, placing.second, placingVolume())
    EditorState.previewLevel = level
}

/** How loud the note being placed is: the dragged one's volume, or (for a new one) its source's. */
private fun placingVolume() = (EditorState.dragging as? Instrument)?.volume ?: EditorState.linkingFrom?.volume ?: 1.0

/** The pointer left the canvas (e.g. moved out of the window). */
fun onPointerLeave(e: PointerEvent) {
    EditorState.pointerX = -1.0
    EditorState.pointerY = -1.0
}

fun onPointerUp(e: PointerEvent) {
    processFileButtonsRelease(e.offsetX, e.offsetY)
    processAdvancedRelease(e.offsetX, e.offsetY)
    processTempoRelease(e.offsetX, e.offsetY)
    processTabRelease(e.offsetX, e.offsetY)
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
            // Released on empty canvas: make a new instrument of the same type (and volume) there, linked to.
            val copy = Instrument(linkingFrom.type, linkingFrom.shape, worldX, worldY, linkingFrom.volume)
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

/** Whether screen point (x, y) is over any of the UI: sidebar, dropdowns, top bar, or Open / Save. */
private fun isOverUi(x: Double, y: Double) =
    isOverToolbar(x, y) || isOverInstrumentMenu(x, y) || isOverPlayButton(x, y) || isOverScalePicker(x, y) ||
        isOverTempo(x, y) || isOverFileButtons(x, y) || isOverAdvanced(x, y)

fun onWheel(e: WheelEvent) {
    // Line-based wheels (some mice) report lines rather than pixels.
    val unit = if (e.deltaMode == WheelEvent.DOM_DELTA_LINE) 16.0 else 1.0
    var dx = e.deltaX * unit
    var dy = e.deltaY * unit
    // Shift + wheel scrolls sideways on mice without horizontal scrolling.
    if (e.shiftKey && dx == 0.0) { dx = dy; dy = 0.0 }
    // Over the tempo, scrolling up or down changes it instead. Not sideways scrolling, and
    // not ctrl + wheel, which is how trackpads report a pinch.
    if (!e.ctrlKey && dy != 0.0 && processTempoWheel(e.offsetX, e.offsetY, dy)) return
    // Holding a note down and scrolling changes its volume instead.
    val held = EditorState.dragging as? Instrument
    if (held != null) {
        if (dy != 0.0) changeVolume(held, dy)
        return
    }
    volumeWheelRemainder = 0.0
    EditorState.selectedUniverse.panBy(dx, dy)
}

/** How far the wheel has to scroll, in pixels, to change a note's volume by one step (5%). */
private const val VOLUME_WHEEL_STEP = 40.0
private const val VOLUME_STEPS = 20  // from silent to full

/** At most one preview this often while scrolling a note's volume, in ms, so it doesn't machine-gun. */
private const val VOLUME_PREVIEW_MS = 120.0

/** Wheel distance not yet turned into a whole volume step. */
private var volumeWheelRemainder = 0.0
private var lastVolumePreviewAt = Double.NEGATIVE_INFINITY

/** Scrolling up makes [note] louder, down quieter, in steps; it plays at the new volume so you can hear it. */
private fun changeVolume(note: Instrument, dy: Double) {
    volumeWheelRemainder -= dy
    val steps = (volumeWheelRemainder / VOLUME_WHEEL_STEP).toInt()  // toward zero, so the rest carries over
    if (steps == 0) return
    volumeWheelRemainder -= steps * VOLUME_WHEEL_STEP
    val before = note.volume
    // Snapped to whole steps, so it always lands on round percentages.
    note.volume = ((note.volume * VOLUME_STEPS).roundToInt() + steps).toDouble() / VOLUME_STEPS
    if (note.volume == before) return
    EditorState.selectedUniverse.markChanged()
    val now = window.performance.now()
    if (now - lastVolumePreviewAt >= VOLUME_PREVIEW_MS) {
        lastVolumePreviewAt = now
        previewNote(note.type, note.level, note.volume)
    }
}

fun onKeyDown(e: KeyboardEvent) {
    // Typing in a text box (e.g. the tempo) isn't a shortcut, and nor is anything while a
    // dialog (help, a bank's presets) is open: it handles its own keys.
    if (e.target is HTMLInputElement || e.target is HTMLTextAreaElement || isModalOpen()) return
    // ? opens the help.
    if (e.key == "?" && !e.ctrlKey && !e.metaKey) {
        e.preventDefault()
        showHelp()
        return
    }
    // Space plays / stops.
    if (e.key == " " && !e.repeat) {
        e.preventDefault()
        togglePlayback()
    }
    // Ctrl+S saves, Ctrl+O opens (⌘ on a Mac), instead of the browser's own.
    if ((e.ctrlKey || e.metaKey) && !e.altKey && !e.repeat) {
        when (e.key.lowercase()) {
            "s" -> { e.preventDefault(); saveSong(); return }
            "o" -> { e.preventDefault(); openSong(); return }
        }
    }
    // T taps the tempo.
    if ((e.key == "t" || e.key == "T") && !e.repeat && !e.ctrlKey && !e.metaKey && !e.altKey) tapTempo()
}

fun onKeyUp(e: KeyboardEvent) {
}
