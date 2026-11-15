package durhack26.music

import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The y axis is pitch. It's split into levels, LEVEL_HEIGHT apart, one per note of the
 * chosen scale (EditorState.scale) in the chosen key (EditorState.key), from the key's
 * note in octave 3 at the bottom to the same note in octave 6 at the top: C3 to C6 in C.
 * Higher on screen = higher note. Scales with fewer notes per octave have fewer levels.
 *
 * Notes can sit anywhere; when they play, their y is rounded to the nearest level.
 * Melodic instruments play that level's note. Drums ignore the scale and just cycle
 * through the kit, one drum per level (see DrumSound).
 *
 * Level 0 is the bottom (e.g. C3) and sits at world y = 0; levels go up (negative y) from there.
 */

const val LEVEL_HEIGHT = 48.0
private const val LOWEST_OCTAVE = 3
private const val OCTAVES = 3

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** How many keys there are: one per semitone, C to B. */
const val KEY_COUNT = 12

/** The name of [key] (semitones above C), e.g. "F#". */
fun keyName(key: Int) = NOTE_NAMES[key]

/**
 * A scale, as the semitones above the key's note of each note in one octave. This is for beginners, so
 * each is labelled by the mood it gives rather than its music-theory name (in the comment).
 */
enum class Scale(val label: String, val semitones: List<Int>) {
    HAPPY("Happy", listOf(0, 2, 4, 5, 7, 9, 11)),          // major
    SAD("Sad", listOf(0, 2, 3, 5, 7, 8, 10)),              // natural minor
    PEACEFUL("Peaceful", listOf(0, 2, 4, 7, 9)),           // major pentatonic
    MOODY("Moody", listOf(0, 3, 5, 7, 10)),                // minor pentatonic
    DREAMY("Dreamy", listOf(0, 2, 4, 6, 7, 9, 11)),        // lydian
    GROOVY("Groovy", listOf(0, 2, 3, 5, 7, 9, 10)),        // dorian
    SOULFUL("Soulful", listOf(0, 3, 5, 6, 7, 10)),         // blues
    MYSTERIOUS("Mysterious", listOf(0, 1, 3, 5, 7, 8, 10)), // phrygian
    CHAOTIC("Chaotic", (0 until 12).toList());             // chromatic

    /** Every level in the range, including the top C. */
    val levelCount get() = OCTAVES * semitones.size + 1
}

private val scale get() = EditorState.scale

/** World y of [level]. */
fun levelY(level: Int) = -level * LEVEL_HEIGHT

/** World y of the top and bottom levels in the current scale. */
fun topLevelY() = levelY(scale.levelCount - 1)
const val BOTTOM_LEVEL_Y = 0.0

/** The level nearest world y, clamped to the current scale's range. */
fun levelAt(y: Double) = (-y / LEVEL_HEIGHT).roundToInt().coerceIn(0, scale.levelCount - 1)

/** Whether [level] exists in the current scale. */
fun isLevel(level: Int) = level in 0 until scale.levelCount

/** [level]'s note as a MIDI number, moved by [octaveShift] octaves. */
private fun midiOf(level: Int, octaveShift: Int): Int {
    val perOctave = scale.semitones.size
    val octave = LOWEST_OCTAVE + level / perOctave + octaveShift
    return 12 * (octave + 1) + EditorState.key + scale.semitones[level % perOctave]
}

/** E.g. "E4" or "F#3". [octaveShift] moves it by whole octaves. */
fun noteName(level: Int, octaveShift: Int = 0): String {
    val midi = midiOf(level, octaveShift)
    return NOTE_NAMES[midi % 12] + (midi / 12 - 1)
}

/** Frequency of [level]'s note in Hz, moved by [octaveShift] octaves. */
fun frequencyOf(level: Int, octaveShift: Int = 0): Double =
    440.0 * 2.0.pow((midiOf(level, octaveShift) - 69) / 12.0)
