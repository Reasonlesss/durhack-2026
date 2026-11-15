package durhack26.music

import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement

object CanvasState {
    var canvas: HTMLCanvasElement? = null
    var ctx: CanvasRenderingContext2D? = null

    /** Window size in CSS pixels. */
    var width = 0.0
    var height = 0.0

    /** Physical pixels per CSS pixel (2.0 on most retina screens). */
    var dpr = 1.0
}

object EditorState {
    /**
     * The scale (mood) melodic instruments play in, for every universe. See levels.kt.
     * Declared first: new universes read it to place their camera.
     */
    var scale = Scale.HAPPY

    /**
     * The key every scale starts from, as semitones above C (0 = C, 9 = A). An advanced
     * option under the moods: "Sad" in A is A minor. See levels.kt.
     */
    var key = 0

    /**
     * True for "No key": every note of the chromatic scale from C, whatever the mood. Picking
     * a mood or a key turns it off again. See levels.kt.
     */
    var noKey = false

    /** Whether the Advanced box in the bottom-left corner is ticked: shows importing presets and samples. */
    var advanced = false

    /**
     * Presets and samples imported this session (see imports.kt), offered in the instrument
     * dropdown of every universe alongside the built-in types.
     */
    val importedSounds = mutableListOf<ImportedSound>()

    /** All open universes, in tab order. */
    val universes = mutableListOf(
        Universe("Universe 1"),
        Universe("Universe 2"),
        Universe("Universe 3"),
    )

    /** The universe currently shown. Always one of [universes]. */
    var selectedUniverse: Universe = universes.first()

    /** Last known pointer position in CSS pixels; negative when the pointer is off the canvas. */
    var pointerX = -1.0
    var pointerY = -1.0

    /** True while the user is dragging the editor background to pan. */
    var panning = false

    /** The instrument or effect being dragged, if any. */
    var dragging: Movable? = null

    /** True when [dragging] was just pulled out of the sidebar. */
    var draggingFromToolbar = false

    /** The instrument a new edge is being dragged from, if any. */
    var linkingFrom: Instrument? = null

    /** The level last previewed while placing a note, so it only sounds on crossing a line. */
    var previewLevel: Int? = null

    /**
     * The note being placed, as (type, level), or null: the instrument being dragged, or
     * the new one an edge would make if it were dropped on empty canvas right now.
     */
    fun placingNote(): Pair<SoundType, Int>? {
        (dragging as? Instrument)?.let { return it.type to it.level }
        val from = linkingFrom ?: return null
        val worldX = pointerX + selectedUniverse.cameraX
        val worldY = pointerY + selectedUniverse.cameraY
        if (selectedUniverse.instrumentNear(worldX, worldY) != null) return null  // would link, not create
        return from.type to levelAt(worldY)
    }

    /** The effect showing rotate/scale handles. Only counts while it's in [selectedUniverse]. */
    var selectedEffect: Effect? = null
        get() = field?.takeIf { it in selectedUniverse.effects }

    /** The handle of [selectedEffect] being dragged, if any. */
    var transforming: EffectHandle? = null

    /** True while the enabled universes are playing. */
    var playing = false

    /** The tempo, in beats per minute. Change it with setBpm (audio.kt), which keeps playback in step. */
    var bpm = 124  // house tempo

    /** True while the scale dropdown is open. */
    var scaleMenuOpen = false

    /** True while the instrument dropdown is open. */
    var instrumentMenuOpen = false

    /**
     * While [instrumentMenuOpen]: the sidebar instrument the dropdown is editing, or null
     * when it's the + dropdown for adding one.
     */
    var editingShape: InstrumentShape? = null

    /** Opens the dropdown for the sidebar instrument drawn as [shape], or the + dropdown if null. */
    fun openInstrumentMenu(shape: InstrumentShape?) {
        instrumentMenuOpen = true
        editingShape = shape
    }
}

/** How far the editor can scroll left/right, in CSS pixels. */
const val CAMERA_MIN_X = -2000.0
const val CAMERA_MAX_X = 2000.0

/** How far past the top and bottom levels the editor can scroll, in CSS pixels. */
const val CAMERA_Y_MARGIN = 120.0

class Universe(var name: String, var enabled: Boolean = true) {
    /** Where this universe's editor is scrolled to: the world point at the top-left of the screen. */
    var cameraX = 0.0
        private set

