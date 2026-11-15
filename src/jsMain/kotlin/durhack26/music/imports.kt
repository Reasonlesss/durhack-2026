package durhack26.music

import durhack26.music.tone.Filter
import durhack26.music.tone.Gain
import durhack26.music.tone.Sampler
import durhack26.music.tone.Synth
import durhack26.music.tone.ToneAudioNode
import durhack26.music.tone.getContext
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.files.File
import kotlin.js.Promise
import kotlin.js.json
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Importing sounds from outside the app (an Advanced option, see advanced.kt):
 *
 * - Presets for Sylenth1 and Serum (1), as .fxp files. Neither format is public, and both
 *   synths do far more than Tone.js can, so a preset becomes an approximation built from
 *   its main settings: the oscillators' waveforms, unison, tuning and levels, the amp
 *   envelope, and the filter. Wavetables, modulation, the arpeggiator and the synth's own
 *   effects aren't carried over. Where the values live in each file comes from people
 *   who reverse-engineered them:
 *     Sylenth1: https://github.com/perivar/AudioVSTToolbox (PresetConverter/Sylenth1Preset.cs)
 *     Serum:    https://gist.github.com/0xdevalias/135a18e979ac8e302ebbc700a50a8d74
 * - Samples: any audio file the browser can decode. It plays at its own pitch on C4 and is
 *   repitched for other levels, like a sampler keyboard.
 *
 * Each import becomes an [ImportedSound], a [SoundType] like the built-in instruments: it's
 * offered in every universe's instrument dropdown, and saved inside song files (see songFile.kt).
 */

/** A preset or sample the user imported. */
sealed class ImportedSound(override val label: String) : SoundType {
    override val category get() = InstrumentCategory.IMPORTED
    override val octaveShift get() = 0
    override fun synthKey(level: Int) = 0
    override fun describe(level: Int) = noteName(level)

    /** This sound as song-file JSON, enough to make it again with [importedSoundFromJson]. */
    abstract fun toJson(): dynamic
}

// --- Presets --------------------------------------------------------------------------------

/** One oscillator of a preset. [detune] (cents) holds its octave, note and fine tuning together. */
class PresetOscillator(
    /** A Tone.js basic waveform: "sine", "triangle", "sawtooth" or "square". */
    val wave: String,
    /** Unison voices, 1 or more. */
    val voices: Int,
    /** How far apart unison voices are spread, in cents. */
    val spread: Double,
    val detune: Double,
    /** 0..1. */
    val volume: Double,
)

/**
 * The lowest cutoff an imported filter is given, in Hz. Presets usually sweep their cutoff
 * up with an envelope or LFO, which isn't carried over; left at its resting value, which can
 * be a few tens of Hz, the sound would be all but silent.
 */
private const val MIN_IMPORTED_CUTOFF = 150.0

/** A preset's filter. [type] is a Tone.js filter type; [rolloff] is -12 or -24 (dB per octave). */
class PresetFilter(val type: String, val frequency: Double, val q: Double, val rolloff: Int)

/**
 * A group of oscillators sharing one amp envelope and filter: a "part" in Sylenth1, which has
 * two. Times are in seconds, [sustain] and [gain] are 0..1.
 */
class PresetLayer(
    val oscillators: List<PresetOscillator>,
    val attack: Double,
    val decay: Double,
    val sustain: Double,
    val release: Double,
    val filter: PresetFilter?,
    val gain: Double,
)

/** An imported synth preset, rebuilt from Tone.js synths. [source] names the synth it came from. */
class PresetSound(label: String, val source: String, val layers: List<PresetLayer>, val gain: Double) :
    ImportedSound(label) {
    /** Long-attack sounds (pads) are held for longer, so they have time to swell. */
    override val duration = if (layers.any { it.attack > 0.25 }) "2n" else "8n"

    override fun createSound(level: Int): InstrumentSound? {
        val output = Gain(json("gain" to gain))
        val nodes = mutableListOf<ToneAudioNode>(output)
        val voices = mutableListOf<ToneAudioNode>()
        for (layer in layers) {
            val layerGain = Gain(json("gain" to layer.gain))
            layerGain.connect(output)
            nodes += layerGain
            val filter = layer.filter?.let { f ->
                Filter(json("type" to f.type, "frequency" to f.frequency, "Q" to f.q, "rolloff" to f.rolloff)).also {
                    it.connect(layerGain)
                    nodes += it
                }
            }
            for (osc in layer.oscillators) {
                val synth = Synth(synthOptions(osc, layer))
                synth.connect(filter ?: layerGain)
                nodes += synth
                voices += synth
            }
        }
        if (voices.isEmpty()) {
            nodes.forEach { it.dispose() }
            return null
        }
        return InstrumentSound(voices.first(), output, nodes, voices)
    }

    override fun play(sound: InstrumentSound, level: Int, time: Double) {
        val frequency = frequencyOf(level)
        for (voice in sound.voices) voice.asDynamic().triggerAttackRelease(frequency, duration, time)
    }

    override fun toJson(): dynamic = json(
        "kind" to "preset",
        "label" to label,
        "source" to source,
        "gain" to gain,
        "layers" to layers.map { layer ->
            json(
                "attack" to layer.attack, "decay" to layer.decay, "sustain" to layer.sustain, "release" to layer.release,
                "gain" to layer.gain,
                "filter" to layer.filter?.let { json("type" to it.type, "frequency" to it.frequency, "q" to it.q, "rolloff" to it.rolloff) },
                "oscillators" to layer.oscillators.map {
                    json("wave" to it.wave, "voices" to it.voices, "spread" to it.spread, "detune" to it.detune, "volume" to it.volume)
                }.toTypedArray(),
            )
        }.toTypedArray(),
    )
}

