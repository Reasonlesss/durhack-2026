package durhack26.music

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.js.json

/*
 * Saving the whole song to a file, and opening one again.
 *
 * A song file is JSON: the tempo, mood and key, then every universe with its sidebar
 * instruments, notes, edges and effects. Edges refer to notes by their index in the
 * universe's list. For example:
 *
 *   {
 *     "format": "durhack-music", "version": 1,
 *     "bpm": 124, "scale": "SAD", "key": 0, "selectedUniverse": 0,
 *     "universes": [{
 *       "name": "Drums", "enabled": true, "cameraX": 0, "cameraY": -400,
 *       "instrumentTypes": [{ "shape": "CIRCLE", "type": "DRUMS" }],
 *       "instruments": [{ "type": "DRUMS", "shape": "CIRCLE", "x": 200, "y": -336 }, …],
 *       "edges": [{ "from": 0, "to": 1, "segments": 2 }, …],
 *       "effects": [{ "type": "REVERB", "x": 612, "y": -768, "rotation": 0, "scale": 12 }]
 *     }, …]
 *   }
 *
 * Opening a file checks all of it before anything changes, so a bad file leaves the
 * current song as it was.
 */

private const val FORMAT = "durhack-music"
private const val VERSION = 1
private const val MIME_TYPE = "application/json"
private const val EXTENSION = ".json"

/** The name the last opened or saved file had, offered again when saving. */
private var fileName = "song$EXTENSION"

// --- Saving --------------------------------------------------------------------------------

/** The whole song as song-file JSON. */
fun songToJson(): String {
    val song = json(
        "format" to FORMAT,
        "version" to VERSION,
        "bpm" to EditorState.bpm,
        "scale" to EditorState.scale.name,
        "key" to EditorState.key,
        "selectedUniverse" to EditorState.universes.indexOf(EditorState.selectedUniverse),
        "universes" to EditorState.universes.map(::universeToJson).toTypedArray(),
    )
    return js("JSON.stringify(song, null, 2)") as String
}

private fun universeToJson(universe: Universe) = json(
    "name" to universe.name,
    "enabled" to universe.enabled,
    "cameraX" to universe.cameraX,
    "cameraY" to universe.cameraY,
    "instrumentTypes" to universe.instrumentTypes.map { (shape, type) ->
        json("shape" to shape.name, "type" to type.name)
    }.toTypedArray(),
    "instruments" to universe.instruments.map {
        json("type" to it.type.name, "shape" to it.shape.name, "x" to it.x, "y" to it.y)
    }.toTypedArray(),
    "edges" to universe.edges.map {
        json(
            "from" to universe.instruments.indexOf(it.from),
            "to" to universe.instruments.indexOf(it.to),
            "segments" to it.segments,
        )
    }.toTypedArray(),
    "effects" to universe.effects.map {
        json("type" to it.type.name, "x" to it.x, "y" to it.y, "rotation" to it.rotation, "scale" to it.scale)
    }.toTypedArray(),
)

/**
 * Saves the song. Where the browser can (Chrome, Edge), this asks where to save it;
 * elsewhere it's downloaded. Must be called from a click or key press.
 */
fun saveSong() {
    val text = songToJson()
    val picker = window.asDynamic().showSaveFilePicker
    if (picker == null) {
        download(text)
        return
    }
    val accept: dynamic = js("({})")
    accept[MIME_TYPE] = arrayOf(EXTENSION)
    val options = json(
        "suggestedName" to fileName,
        "types" to arrayOf(json("description" to "Song", "accept" to accept)),
    )
    window.asDynamic().showSaveFilePicker(options).then(
        { handle: dynamic -> writeTo(handle, text) },
        { error: dynamic ->
            // Cancelling the dialog isn't a failure. If the dialog couldn't open at all, download
            // instead: this runs straight after the click, so the browser still allows it.
            if (error?.name != "AbortError") download(text)
        },
    )
}

