package durhack26.music

import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent

/*
 * Picking presets from a Sylenth1 bank (.fxb) to import. Banks often hold 128 or more, far
 * more than the sidebar or the instrument dropdown could show, so a dialog lists them all
 * with a search box and a tick box each, and only the ticked ones are imported. Presets
 * that can't be imported (e.g. every oscillator off) are listed greyed out, saying why.
 */

private const val NAME_COLOR = ZINC_200
private const val MUTED_COLOR = ZINC_500
private const val ROW_HOVER_COLOR = ZINC_800
private const val INPUT_COLOR = ZINC_800

/**
 * Shows the presets in the bank [bankName] and calls [onImport] with the ones ticked, in
 * bank order, when Import is clicked. Each entry is a name and the preset (or why it can't
 * be imported).
 */
fun showBankPicker(bankName: String, presets: List<Pair<String, Result<PresetSound>>>, onImport: (List<PresetSound>) -> Unit) {
    val usable = presets.count { it.second.isSuccess }
    val body = openModal("$bankName: ${presets.size} preset${if (presets.size == 1) "" else "s"}")

    body.appendChild(element<HTMLElement>("div") {
        color = MUTED_COLOR
        marginBottom = "10px"
    }.also {
        it.textContent = "Tick the presets to import. Each becomes an instrument, rebuilt from its main settings, so it " +
            "won't sound exactly like Sylenth1." + if (usable < presets.size) " Greyed-out ones can't be imported." else ""
    })

    val search = element<HTMLInputElement>("input") {
        font = "inherit"
        color = NAME_COLOR
        background = INPUT_COLOR
        border = "none"
        borderRadius = "6px"
        padding = "7px 10px"
        marginBottom = "8px"
        outline = "none"
    }
    search.type = "search"
    search.placeholder = "Search presets"
    body.appendChild(search)

    val list = element<HTMLDivElement>("div") {
        overflowY = "auto"
        minHeight = "120px"
        maxHeight = "50vh"
        marginBottom = "12px"
    }
    body.appendChild(list)

    class Entry(val row: HTMLElement, val box: HTMLInputElement?, val name: String, val preset: PresetSound?)
    val entries = presets.mapIndexed { i, (name, result) ->
        val preset = result.getOrNull()
        val row = element<HTMLElement>("label") {
            display = "flex"
            alignItems = "center"
            setProperty("gap", "10px")
            padding = "4px 8px"
            borderRadius = "6px"
            cursor = if (preset != null) "pointer" else "default"
            color = if (preset != null) NAME_COLOR else MUTED_COLOR
        }
        val box = if (preset == null) null else element<HTMLInputElement>("input") {
            setProperty("accent-color", VIOLET_500)
            margin = "0"
        }.also {
            it.type = "checkbox"
            row.appendChild(it)
        }
        if (box == null) row.appendChild(element<HTMLElement>("span") { width = "13px"; flexShrink = "0" })
        row.appendChild(element<HTMLElement>("span") { color = MUTED_COLOR; minWidth = "2.5em" }.also { it.textContent = "${i + 1}" })
        row.appendChild(element<HTMLElement>("span") { flexGrow = "1" }.also { it.textContent = name })
        result.exceptionOrNull()?.let { error ->
            row.appendChild(element<HTMLElement>("span") { fontSize = "12px" }.also { it.textContent = error.message ?: "can't be imported" })
        }
        if (preset != null) {
            row.onmouseenter = { row.style.background = ROW_HOVER_COLOR; null }
            row.onmouseleave = { row.style.background = "none"; null }
        }
        list.appendChild(row)
        Entry(row, box, name, preset)
    }

    val footer = element<HTMLDivElement>("div") {
        display = "flex"
        alignItems = "center"
        setProperty("gap", "8px")
    }
    val selectShown = element<HTMLElement>("label") {
        display = "flex"
        alignItems = "center"
        setProperty("gap", "8px")
        cursor = "pointer"
        flexGrow = "1"
    }
    val selectAll = element<HTMLInputElement>("input") { setProperty("accent-color", VIOLET_500); margin = "0" }
    selectAll.type = "checkbox"
    selectShown.appendChild(selectAll)
    val selectLabel = element<HTMLElement>("span")
    selectShown.appendChild(selectLabel)
    footer.appendChild(selectShown)
    footer.appendChild(button("Cancel", primary = false) { closeModal() })
    val importButton = button("Import") {}
    footer.appendChild(importButton)
    body.appendChild(footer)

    fun shown() = entries.filter { it.box != null && it.row.style.display != "none" }
    fun ticked() = entries.filter { it.box?.checked == true }
    fun refresh() {
        val count = ticked().size
        importButton.textContent = if (count == 0) "Import" else "Import $count"
        importButton.asDynamic().disabled = count == 0
        importButton.style.opacity = if (count == 0) "0.5" else "1"
        val visible = shown()
        selectLabel.textContent = if (search.value.isBlank()) "Select all" else "Select all ${visible.size} shown"
        selectAll.checked = visible.isNotEmpty() && visible.all { it.box!!.checked }
    }

    search.oninput = {
        val query = search.value.trim().lowercase()
        for (entry in entries) entry.row.style.display = if (query.isEmpty() || query in entry.name.lowercase()) "flex" else "none"
        refresh()
    }
    selectAll.onchange = {
        shown().forEach { it.box!!.checked = selectAll.checked }
        refresh()
    }
    entries.forEach { entry -> entry.box?.onchange = { refresh() } }
    fun import() {
        val picked = ticked().mapNotNull { it.preset }
        if (picked.isEmpty()) return
        closeModal()
        onImport(picked)
    }
    importButton.onclick = { import() }
    // Enter imports; Escape is handled by the dialog.
    body.onkeydown = { e -> if ((e as KeyboardEvent).key == "Enter") import() }

    refresh()
    search.focus()
}