/** Tone.js Synth options for one oscillator of [layer]. */
private fun synthOptions(osc: PresetOscillator, layer: PresetLayer): dynamic {
    val oscillator: dynamic = js("({})")
    if (osc.voices > 1) {
        // "fat" oscillators are Tone.js's unison: several detuned copies.
        oscillator.type = "fat${osc.wave}"
        oscillator.count = osc.voices
        oscillator.spread = osc.spread
    } else {
        oscillator.type = osc.wave
    }
    val envelope: dynamic = js("({})")
    envelope.attack = layer.attack.coerceIn(0.002, 10.0)  // a tiny attack, so notes don't click
    envelope.decay = layer.decay.coerceIn(0.01, 10.0)
    envelope.sustain = layer.sustain.coerceIn(0.0, 1.0)
    envelope.release = layer.release.coerceIn(0.01, 6.0)
    val options: dynamic = js("({})")
    options.oscillator = oscillator
    options.envelope = envelope
    options.detune = osc.detune
    options.volume = gainToDb(osc.volume)
    return options
}

private fun gainToDb(gain: Double) = 20 * log10(gain.coerceAtLeast(0.001))

/**
 * The most oscillators a preset keeps, loudest first. Each is a whole synth per note, and
 * the audio thread can only take so many (see audio.kt).
 */
private const val MAX_PRESET_OSCILLATORS = 3

/** Most unison voices per oscillator, for the same reason. */
private const val MAX_UNISON = 4

/** Keeps only the [MAX_PRESET_OSCILLATORS] loudest oscillators across [layers], dropping layers left empty. */
private fun loudestOnly(layers: List<PresetLayer>): List<PresetLayer> {
    val kept = layers.flatMap { it.oscillators.map { osc -> osc to it.gain } }
        .sortedByDescending { (osc, layerGain) -> osc.volume * layerGain }
        .take(MAX_PRESET_OSCILLATORS)
        .map { it.first }
        .toSet()
    return layers.map { layer ->
        PresetLayer(
            layer.oscillators.filter { it in kept }, layer.attack, layer.decay, layer.sustain, layer.release,
            layer.filter, layer.gain,
        )
    }.filter { it.oscillators.isNotEmpty() }
}

// --- Reading .fxp files ---------------------------------------------------------------------

/** Why a file couldn't be imported, in words for the user. */
class ImportError(message: String) : Exception(message)

/**
 * A VST2 preset (.fxp) or bank (.fxb) file: "CcnK", then big-endian fields. Ones that store
 * their settings as an opaque blob ("FPCh" for a preset, "FBCh" for a bank) have it in
 * [chunk]; presets that store plain parameters ("FxCk"), which some Serum presets do, have
 * them in [params]. A bank has [programs] presets in it (a preset, 1).
 */
private class Fxp(
    val pluginId: String,
    val name: String,
    val chunk: Uint8Array?,
    val params: List<Double>?,
    val bank: Boolean = false,
    val programs: Int = 1,
)

/** Where a bank's chunk size is: after its header's 128 bytes of reserved space. The chunk follows it. */
private const val BANK_CHUNK_SIZE_OFFSET = 156