/** Writes [text] to the file [handle] the save dialog chose, and remembers its name once it's saved. */
private fun writeTo(handle: dynamic, text: String) {
    val name = handle.name as String
    handle.createWritable()
        .then { writable: dynamic -> writable.write(text).then { writable.close() } }
        .then(
            { _: dynamic -> fileName = name },
            { _: dynamic -> window.alert("Couldn't save \"$name\". Try saving it somewhere else.") },
        )
}

/** Saves [text] by downloading it as [fileName]. */
private fun download(text: String) {
    val url = URL.createObjectURL(Blob(arrayOf(text), BlobPropertyBag(type = MIME_TYPE)))
    val link = document.createElement("a") as HTMLAnchorElement
    link.href = url
    link.download = fileName
    link.click()
    window.setTimeout({ URL.revokeObjectURL(url) }, 1000)
}

// --- Opening -------------------------------------------------------------------------------

/** Asks for a song file and opens it, replacing the current song. Must be called from a click or key press. */
fun openSong() {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = "$EXTENSION,$MIME_TYPE"
    // Some browsers (iOS Safari) only report the chosen file for an input that's on the page.
    input.style.display = "none"
    document.body?.appendChild(input)
    input.onchange = {
        input.remove()
        val file = input.files?.item(0)
        if (file != null) {
            file.asDynamic().text().then(
                { text: String ->
                    try {
                        loadSong(text)
                        fileName = file.name
                    } catch (e: Throwable) {
                        val reason = (e as? SongFileError)?.message ?: "it couldn't be read"
                        window.alert("Couldn't open \"${file.name}\": $reason.")
                    }
                },
                { _: dynamic -> window.alert("Couldn't open \"${file.name}\": it couldn't be read.") },
            )
        }
    }
    // Cancelling the picker fires no event, so the input is also tidied away on the next Open.
    document.querySelectorAll("input[data-song-picker]").asList().forEach { (it as HTMLInputElement).remove() }
    input.setAttribute("data-song-picker", "")
    input.click()
}

/** Why a song file couldn't be opened, in words for the user. */
class SongFileError(message: String) : Exception(message)

/**
 * Replaces the current song with the one in [text], a song file. Stops playback first.
 * Throws [SongFileError] (and changes nothing) if the file isn't a valid song.
 */
fun loadSong(text: String) {
    val song: dynamic = try {
        JSON.parse<dynamic>(text)
    } catch (e: Throwable) {
        throw SongFileError("it isn't a song file")
    }
    if (jsTypeOf(song) != "object" || song == null || song.format != FORMAT) {
        throw SongFileError("it isn't a song file")
    }
    if (int(song, "version") > VERSION) throw SongFileError("it was saved by a newer version of the app")

    val bpm = int(song, "bpm").coerceIn(MIN_BPM, MAX_BPM)
    val scale = enumField<Scale>(song, "scale")
    val key = int(song, "key")
    if (key !in 0 until KEY_COUNT) throw SongFileError("its key is out of range")
    val loaded = list(song, "universes").map(::universeFromJson)
    if (loaded.isEmpty()) throw SongFileError("it has no universes")
    val selected = int(song, "selectedUniverse")

    // Everything checked out: swap it in.
    if (EditorState.playing) togglePlayback()
    EditorState.scale = scale
    EditorState.key = key
    setBpm(bpm.toDouble())
    EditorState.universes.clear()
    EditorState.universes += loaded.map { it.first }
    EditorState.selectedUniverse = EditorState.universes.getOrElse(selected) { EditorState.universes.first() }
    // Scroll each universe now the scale (and so the note range its camera is kept within) is set.
    for ((universe, camera) in loaded) universe.scrollTo(camera.first, camera.second)
    resetEditing()
}

/** Forgets anything mid-edit: drags, selections, open dropdowns. They'd point at the old song. */
private fun resetEditing() {
    EditorState.dragging = null
    EditorState.draggingFromToolbar = false
    EditorState.linkingFrom = null
    EditorState.transforming = null
    EditorState.selectedEffect = null
    EditorState.panning = false
    EditorState.previewLevel = null
    EditorState.scaleMenuOpen = false
    EditorState.instrumentMenuOpen = false
    EditorState.editingShape = null
    resetTapTempo()
}

