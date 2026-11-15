package durhack26.music

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent

/*
 * Browser plumbing only. This file finds the canvas, keeps it the size of the window,
 * runs the frame loop, and forwards every input event to the editor (editor.kt).
 * It makes no decisions about what to draw or what input means.
 */
/** Finds the `<canvas id="grid">`, sizes it, connects input, and starts the frame loop. */
fun setupCanvas() {
    val canvas = document.getElementById("grid") as HTMLCanvasElement
    CanvasState.canvas = canvas
    CanvasState.ctx = canvas.getContext("2d") as CanvasRenderingContext2D

    fitToWindow()
    window.addEventListener("resize", { fitToWindow() })

    canvas.addEventListener("pointerdown", { e ->
        e as PointerEvent
        // Keep receiving moves/up for this pointer even if it leaves the canvas.
        canvas.setPointerCapture(e.pointerId)
        onPointerDown(e)
    })
    canvas.addEventListener("pointermove", { onPointerMove(it as PointerEvent) })
    canvas.addEventListener("pointerup", { onPointerUp(it as PointerEvent) })
    canvas.addEventListener("pointercancel", { onPointerUp(it as PointerEvent) })
    canvas.addEventListener("pointerleave", { onPointerLeave(it as PointerEvent) })
    canvas.addEventListener("wheel", { e ->
        e.preventDefault() // stop the page from scrolling / pinch-zooming
        onWheel(e as WheelEvent)
    })
    window.addEventListener("keydown", { onKeyDown(it as KeyboardEvent) })
    window.addEventListener("keyup", { onKeyUp(it as KeyboardEvent) })
    // Right-click belongs to the editor: stop the browser's context menu.
    canvas.addEventListener("contextmenu", Event::preventDefault)

    window.requestAnimationFrame(::frame)
}

private fun frame(@Suppress("UNUSED_PARAMETER") time: Double) {
    val ctx = CanvasState.ctx
    if (ctx != null) {
        // Draw in CSS pixels; the canvas buffer itself is dpr times larger.
        ctx.setTransform(CanvasState.dpr, 0.0, 0.0, CanvasState.dpr, 0.0, 0.0)
        ctx.clearRect(0.0, 0.0, CanvasState.width, CanvasState.height)
        render(ctx)
    }
    window.requestAnimationFrame(::frame)
}

/** Makes the canvas exactly fill the window, then tells the editor the new size. */
private fun fitToWindow() {
    val canvas = CanvasState.canvas ?: return
    CanvasState.dpr = window.devicePixelRatio
    CanvasState.width = window.innerWidth.toDouble()
    CanvasState.height = window.innerHeight.toDouble()

    canvas.style.width = "${CanvasState.width}px"
    canvas.style.height = "${CanvasState.height}px"
    canvas.width = (CanvasState.width * CanvasState.dpr).toInt()
    canvas.height = (CanvasState.height * CanvasState.dpr).toInt()

    onResize(CanvasState.width, CanvasState.height)
}