private fun readFxp(bytes: Uint8Array): Fxp {
    if (bytes.length < 60 || ascii(bytes, 0, 4) != "CcnK") throw ImportError("it isn't a VST preset (.fxp) file")
    val view = DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
    val pluginId = ascii(bytes, 16, 4)
    val name = ascii(bytes, 28, 28).substringBefore('\u0000').trim()
    return when (ascii(bytes, 8, 4)) {
        "FPCh" -> {
            val size = view.getInt32(56, false)
            if (size <= 0 || 60 + size > bytes.length) throw ImportError("it's cut short")
            Fxp(pluginId, name, Uint8Array(bytes.buffer, bytes.byteOffset + 60, size), null)
        }
        "FxCk" -> {
            val count = view.getInt32(24, false)
            if (count <= 0 || 56 + count * 4 > bytes.length) throw ImportError("it's cut short")
            Fxp(pluginId, name, null, (0 until count).map { view.getFloat32(56 + it * 4, false).toDouble() })
        }
        "FBCh" -> {
            if (bytes.length < BANK_CHUNK_SIZE_OFFSET + 4) throw ImportError("it's cut short")
            val programs = view.getInt32(24, false)
            val size = view.getInt32(BANK_CHUNK_SIZE_OFFSET, false)
            val start = BANK_CHUNK_SIZE_OFFSET + 4
            if (programs <= 0 || size <= 0 || start + size > bytes.length) throw ImportError("it's cut short")
            Fxp(pluginId, "", Uint8Array(bytes.buffer, bytes.byteOffset + start, size), null, bank = true, programs = programs)
        }
        "FxBk" -> throw ImportError("it's a bank in a format that can't be read. Export the presets you want from it as single .fxp files and import those")
        else -> throw ImportError("it isn't a VST preset (.fxp) file")
    }
}

private fun ascii(bytes: Uint8Array, start: Int, length: Int): String =
    (start until minOf(start + length, bytes.length)).map { bytes[it].toInt().and(0xff).toChar() }.joinToString("")

// --- Sylenth1 -------------------------------------------------------------------------------

/*
 * Sylenth1 keeps its settings as little-endian floats, 0..1, after a 20-byte header in the
 * chunk. Byte offsets below are from the start of the chunk. Then comes the preset's name.
 * A bank's chunk has the same header, then every preset one after another, each a
 * [SYLENTH_SETTINGS_BYTES] block of settings, sometimes a 4-byte EQ mode (newer versions),
 * then its name; so the n'th preset's offsets are n × that size further on.
 */
private const val SYLENTH_ID = "syl1"

private class SylenthPart(
    val attack: Int, val decay: Int, val release: Int, val sustain: Int,
    val cutoff: Int, val reso: Int, val type: Int, val db: Int,
    val mix: Int, val oscillators: List<Int>,
)

private val SYLENTH_PARTS = listOf(
    SylenthPart(attack = 20, decay = 24, release = 28, sustain = 32, cutoff = 188, reso = 200, type = 204, db = 208, mix = 296, oscillators = listOf(344, 392)),
    SylenthPart(attack = 36, decay = 40, release = 44, sustain = 48, cutoff = 212, reso = 224, type = 228, db = 232, mix = 300, oscillators = listOf(440, 488)),
)

// Within an oscillator, from its first field (Detune).
private const val OSC_FINE = 4
private const val OSC_NOTE = 12
private const val OSC_OCTAVE = 16
private const val OSC_VOICES = 36
private const val OSC_VOLUME = 40
private const val OSC_WAVE = 44

private const val SYLENTH_FILTER_CONTROL_CUTOFF = 236
private const val SYLENTH_MAIN_VOLUME = 292
private const val SYLENTH_SOLO = 608
private const val SYLENTH_PART_SELECT = 984
private const val SYLENTH_HEADER_BYTES = 20
private const val SYLENTH_SETTINGS_BYTES = 968
private const val SYLENTH_EQ_MODE_BYTES = 4
private const val SYLENTH_NAME_BYTES = 36
private const val SYLENTH_MIN_CHUNK = SYLENTH_HEADER_BYTES + SYLENTH_SETTINGS_BYTES

/** Sylenth1's waveforms in order, 0..1 in sevenths, as Tone.js waves. Null is noise, which isn't carried over. */
private val SYLENTH_WAVES = listOf("sine", "sawtooth", "triangle", "square", "square", "square", "sawtooth", null)

/**
 * Every preset in a Sylenth1 preset or bank, in order, each with its name (or, if it has
 * none, [fallbackName] and its number). Each is either the preset, or why it can't be
 * imported (e.g. all its oscillators are off).
 */
