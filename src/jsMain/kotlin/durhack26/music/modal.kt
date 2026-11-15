package durhack26.music

import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent

/*
 * A dialog over the editor, for things that need real text, lists and checkboxes (the help,
 * picking presets from a bank). It's plain HTML on top of the canvas, styled like the rest
 * of the app. Clicking outside it, or Escape, closes it. While one is open, the editor's
 * keyboard shortcuts are off (see onKeyDown), and the canvas gets no pointer events.
 */

private const val PANEL_COLOR = ZINC_900
private const val TEXT_COLOR = ZINC_300
private const val TITLE_COLOR = ZINC_100
private const val BACKDROP_COLOR = "rgba(0, 0, 0, 0.55)"
private const val FONT = "13px/1.5 system-ui, -apple-system, sans-serif"

/** The open dialog's backdrop, or null. */
private var openBackdrop: HTMLDivElement? = null
private var onClose: (() -> Unit)? = null

fun isModalOpen() = openBackdrop != null

/**
 * Opens a dialog titled [title], [width] px wide at most, replacing any already open, and
 * returns its body to fill in. [closed] runs when it closes, however that happens.
 */
fun openModal(title: String, width: Int = 520, closed: () -> Unit = {}): HTMLDivElement {
    closeModal()
    val backdrop = element<HTMLDivElement>("div") {
        position = "fixed"
        setProperty("inset", "0")
        background = BACKDROP_COLOR
        display = "flex"
        alignItems = "center"
        justifyContent = "center"
        zIndex = "10"
    }
    val panel = element<HTMLDivElement>("div") {
        background = PANEL_COLOR
        color = TEXT_COLOR
        font = FONT
        borderRadius = "10px"
        padding = "16px 20px 20px"
        boxSizing = "border-box"
        this.width = "min(${width}px, calc(100vw - 32px))"
        maxHeight = "calc(100vh - 64px)"
        display = "flex"
        flexDirection = "column"
        boxShadow = "0 12px 40px rgba(0, 0, 0, 0.5)"
    }
    panel.setAttribute("role", "dialog")
    panel.setAttribute("aria-label", title)
    val header = element<HTMLDivElement>("div") {
        display = "flex"
        alignItems = "center"
        marginBottom = "12px"
    }
    header.appendChild(element<HTMLElement>("div") {
        color = TITLE_COLOR
        fontSize = "16px"
        fontWeight = "600"
        flexGrow = "1"
    }.also { it.textContent = title })
    header.appendChild(button("Close", primary = false) { closeModal() })
    val body = element<HTMLDivElement>("div") {
        overflowY = "auto"
        minHeight = "0"
        display = "flex"
        flexDirection = "column"
    }
    panel.appendChild(header)
    panel.appendChild(body)
    backdrop.appendChild(panel)

    // Clicking the backdrop (not the dialog) closes it.
    backdrop.onmousedown = { e -> if (e.target === backdrop) closeModal() }
    backdrop.onkeydown = { e -> if ((e as KeyboardEvent).key == "Escape") closeModal() }
    backdrop.tabIndex = -1
    openBackdrop = backdrop
    onClose = closed
    document.body?.appendChild(backdrop)
    backdrop.focus()
    return body
}

fun closeModal() {
    val backdrop = openBackdrop ?: return
    openBackdrop = null
    backdrop.remove()
    onClose?.also { onClose = null }?.invoke()
}

/** A new [tag] element with [style] applied. */
fun <T : HTMLElement> element(tag: String, style: org.w3c.dom.css.CSSStyleDeclaration.() -> Unit = {}): T {
    @Suppress("UNCHECKED_CAST")
    val e = document.createElement(tag) as T
    e.style.style()
    return e
}

/** A button styled like the app's: [primary] ones stand out. */
fun button(label: String, primary: Boolean = true, onClick: () -> Unit) = element<HTMLElement>("button") {
    font = FONT
    color = if (primary) WHITE else ZINC_300
    background = if (primary) VIOLET_600 else ZINC_800
    border = "none"
    borderRadius = "6px"
    padding = "6px 14px"
    cursor = "pointer"
}.also {
    it.textContent = label
    it.setAttribute("type", "button")
    it.onclick = { onClick() }
}