    /** Starts with the middle of the note range in the middle of the screen. */
    var cameraY = (topLevelY() + BOTTOM_LEVEL_Y) / 2 - CanvasState.height / 2
        private set

    /**
     * Scrolls the editor by (dx, dy). X stays within [CAMERA_MIN_X, CAMERA_MAX_X]; y stays
     * within [CAMERA_Y_MARGIN] of the note range (see levels.kt).
     */
    fun panBy(dx: Double, dy: Double) {
        cameraX = (cameraX + dx).coerceIn(CAMERA_MIN_X, CAMERA_MAX_X)
        val minY = topLevelY() - CAMERA_Y_MARGIN
        val maxY = (BOTTOM_LEVEL_Y + CAMERA_Y_MARGIN - CanvasState.height).coerceAtLeast(minY)
        cameraY = (cameraY + dy).coerceIn(minY, maxY)
    }

    /** Scrolls straight to (x, y), within the same limits as [panBy]. */
    fun scrollTo(x: Double, y: Double) = panBy(x - cameraX, y - cameraY)

    /** Every node in this universe, by id. */
    val nodes = mutableMapOf<Int, Node>()

    /** Effects applied in this universe, in order. */
    val effects = mutableListOf<Effect>()

    /** Lines connecting instruments. */
    val edges = mutableListOf<Edge>()

    /**
     * Instruments added to this universe's toolbar, in order, keyed by the shape their
     * instances are drawn as. Shapes are unique, so there are at most eight; types can repeat.
     */
    val instrumentTypes = mutableMapOf<InstrumentShape, SoundType>()

    /** Instrument instances in this universe, shown on the canvas. */
    val instruments = mutableListOf<Instrument>()

    private var timelineCache: Timeline? = null

    /**
     * What this universe plays, worked out from its instruments, edges and effects. It's
     * cached until [markChanged] is called.
     */
    val timeline: Timeline
        get() = timelineCache ?: buildTimeline(this).also { timelineCache = it }

    /** Call after changing anything the [timeline] depends on, so it's rebuilt next time. */
    fun markChanged() {
        timelineCache = null
    }

    /**
     * The topmost instrument or effect at world point (x, y), or null. Instruments are drawn
     * above effects, so they're checked first; within each list, later ones are on top.
     */
    fun movableAt(x: Double, y: Double): Movable? =
        instruments.lastOrNull { it.contains(x, y) } ?: effects.lastOrNull { it.contains(x, y) }

    /** The topmost instrument whose outline (see [Instrument.isNear]) covers world point (x, y). */
    fun instrumentNear(x: Double, y: Double): Instrument? = instruments.lastOrNull { it.isNear(x, y) }

    /** The first shape no sidebar instrument has yet, or null when all eight are taken. */
    fun nextInstrumentShape(): InstrumentShape? =
        InstrumentShape.entries.firstOrNull { it !in instrumentTypes }

    /** Types that can be added to the sidebar: all of them, until every shape is taken. */
    fun availableInstrumentTypes(): List<SoundType> =
        if (nextInstrumentShape() == null) emptyList() else allSoundTypes()

    /** Switches the sidebar instrument drawn as [shape], and all its instances, to [type]. */
    fun changeInstrumentType(shape: InstrumentShape, type: SoundType) {
        instrumentTypes[shape] = type
        instruments.filter { it.shape == shape }.forEach { it.type = type }
        markChanged()
    }

    /** Removes the sidebar instrument drawn as [shape], and all its instances. */
    fun removeInstrumentType(shape: InstrumentShape) {
        instrumentTypes.remove(shape)
        instruments.filter { it.shape == shape }.forEach { removeInstrument(it) }
    }

    /** Removes [instrument] and every edge to or from it. */
    fun removeInstrument(instrument: Instrument) {
        instruments.remove(instrument)
        edges.removeAll { it.from === instrument || it.to === instrument }
        markChanged()
    }

    /** The topmost edge within reach of world point (x, y), or null. */
    fun edgeAt(x: Double, y: Double): Edge? = edges.lastOrNull { it.isNear(x, y) }

    /** Removes [edge], leaving the instruments at both ends where they are. */
    fun removeEdge(edge: Edge) {
        edges.remove(edge)
        markChanged()
    }