private fun sylenthPresets(fxp: Fxp, fallbackName: String): List<Pair<String, Result<PresetSound>>> {
    val chunk = fxp.chunk ?: throw ImportError("it's in an older Sylenth1 format that can't be read")
    if (chunk.length < SYLENTH_MIN_CHUNK || ascii(chunk, 0, 4) != "1lys") {
        throw ImportError("it's in a Sylenth1 format that can't be read (newer versions can export presets as .fxp, which can)")
    }
    // Each preset's size, from how many there are: with the EQ mode or without.
    val body = chunk.length - SYLENTH_HEADER_BYTES
    val stride = listOf(SYLENTH_SETTINGS_BYTES + SYLENTH_EQ_MODE_BYTES + SYLENTH_NAME_BYTES, SYLENTH_SETTINGS_BYTES + SYLENTH_NAME_BYTES)
        .firstOrNull { body >= it * fxp.programs && (!fxp.bank || body == it * fxp.programs) }
        ?: if (fxp.bank) throw ImportError("its presets couldn't be found in it") else body
    val view = DataView(chunk.buffer, chunk.byteOffset, chunk.byteLength)
    return (0 until fxp.programs).map { n ->
        val base = n * stride
        val nameAt = SYLENTH_HEADER_BYTES + base + stride - SYLENTH_NAME_BYTES
        val stored = if (nameAt >= SYLENTH_MIN_CHUNK + base) ascii(chunk, nameAt, SYLENTH_NAME_BYTES).substringBefore('\u0000').trim() else ""
        // Its own name field holds more than the file header's (36 characters, not 28).
        val name = when {
            stored.isNotEmpty() -> stored
            !fxp.bank && fxp.name.isNotEmpty() -> fxp.name
            fxp.bank -> "$fallbackName ${n + 1}"
            else -> fallbackName
        }
        name to runCatching { sylenthPreset(view, base, name) }
    }
}

/** One Sylenth1 preset, its settings [base] bytes further into the chunk than a single preset's. */
private fun sylenthPreset(view: DataView, base: Int, name: String): PresetSound {
    fun f(offset: Int) = view.getFloat32(base + offset, true).toDouble().takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0

    // Solo plays only the part being edited.
    val parts = if (f(SYLENTH_SOLO) >= 0.5) listOf(SYLENTH_PARTS[if (f(SYLENTH_PART_SELECT) >= 0.5) 1 else 0]) else SYLENTH_PARTS
    val layers = parts.map { part ->
        val oscillators = part.oscillators.mapNotNull { o ->
            val voices = (f(o + OSC_VOICES) * 8).roundToInt()  // 0 voices turns the oscillator off
            val volume = f(o + OSC_VOLUME)
            val wave = SYLENTH_WAVES[(f(o + OSC_WAVE) * 7).roundToInt()]
            if (voices == 0 || volume <= 0.0 || wave == null) return@mapNotNull null
            val semitones = (f(o + OSC_OCTAVE) * 6 - 3).roundToInt() * 12 + (f(o + OSC_NOTE) * 14 - 7).roundToInt()
            PresetOscillator(
                wave = wave,
                voices = voices.coerceAtMost(MAX_UNISON),
                spread = f(o) * 60,  // Detune 0..10 → up to 60 cents across the voices
                detune = semitones * 100 + (f(o + OSC_FINE) * 2 - 1) * 100,
                volume = volume,
            )
        }
        val filterType = when ((f(part.type) * 3).roundToInt()) {
            1 -> "lowpass"
            2 -> "bandpass"
            3 -> "highpass"
            else -> null  // bypass
        }
        PresetLayer(
            oscillators = oscillators,
            attack = sylenthEnvelopeSeconds(f(part.attack)),
            decay = sylenthEnvelopeSeconds(f(part.decay)),
            sustain = f(part.sustain),
            release = sylenthEnvelopeSeconds(f(part.release)),
            filter = filterType?.let {
                PresetFilter(
                    type = it,
                    frequency = sylenthCutoff(f(part.cutoff), f(SYLENTH_FILTER_CONTROL_CUTOFF)),
                    q = 0.7 + f(part.reso) * 12,
                    rolloff = if (f(part.db) >= 0.5) -12 else -24,
                )
            },
            gain = f(part.mix) * 2,  // Mix 5 (of 10) is unity
        )
    }
    val kept = loudestOnly(layers)
    if (kept.isEmpty()) throw ImportError("all its oscillators are off (or noise, which can't be imported)")
    val oscillatorCount = kept.sumOf { it.oscillators.size }
    return PresetSound(
        label = name,
        source = "Sylenth1",
        layers = kept,
        // Main volume 5 (of 10) is unity; split across the oscillators so stacking them doesn't clip.
        gain = f(SYLENTH_MAIN_VOLUME) * 2 / oscillatorCount,
    )
}

/**
 * A Sylenth1 envelope value (0..1) in seconds. Sylenth1's curve isn't published: this is the
 * fit PresetConverter measured, in three pieces.
 */
private fun sylenthEnvelopeSeconds(x: Double): Double {
    val ms = when {
        x <= 0.1 -> 2326.388889 * x.pow(3) + 650.5952381 * x.pow(2) + 204.6646825 * x + 0.541666667
        x < 0.51 -> -10825.28025 * x.pow(6) + 51052.14783 * x.pow(5) - 37084.52751 * x.pow(4) +
            17464.58482 * x.pow(3) - 2556.284025 * x.pow(2) + 537.7264341 * x - 12.90202926
        else -> 28.18696734 * exp(6.970916827 * x)
    }
    return (ms / 1000).coerceAtLeast(0.0)
}

