package durhack26.music

import org.w3c.dom.HTMLElement

/*
 * The help: how to make music with the editor, and every shortcut. Opened from the Help
 * button by Open / Save, or with ?. Keep it in step with editor.kt when controls change.
 */

private const val HEADING_COLOR = ZINC_100
private const val KEY_COLOR = ZINC_100
private const val KEY_BACKGROUND = ZINC_800
private const val MUTED_COLOR = ZINC_400

/** A section of the help: a heading, then rows of what to do and what it does. */
private class HelpSection(val title: String, val rows: List<Pair<String, String>>)

/*
 * Text in [brackets] is drawn as a key (e.g. [Space]), and [Ctrl] reads ⌘ on a Mac.
 */
private val sections = listOf(
    HelpSection(
        "The idea",
        listOf(
            "Notes" to "Each shape on the canvas is a note. Higher up plays a higher pitch; the shape is its instrument.",
            "Arrows" to "An arrow plays the next note a little after the one it comes from. Notes with no arrow into them play first, on the beat.",
            "Universes" to "The tabs at the top left are separate canvases that all play together, e.g. drums in one and chords in another.",
        ),
    ),
    HelpSection(
        "Notes",
        listOf(
            "Drag an instrument from the sidebar" to "Places a note where you drop it. You hear it as it crosses each pitch.",
            "Drag a note" to "Moves it, changing its pitch and timing.",
            "Hold a note and scroll" to "Changes its volume, in 5% steps. Quieter notes are fainter.",
            "Right-click a note" to "Deletes it, with its arrows.",
            "Click an instrument in the sidebar" to "Switches every note of that shape to another instrument, or deletes them all. + adds an instrument.",
        ),
    ),
    HelpSection(
        "Arrows",
        listOf(
            "Drag from just outside a note" to "Draws an arrow from it. Drop it on another note to link them, or on empty space to make a new note there.",
            "Draw the same arrow again" to "Removes it.",
            "Click an arrow" to "Changes how long it waits: 1, 1/2, 1/3, 1/4, 1/6 or 1/8 of a beat.",
            "Right-click an arrow" to "Deletes it.",
        ),
    ),
    HelpSection(
        "Effects",
        listOf(
            "Drag an effect from the sidebar" to "Places it. It changes every note inside it; overlapping effects stack.",
            "Click an effect" to "Shows its handles.",
            "Drag the corner handle" to "Resizes it, to cover more or fewer notes. Size doesn't change how it sounds.",
            "Drag the top handle" to "Turns it, which changes its setting (e.g. Reverb's room size), shown by the handle.",
            "Right-click an effect" to "Deletes it.",
        ),
    ),
    HelpSection(
        "Playing",
        listOf(
            "[Space] or ▶" to "Plays or stops.",
            "Click the BPM" to "Type a tempo (60–200), then [Enter]. [Esc] cancels.",
            "− / + or scroll over the tempo" to "Changes the tempo by 1 BPM.",
            "[T] or Tap" to "Sets the tempo from how fast you tap.",
            "The mood button" to "Picks the scale notes snap to (Happy, Sad…), and under Key, its key, or No key for every note.",
            "Click a tab's toggle" to "Mutes or unmutes that universe. + adds one, × closes one.",
            "Double-click a tab's name" to "Renames it. [Enter] keeps the new name, [Esc] cancels.",
        ),
    ),
    HelpSection(
        "Moving around",
        listOf(
            "Drag empty space" to "Scrolls the canvas.",
            "Scroll" to "Scrolls the canvas.",
        ),
    ),
    HelpSection(
        "Files",
        listOf(
            "[Ctrl] [S] or Save" to "Saves the song to a file.",
            "[Ctrl] [O] or Open" to "Opens a saved song.",
            "Advanced (bottom left)" to "Import preset: Sylenth1 or Serum (1) presets (.fxp), or Sylenth1 banks (.fxb). Import sample: an audio file, played on C4. Imports are saved in the song.",
            "[?]" to "Shows this help.",
        ),
    ),
)

/** A [Key] in help text. */
private val KEY_PATTERN = Regex("""\[([^\]]+)\]""")

private val isMac = js("/Mac|iPhone|iPad/.test(navigator.platform)") as Boolean

fun showHelp() {
    val body = openModal("How to use it", width = 640)
    for (section in sections) {
        body.appendChild(element<HTMLElement>("div") {
            color = HEADING_COLOR
            fontWeight = "600"
            margin = "14px 0 6px"
        }.also { it.textContent = section.title })
        val table = element<HTMLElement>("div") {
            display = "grid"
            setProperty("grid-template-columns", "minmax(150px, 36%) 1fr")
            setProperty("gap", "6px 16px")
        }
        for ((action, effect) in section.rows) {
            table.appendChild(actionCell(action))
            table.appendChild(actionCell(effect).also { it.style.color = MUTED_COLOR })
        }
        body.appendChild(table)
    }
}

/** [text], with each [Key] drawn as a key cap. */
private fun actionCell(text: String): HTMLElement {
    val cell = element<HTMLElement>("div")
    val parts = KEY_PATTERN.split(text)
    val keys = KEY_PATTERN.findAll(text).map { it.groupValues[1] }.toList()
    parts.forEachIndexed { i, part ->
        if (part.isNotEmpty()) cell.appendChild(element<HTMLElement>("span").also { it.textContent = part })
        keys.getOrNull(i)?.let { key ->
            cell.appendChild(element<HTMLElement>("kbd") {
                font = "12px system-ui, -apple-system, sans-serif"
                color = KEY_COLOR
                background = KEY_BACKGROUND
                borderRadius = "4px"
                padding = "1px 6px"
                margin = "0 2px"
                setProperty("box-shadow", "inset 0 -1px 0 rgba(255, 255, 255, 0.08)")
            }.also { it.textContent = if (key == "Ctrl" && isMac) "⌘" else key })
        }
    }
    return cell
}