/** A universe from a song file, and the (x, y) to scroll it to once the scale is set. */
private fun universeFromJson(json: dynamic): Pair<Universe, Pair<Double, Double>> {
    val universe = Universe(string(json, "name"), boolean(json, "enabled"))

    for (entry in list(json, "instrumentTypes")) {
        universe.instrumentTypes[enumField<InstrumentShape>(entry, "shape")] = enumField<InstrumentType>(entry, "type")
    }
    for (entry in list(json, "instruments")) {
        val shape = enumField<InstrumentShape>(entry, "shape")
        // Every note's shape has to be in the sidebar, or it couldn't be changed or removed;
        // and every note plays its shape's sidebar type, as the editor keeps it.
        val type = universe.instrumentTypes.getOrPut(shape) { enumField<InstrumentType>(entry, "type") }
        universe.instruments += Instrument(type, shape, number(entry, "x"), number(entry, "y"))
    }
    for (entry in list(json, "edges")) {
        val from = universe.instruments.getOrNull(int(entry, "from"))
        val to = universe.instruments.getOrNull(int(entry, "to"))
        if (from == null || to == null || from === to) throw SongFileError("it has an edge between notes that don't exist")
        // The editor allows one edge between two notes, one way round (see Universe.connect).
        if (universe.edges.any { (it.from === from && it.to === to) || (it.from === to && it.to === from) }) {
            throw SongFileError("it has two edges between the same notes")
        }
        val segments = int(entry, "segments")
        if (segments !in SEGMENT_OPTIONS) throw SongFileError("it has an edge with an unknown delay")
        universe.edges += Edge(from, to, segments)
    }
    for (entry in list(json, "effects")) {
        val scale = number(entry, "scale")
        if (scale <= 0) throw SongFileError("it has an effect with no size")
        universe.effects += Effect(
            x = number(entry, "x"),
            y = number(entry, "y"),
            type = enumField<EffectType>(entry, "type"),
            rotation = number(entry, "rotation"),
            scale = scale,
        )
    }
    return universe to (number(json, "cameraX") to number(json, "cameraY"))
}

// --- Reading fields, with errors that say what's wrong --------------------------------------

private fun field(obj: dynamic, name: String): dynamic {
    if (jsTypeOf(obj) != "object" || obj == null) throw SongFileError("part of it is missing")
    val value = obj[name]
    if (value == null) throw SongFileError("it's missing \"$name\"")
    return value
}

private fun number(obj: dynamic, name: String): Double {
    val value = field(obj, name)
    if (jsTypeOf(value) != "number" || !(value as Double).isFinite()) throw SongFileError("\"$name\" isn't a number")
    return value
}

private fun int(obj: dynamic, name: String): Int {
    val value = number(obj, name)
    if (value % 1.0 != 0.0) throw SongFileError("\"$name\" isn't a whole number")
    return value.toInt()
}

private fun string(obj: dynamic, name: String): String {
    val value = field(obj, name)
    if (jsTypeOf(value) != "string") throw SongFileError("\"$name\" isn't text")
    return value as String
}

private fun boolean(obj: dynamic, name: String): Boolean {
    val value = field(obj, name)
    if (jsTypeOf(value) != "boolean") throw SongFileError("\"$name\" isn't true or false")
    return value as Boolean
}

private fun list(obj: dynamic, name: String): List<dynamic> {
    val value = field(obj, name)
    if (!(js("Array").isArray(value) as Boolean)) throw SongFileError("\"$name\" isn't a list")
    return (value as Array<dynamic>).toList()
}

private inline fun <reified T : Enum<T>> enumField(obj: dynamic, name: String): T {
    val value = string(obj, name)
    return enumValues<T>().firstOrNull { it.name == value } ?: throw SongFileError("it has an unknown $name \"$value\"")
}