/** A Sylenth1 filter's cutoff in Hz, with the shared Filter Control cutoff on top (PresetConverter's fit). */
private fun sylenthCutoff(filter: Double, control: Double) =
    (0.064749088 * exp(10.152717 * (filter + control))).coerceIn(MIN_IMPORTED_CUTOFF, 20000.0)

// --- Serum ----------------------------------------------------------------------------------

/*
 * Serum 1 keeps its settings zlib-compressed in the chunk. Inside, its automatable
 * parameters are little-endian floats, 0..1, in the order a DAW lists them, from
 * PARAMS_OFFSET. Whether an oscillator (or the filter) is switched on isn't one of those
 * parameters, so one oscillator is used: A, or B if A is silent, or else the sub oscillator.
 * The filter is only used if it looks set up: moved off its default and not shut.
 */
private const val SERUM_ID = "XfsX"
private const val SERUM_PARAMS_OFFSET = 0x3460

private const val SERUM_MASTER_VOLUME = 0
/** Oscillators A and B have the same parameters, in the same order, from these. */
private const val SERUM_OSC_A = 1
private const val SERUM_OSC_B = 14
// From an oscillator's first parameter (its volume).
private const val OSC_OCTAVE_PARAM = 2
private const val OSC_SEMI_PARAM = 3
private const val OSC_FINE_PARAM = 4
private const val OSC_UNISON_PARAM = 5
private const val OSC_UNISON_DETUNE_PARAM = 6

private const val SERUM_SUB_LEVEL = 33
private const val SERUM_ENV1_ATTACK = 35
private const val SERUM_ENV1_DECAY = 37
private const val SERUM_ENV1_SUSTAIN = 38
private const val SERUM_ENV1_RELEASE = 39
private const val SERUM_OSC_A_TO_FILTER = 40
private const val SERUM_OSC_B_TO_FILTER = 41
private const val SERUM_SUB_TO_FILTER = 43
private const val SERUM_FILTER_CUTOFF = 45
private const val SERUM_FILTER_RESO = 46
private const val SERUM_FILTER_MIX = 49
private const val SERUM_PARAM_COUNT = 50

/** Quieter than this, an oscillator counts as silent. */
private const val SERUM_SILENT = 0.01

/** Serum's untouched cutoff (about 425 Hz). Left there, the filter is most likely switched off. */
private const val SERUM_DEFAULT_CUTOFF = 0.5

/** Below this the cutoff is under about 12 Hz: nothing would get through, so the filter must be off. */
private const val SERUM_SHUT_CUTOFF = 0.05

private fun serumPreset(name: String, params: List<Double>): PresetSound {
    if (params.size < SERUM_PARAM_COUNT || params.take(SERUM_PARAM_COUNT).any { !it.isFinite() }) {
        throw ImportError("its settings couldn't be found")
    }
    fun p(i: Int) = params[i].coerceIn(0.0, 1.0)

    /** Oscillator A or B, from its first parameter [base]. */
    fun oscillator(base: Int) = PresetOscillator(
        // Serum's Basic Shapes table, which most presets start from, defaults to a saw.
        wave = "sawtooth",
        voices = (1 + p(base + OSC_UNISON_PARAM) * 15).roundToInt().coerceIn(1, MAX_UNISON),  // 1..16 voices
        spread = p(base + OSC_UNISON_DETUNE_PARAM) * 80,
        detune = (p(base + OSC_OCTAVE_PARAM) * 8 - 4).roundToInt() * 1200 +  // -4..4 octaves
            (p(base + OSC_SEMI_PARAM) * 24 - 12).roundToInt() * 100 +           // -12..12 semitones
            (p(base + OSC_FINE_PARAM) * 200 - 100),                             // -100..100 cents
        volume = p(base),
    )
    val (osc, toFilter) = when {
        p(SERUM_OSC_A) > SERUM_SILENT -> oscillator(SERUM_OSC_A) to SERUM_OSC_A_TO_FILTER
        p(SERUM_OSC_B) > SERUM_SILENT -> oscillator(SERUM_OSC_B) to SERUM_OSC_B_TO_FILTER
        // The sub oscillator is a plain wave; most presets that lean on it (808s) use a sine.
        p(SERUM_SUB_LEVEL) > SERUM_SILENT ->
            PresetOscillator("sine", 1, 0.0, 0.0, p(SERUM_SUB_LEVEL)) to SERUM_SUB_TO_FILTER
        else -> throw ImportError("it only uses Serum's noise oscillator, which can't be imported")
    }
    val cutoff = p(SERUM_FILTER_CUTOFF)
    val filtered = p(toFilter) >= 0.5 && p(SERUM_FILTER_MIX) >= 0.5 &&
        kotlin.math.abs(cutoff - SERUM_DEFAULT_CUTOFF) > 1e-4 && cutoff > SERUM_SHUT_CUTOFF
    val layer = PresetLayer(
        oscillators = listOf(osc),
        attack = serumEnvelopeSeconds(p(SERUM_ENV1_ATTACK)),
        decay = serumEnvelopeSeconds(p(SERUM_ENV1_DECAY)),
        sustain = p(SERUM_ENV1_SUSTAIN),
        release = serumEnvelopeSeconds(p(SERUM_ENV1_RELEASE)),
        filter = if (!filtered) null else PresetFilter(
            type = "lowpass",
            frequency = (8 * (22050.0 / 8).pow(cutoff)).coerceIn(MIN_IMPORTED_CUTOFF, 20000.0),  // 8 Hz..22 kHz
            q = 0.7 + p(SERUM_FILTER_RESO) * 12,
            rolloff = -12,
        ),
        gain = 1.0,
    )
    // Master volume's default (70%) is unity.
    return PresetSound(name, "Serum", listOf(layer), gain = p(SERUM_MASTER_VOLUME) / 0.7)
}