    /**
     * Draws an edge from [from] to [to]. Drawing an edge that already exists deletes it;
     * drawing one the other way round reverses it.
     */
    fun connect(from: Instrument, to: Instrument) {
        if (from === to) return
        val same = edges.any { it.from === from && it.to === to }
        edges.removeAll { (it.from === from && it.to === to) || (it.from === to && it.to === from) }
        if (!same) edges += Edge(from, to)
        markChanged()
    }
}

/** Something on the canvas that can be dragged around, in world coordinates. */
interface Movable {
    var x: Double
    var y: Double
}
/** A point in a universe. [id] is its key in [Universe.nodes]. */
class Node(val id: Int, var x: Double, var y: Double)

/**
 * An instrument placed in a universe. [shape] is its type's shape in that universe.
 * [volume] is how loud this one note is, from 0 (silent) to 1 (full): hold it and scroll.
 */
class Instrument(
    var type: SoundType,
    val shape: InstrumentShape,
    override var x: Double,
    override var y: Double,
    volume: Double = 1.0,
) : Movable {
    var volume = volume.coerceIn(0.0, 1.0)
        set(value) {
            field = value.coerceIn(0.0, 1.0)
        }

    /** When this was last heard, on the `performance.now()` clock in ms. Drives its pulse. */
    var lastPlayedAt = Double.NEGATIVE_INFINITY

    /** The level (note) this plays: its y rounded to the nearest one. See levels.kt. */
    val level get() = levelAt(y)

    /** Whether world point (px, py) is on this instrument. Uses its square bounds: shapes are small. */
    fun contains(px: Double, py: Double) =
        kotlin.math.abs(px - x) <= INSTRUMENT_SIZE / 2 && kotlin.math.abs(py - y) <= INSTRUMENT_SIZE / 2

    /** Whether world point (px, py) is on this instrument or within [LINK_MARGIN] of it. */
    fun isNear(px: Double, py: Double): Boolean {
        val reach = INSTRUMENT_SIZE / 2 + LINK_MARGIN
        return kotlin.math.abs(px - x) <= reach && kotlin.math.abs(py - y) <= reach
    }
}

/** How far outside an instrument its link outline sits, in CSS pixels. */
const val LINK_MARGIN = 8.0

/**
 * A directed line from one instrument to another. [segments] is the delay: the beat is split
 * into that many parts, so 1 = every beat, 2 = every half beat, 3 = every third, and so on.
 */
class Edge(val from: Instrument, val to: Instrument, var segments: Int = 1) {
    /** Steps the delay to the next of [SEGMENT_OPTIONS], wrapping round to the first. */
    fun cycleSegments() {
        val index = SEGMENT_OPTIONS.indexOf(segments)
        segments = SEGMENT_OPTIONS[(index + 1) % SEGMENT_OPTIONS.size]
    }

    /** Whether world point (px, py) is within [EDGE_HIT_DISTANCE] of the line. */
    fun isNear(px: Double, py: Double): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return false
        // Closest point on the line to (px, py), as a fraction of the way along it.
        val t = (((px - from.x) * dx + (py - from.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return kotlin.math.hypot(px - (from.x + t * dx), py - (from.y + t * dy)) <= EDGE_HIT_DISTANCE
    }
}

/** The beat divisions an edge can step through when clicked. */
val SEGMENT_OPTIONS = listOf(1, 2, 3, 4, 6, 8)
const val EDGE_HIT_DISTANCE = 6.0

/** An effect placed in a universe. [rotation] is in radians; [scale] 1.0 is normal size. */
class Effect(
    override var x: Double,
    override var y: Double,
    var type: EffectType,
    var rotation: Double = 0.0,
    var scale: Double = 1.0,
) : Movable {
    /** Whether [node] sits inside this effect's shape. */
    fun overlaps(node: Node) = contains(node.x, node.y)

    /**
     * Whether world point (px, py) is inside this effect's shape. The point is moved into
     * the effect's own space (undoing its position, rotation and scale) and then checked
     * against the unrotated, unscaled shape.
     */
    /** Distance from the centre to the nearest point of the outline, in world pixels. */
    val innerRadius get() = type.innerRadius * scale

    /** Area inside the outline, in world pixels squared. */
    val area get() = type.area * scale * scale

    fun contains(px: Double, py: Double): Boolean {
        val dx = px - x
        val dy = py - y
        val cos = kotlin.math.cos(rotation)
        val sin = kotlin.math.sin(rotation)
        val localX = (dx * cos + dy * sin) / scale
        val localY = (-dx * sin + dy * cos) / scale
        return type.containsLocal(localX, localY)
    }
}