/**
 * A Serum envelope time (0..1) in seconds: 32 s × x⁵, which matches Serum's defaults
 * (0.11 → 0.5 ms attack, 0.5 → 1 s decay, 0.215 → 15 ms release).
 */
private fun serumEnvelopeSeconds(x: Double) = 32 * x.coerceIn(0.0, 1.0).pow(5)

/** Serum's settings from a compressed chunk. The zlib data starts at once, or after a 4-byte length. */
private fun serumParams(chunk: Uint8Array): Promise<List<Double>> {
    fun at(skip: Int) = inflate(Uint8Array(chunk.buffer, chunk.byteOffset + skip, chunk.length - skip))
    return at(0).catch { at(4) }.unsafeCast<Promise<ArrayBuffer>>().then { data ->
        val view = DataView(data)
        val count = ((data.byteLength - SERUM_PARAMS_OFFSET) / 4).coerceIn(0, SERUM_PARAM_COUNT)
        (0 until count).map { view.getFloat32(SERUM_PARAMS_OFFSET + it * 4, true).toDouble() }
    }
}

/**
 * Decompresses zlib data with the browser's own decompressor. Serum's chunks have bytes after
 * the end of the zlib data, which the browser reports as an error once it's decompressed the
 * rest: whatever came out before that is kept.
 */
private fun inflate(bytes: Uint8Array): Promise<ArrayBuffer> = js(
    """(function (bytes) {
        var reader = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate')).getReader();
        var parts = [], length = 0;
        function joined() {
            var out = new Uint8Array(length), at = 0;
            parts.forEach(function (part) { out.set(part, at); at += part.length; });
            return out.buffer;
        }
        function next() {
            return reader.read().then(function (result) {
                if (result.done) return joined();
                parts.push(result.value);
                length += result.value.length;
                return next();
            }, function (error) {
                if (length === 0) throw error;
                return joined();
            });
        }
        return next();
    })"""
)(bytes).unsafeCast<Promise<ArrayBuffer>>()

/**
 * What's in a preset or bank file, whichever synth it's for: its presets, each by name with
 * either the preset or why it can't be imported. [bank] is whether the file was a bank.
 */
private class PresetFile(val bank: Boolean, val presets: List<Pair<String, Result<PresetSound>>>)

/** The preset(s) in [bytes], whichever synth they're for. */
private fun presetsFromBytes(bytes: Uint8Array, fileName: String): Promise<PresetFile> {
    val fallbackName = fileName.substringBeforeLast('.')
    if (ascii(bytes, 0, 8) == "XferJson") {
        return Promise.reject(ImportError("it's a Serum 2 preset. Only Serum 1 presets (.fxp) can be imported; Serum 2 can save presets in the old format"))
    }
    val fxp = try { readFxp(bytes) } catch (e: ImportError) { return Promise.reject(e) }
    if (fxp.bank && fxp.pluginId != SYLENTH_ID) {
        return Promise.reject(ImportError("it's a bank for a synth other than Sylenth1. Only Sylenth1 banks can be imported; export the presets as single .fxp files instead"))
    }
    return when (fxp.pluginId) {
        SYLENTH_ID -> try {
            Promise.resolve(PresetFile(fxp.bank, sylenthPresets(fxp, fallbackName)))
        } catch (e: ImportError) {
            Promise.reject(e)
        }
        SERUM_ID -> presetFromBytes(fxp, fallbackName).then { PresetFile(false, listOf(it.label to Result.success(it))) }
        else -> Promise.reject(ImportError("it's for another synth (\"${fxp.pluginId}\"). Only Sylenth1 and Serum presets can be imported"))
    }
}

/** A Serum preset. */
private fun presetFromBytes(fxp: Fxp, fallbackName: String): Promise<PresetSound> {
    return when (fxp.pluginId) {
        SERUM_ID -> {
            val name = fxp.name.ifEmpty { fallbackName }
            val params = fxp.params?.let { Promise.resolve(it) } ?: serumParams(fxp.chunk!!)
            params.then({ serumPreset(name, it) }, { throw ImportError("its settings couldn't be unpacked") })
                .unsafeCast<Promise<PresetSound>>()
        }
        else -> Promise.reject(ImportError("it's for another synth (\"${fxp.pluginId}\"). Only Sylenth1 and Serum presets can be imported"))
    }
}

// --- Samples --------------------------------------------------------------------------------

/** Samples bigger than this aren't accepted: they're stored inside song files. */
private const val MAX_SAMPLE_BYTES = 10 * 1024 * 1024

/** Audio files the sample picker offers. Anything the browser can decode works. */
private const val SAMPLE_TYPES = "audio/*,.wav,.mp3,.ogg,.flac,.aif,.aiff,.m4a"

/**
 * An imported audio sample, [data] being the file's bytes as base64 (kept for saving). It's
 * decoded in the background; until it's ready, or if it can't be, it plays nothing.
 */
class SampleSound(label: String, val data: String) : ImportedSound(label) {
    override val duration = "8n"

    /** The decoded AudioBuffer, once it's ready. */
    private var buffer: dynamic = null

    /** Settles once decoding has finished: with nothing, or the reason it failed. */
    val ready: Promise<Unit> = getContext().decodeAudioData(base64ToBytes(data).buffer).then<Unit> { decoded: dynamic ->
        buffer = decoded
    }

    override fun createSound(level: Int): InstrumentSound? {
        if (buffer == null) return null
        val urls: dynamic = js("({})")
        urls["C4"] = buffer  // its own pitch is C4; other notes are repitched from it
        val options: dynamic = js("({})")
        options.urls = urls
        options.release = 0.3
        return InstrumentSound(Sampler(options))
    }

    /** Plays the whole sample, as samplers do with one-shots, rather than cutting it off after [duration]. */
    override fun play(sound: InstrumentSound, level: Int, time: Double) {
        sound.synth.asDynamic().triggerAttack(frequencyOf(level), time)
    }

    override fun toJson(): dynamic = json("kind" to "sample", "label" to label, "data" to data)
}

private fun bytesToBase64(bytes: Uint8Array): String = js(
    """(function (b) {
        var s = '';
        for (var i = 0; i < b.length; i += 0x8000) s += String.fromCharCode.apply(null, b.subarray(i, i + 0x8000));
        return btoa(s);
    })"""
)(bytes) as String

private fun base64ToBytes(text: String): Uint8Array = js(
    """(function (t) {
        var s = atob(t), b = new Uint8Array(s.length);
        for (var i = 0; i < s.length; i++) b[i] = s.charCodeAt(i);
        return b;
    })"""
)(text).unsafeCast<Uint8Array>()

// --- Importing ------------------------------------------------------------------------------

/**
 * Asks for a Sylenth1 or Serum preset, or a Sylenth1 bank, and imports it. A bank opens a
 * list of its presets to pick from (see bankPicker.kt). Must be called from a click or key press.
 */
fun importPreset() = pickFile(".fxp,.fxb") { file ->
    readBytes(file)
        .then { bytes -> presetsFromBytes(bytes, file.name) }
        .unsafeCast<Promise<PresetFile>>()
        .then(
            { found ->
                if (found.bank) {
                    showBankPicker(file.name.substringBeforeLast('.'), found.presets) { picked -> addImportedBank(picked) }
                } else {
                    val preset = found.presets.single().second.getOrElse { throw it }
                    addImported(preset, "Imported ${preset.source} preset \"${preset.label}\"")
                }
            },
            { error -> importFailed(file, error) },
        )
        .catch { error -> importFailed(file, error) }
}

/** Adds the presets picked from a bank to the library, and as many as there's room for to the sidebar. */
private fun addImportedBank(presets: List<PresetSound>) {
    if (presets.isEmpty()) return
    EditorState.importedSounds += presets
    val universe = EditorState.selectedUniverse
    var added = 0
    for (preset in presets) {
        val shape = universe.nextInstrumentShape() ?: break
        universe.instrumentTypes[shape] = preset
        added++
    }
    val count = if (presets.size == 1) "\"${presets[0].label}\"" else "${presets.size} presets"
    showImportStatus(
        when (added) {
            presets.size -> "Imported $count. They're in the sidebar."
            0 -> "Imported $count. The sidebar is full: pick them from an instrument's dropdown, under Imported."
            else -> "Imported $count. $added are in the sidebar; pick the rest from an instrument's dropdown, under Imported."
        },
    )
}

/** Asks for an audio file and imports it as a sample. Must be called from a click or key press. */
fun importSample() = pickFile(SAMPLE_TYPES) { file ->
    if (file.size.toInt() > MAX_SAMPLE_BYTES) {
        importFailed(file, ImportError("it's over ${MAX_SAMPLE_BYTES / 1024 / 1024} MB. Try a shorter or compressed (MP3/OGG) version"))
        return@pickFile
    }
    readBytes(file).then { bytes ->
        val sample = SampleSound(file.name.substringBeforeLast('.'), bytesToBase64(bytes))
        sample.ready.then(
            { addImported(sample, "Imported sample \"${sample.label}\"") },
            { importFailed(file, ImportError("it isn't audio this browser can play")) },
        )
    }
}

/** Adds [sound] to the library and, if there's room, to the current universe's sidebar. */
private fun addImported(sound: ImportedSound, message: String) {
    EditorState.importedSounds += sound
    val universe = EditorState.selectedUniverse
    val shape = universe.nextInstrumentShape()
    if (shape != null) {
        universe.instrumentTypes[shape] = sound
        showImportStatus("$message. It's in the sidebar.")
    } else {
        showImportStatus("$message. The sidebar is full: pick it from an instrument's dropdown, under Imported.")
    }
}

private fun importFailed(file: File, error: Any?) {
    val reason = (error as? ImportError)?.message ?: "it couldn't be read"
    window.alert("Couldn't import \"${file.name}\": $reason.")
}

private fun readBytes(file: File): Promise<Uint8Array> =
    file.asDynamic().arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().then { Uint8Array(it) }

/** Opens the browser's file picker for [accept] and calls [onPicked] with the chosen file. */
private fun pickFile(accept: String, onPicked: (File) -> Unit) {
    // Cancelling a picker fires no event, so old ones are tidied away on the next.
    document.querySelectorAll("input[data-import-picker]").asList().forEach { (it as HTMLInputElement).remove() }
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = accept
    // Some browsers (iOS Safari) only report the chosen file for an input that's on the page.
    input.style.display = "none"
    input.setAttribute("data-import-picker", "")
    document.body?.appendChild(input)
    input.onchange = {
        input.remove()
        input.files?.item(0)?.let(onPicked)
    }
    input.click()
}

/** The waveforms a preset's oscillators can use. */
private val WAVES = setOf("sine", "triangle", "sawtooth", "square")

// --- Song files -----------------------------------------------------------------------------

/**
 * Makes an imported sound again from its song-file JSON (see [ImportedSound.toJson]).
 * Throws [SongFileError] if it's malformed. A sample starts decoding straight away.
 */
fun importedSoundFromJson(entry: dynamic): ImportedSound {
    fun bad(): Nothing = throw SongFileError("it has an imported sound that can't be read")
    fun number(obj: dynamic, name: String): Double {
        val value: dynamic = if (obj == null) null else obj[name]
        if (jsTypeOf(value) != "number" || !(value as Double).isFinite()) bad()
        return value
    }
    fun text(obj: dynamic, name: String): String {
        val value: dynamic = if (obj == null) null else obj[name]
        if (jsTypeOf(value) != "string") bad()
        return value as String
    }
    fun list(obj: dynamic, name: String): List<dynamic> {
        val value: dynamic = if (obj == null) null else obj[name]
        if (!(js("Array").isArray(value) as Boolean)) bad()
        return (value as Array<dynamic>).toList()
    }
    if (jsTypeOf(entry) != "object" || entry == null) bad()
    return when (text(entry, "kind")) {
        "sample" -> SampleSound(text(entry, "label"), text(entry, "data")).also { sample ->
            sample.ready.catch { window.alert("The sample \"${sample.label}\" in this song couldn't be played in this browser.") }
        }
        "preset" -> PresetSound(
            label = text(entry, "label"),
            source = text(entry, "source"),
            gain = number(entry, "gain"),
            layers = list(entry, "layers").map { layer ->
                val filter = layer.filter
                PresetLayer(
                    oscillators = list(layer, "oscillators").map {
                        PresetOscillator(
                            wave = text(it, "wave").takeIf { w -> w in WAVES } ?: bad(),
                            voices = number(it, "voices").toInt().coerceIn(1, MAX_UNISON),
                            spread = number(it, "spread"),
                            detune = number(it, "detune"),
                            volume = number(it, "volume"),
                        )
                    },
                    attack = number(layer, "attack"),
                    decay = number(layer, "decay"),
                    sustain = number(layer, "sustain"),
                    release = number(layer, "release"),
                    filter = if (filter == null) null else PresetFilter(
                        text(filter, "type"), number(filter, "frequency"), number(filter, "q"), number(filter, "rolloff").toInt(),
                    ),
                    gain = number(layer, "gain"),
                )
            },
        )
        else -> bad()
    }
}
