package durhack26.music

import durhack26.music.tone.BitCrusher
import durhack26.music.tone.Distortion
import durhack26.music.tone.DuoSynth
import durhack26.music.tone.FMSynth
import durhack26.music.tone.Filter
import durhack26.music.tone.MembraneSynth
import durhack26.music.tone.MetalSynth
import durhack26.music.tone.MonoSynth
import durhack26.music.tone.NoiseSynth
import durhack26.music.tone.PluckSynth
import durhack26.music.tone.Synth
import durhack26.music.tone.ToneAudioNode

/** Groups for the instrument dropdown, shown as columns in this order. */
enum class InstrumentCategory(val label: String) {
    DRUMS("Drums"),
    BASS("Bass"),
    LEADS("Leads"),
    KEYS("Keys & pads"),
    FX("FX"),
}

/**
 * One note's sound: [synth] is what gets triggered, [output] is where its sound comes out
 * (the end of any built-in processing), and [nodes] is everything to dispose afterwards.
 */
class InstrumentSound(
    val synth: ToneAudioNode,
    val output: ToneAudioNode = synth,
    val nodes: List<ToneAudioNode> = listOf(synth),
)

/** [synth] wired through [processing] in order, e.g. a lead through its own distortion. */
private fun through(synth: ToneAudioNode, vararg processing: ToneAudioNode): InstrumentSound {
    (listOf(synth) + processing).zipWithNext { from, to -> from.connect(to) }
    return InstrumentSound(synth, processing.lastOrNull() ?: synth, listOf(synth) + processing)
}

/**
 * Every instrument type that can be added to a universe, and how it sounds at a level
 * (see levels.kt). Melodic types play the level's note, moved by [octaveShift] octaves,
 * held for [duration]. Drum types pick a drum by level instead.
 *
 * - [createSound] makes the Tone.js synth (and any built-in processing) for one note.
 *   Null if it makes no sound.
 * - [synthKey] says which synth a level needs: a note that moves to a level with a
 *   different key gets a new one. Melodic types use the same synth on every level.
 * - [play] triggers it at audio time `time`.
 * - [describe] names what a level plays, e.g. "E4" or "Snare".
 */
enum class InstrumentType(
    val label: String,
    val category: InstrumentCategory,
    val octaveShift: Int = 0,
    val duration: String = "8n",
) {
    // --- Drums ---------------------------------------------------------------------------
    DRUMS("Drums", InstrumentCategory.DRUMS) {
        override fun synthKey(level: Int) = DrumSound.at(level).ordinal
        override fun createSound(level: Int) = InstrumentSound(DrumSound.at(level).create())
        override fun play(sound: InstrumentSound, level: Int, time: Double) = DrumSound.at(level).play(sound.synth, time)
        override fun describe(level: Int) = DrumSound.at(level).label
    },
    CRUSHED_DRUMS("Crushed drums", InstrumentCategory.DRUMS) {
        override fun synthKey(level: Int) = DrumSound.at(level).ordinal
        override fun createSound(level: Int) = through(
            DrumSound.at(level).create(),
            Distortion(js("({ distortion: 0.8 })")),
            BitCrusher(js("({ bits: 6 })")),
        )
        override fun play(sound: InstrumentSound, level: Int, time: Double) = DrumSound.at(level).play(sound.synth, time)
        override fun describe(level: Int) = DrumSound.at(level).label
    },

    // --- Bass ----------------------------------------------------------------------------
    BASS_808("808", InstrumentCategory.BASS, octaveShift = -2, duration = "4n") {
        override fun createSound(level: Int) = through(
            MembraneSynth(js("({ pitchDecay: 0.08, octaves: 3, envelope: { attack: 0.001, decay: 1.2, sustain: 0, release: 0.4 } })")),
            Distortion(js("({ distortion: 0.35 })")),
        )
    },
    SUB("Sub", InstrumentCategory.BASS, octaveShift = -2) {
        override fun createSound(level: Int) = InstrumentSound(
            Synth(js("({ oscillator: { type: 'sine' }, envelope: { attack: 0.01, decay: 0.3, sustain: 0.8, release: 0.3 } })")),
        )
    },
    BASS("Bass", InstrumentCategory.BASS, octaveShift = -2) {
        override fun createSound(level: Int) = InstrumentSound(
            MonoSynth(js("({ oscillator: { type: 'sawtooth' }, envelope: { attack: 0.01, decay: 0.2, sustain: 0.4, release: 0.2 } })")),
        )
    },
    REESE("Reese", InstrumentCategory.BASS, octaveShift = -2, duration = "4n") {
        // Two slightly detuned saws beating against each other, darkened.
        override fun createSound(level: Int) = through(
            DuoSynth(js("({ harmonicity: 1.01, vibratoAmount: 0, volume: -6, voice0: { oscillator: { type: 'sawtooth' }, envelope: { attack: 0.01, sustain: 0.9, release: 0.3 } }, voice1: { oscillator: { type: 'sawtooth' }, envelope: { attack: 0.01, sustain: 0.9, release: 0.3 } } })")),
            Filter(js("({ frequency: 900, type: 'lowpass' })")),
        )
    },
    ACID("Acid", InstrumentCategory.BASS, octaveShift = -1, duration = "16n") {
        // A squelchy, resonant filter sweep on every note.
        override fun createSound(level: Int) = InstrumentSound(
            MonoSynth(js("({ oscillator: { type: 'sawtooth' }, filter: { Q: 12, type: 'lowpass' }, filterEnvelope: { attack: 0.001, decay: 0.15, sustain: 0.1, baseFrequency: 200, octaves: 4 }, envelope: { attack: 0.001, decay: 0.2, sustain: 0.3, release: 0.1 } })")),
        )
    },

    // --- Leads ---------------------------------------------------------------------------
    SYNTH("Synth", InstrumentCategory.LEADS) {
        override fun createSound(level: Int) = InstrumentSound(Synth(js("({ oscillator: { type: 'triangle' } })")))
    },
    SUPERSAW("Supersaw", InstrumentCategory.LEADS) {
        override fun createSound(level: Int) = InstrumentSound(
            Synth(js("({ oscillator: { type: 'fatsawtooth', count: 5, spread: 40 }, envelope: { attack: 0.01, decay: 0.2, sustain: 0.6, release: 0.6 }, volume: -8 })")),
        )
    },
    HOOVER("Hoover", InstrumentCategory.LEADS, octaveShift = -1, duration = "4n") {
        // Wide wobbling saws an octave apart: the classic rave hoover, roughly.
        override fun createSound(level: Int) = InstrumentSound(
            DuoSynth(js("({ harmonicity: 0.5, vibratoAmount: 0.4, vibratoRate: 5, volume: -10, voice0: { oscillator: { type: 'fatsawtooth' }, envelope: { attack: 0.05, release: 0.6 } }, voice1: { oscillator: { type: 'fatsawtooth' }, envelope: { attack: 0.05, release: 0.6 } } })")),
        )
    },
    CRUSHED_LEAD("Crushed lead", InstrumentCategory.LEADS) {
        // Blown-out and bit-crushed, for harsh digicore leads.
        override fun createSound(level: Int) = through(
            Synth(js("({ oscillator: { type: 'fatsquare', count: 3, spread: 20 }, envelope: { attack: 0.005, decay: 0.15, sustain: 0.5, release: 0.2 }, volume: -12 })")),
            Distortion(js("({ distortion: 0.9 })")),
            BitCrusher(js("({ bits: 5 })")),
        )
    },
    CHIP("Chip", InstrumentCategory.LEADS, octaveShift = 1, duration = "16n") {
        override fun createSound(level: Int) = InstrumentSound(
            Synth(js("({ oscillator: { type: 'square' }, envelope: { attack: 0.001, decay: 0.1, sustain: 0.3, release: 0.05 }, volume: -10 })")),
        )
    },
    VOX("Vox", InstrumentCategory.LEADS) {
        // A saw through a narrow band-pass, for a vowel-ish "ah".
        override fun createSound(level: Int) = through(
            Synth(js("({ oscillator: { type: 'sawtooth' }, envelope: { attack: 0.02, decay: 0.1, sustain: 0.7, release: 0.2 } })")),
            Filter(js("({ frequency: 900, type: 'bandpass', Q: 6 })")),
        )
    },

    // --- Keys & pads ---------------------------------------------------------------------
    RAVE_STAB("Rave stab", InstrumentCategory.KEYS) {
        override fun createSound(level: Int) = InstrumentSound(
            Synth(js("({ oscillator: { type: 'fatsquare', count: 3, spread: 25 }, envelope: { attack: 0.001, decay: 0.15, sustain: 0, release: 0.1 }, volume: -8 })")),
        )
    },
    RAVE_PIANO("Rave piano", InstrumentCategory.KEYS) {
        override fun createSound(level: Int) = InstrumentSound(
            FMSynth(js("({ harmonicity: 2, modulationIndex: 3, envelope: { attack: 0.001, decay: 0.6, sustain: 0.1, release: 0.5 }, modulationEnvelope: { attack: 0.001, decay: 0.4, sustain: 0 } })")),
        )
    },
    BELL("Bell", InstrumentCategory.KEYS, octaveShift = 1) {
        override fun createSound(level: Int) = InstrumentSound(
            FMSynth(js("({ harmonicity: 3.01, modulationIndex: 14, envelope: { attack: 0.001, decay: 1.5, sustain: 0, release: 1 }, modulationEnvelope: { attack: 0.001, decay: 0.8, sustain: 0 }, volume: -8 })")),
        )
    },
    PLUCK("Pluck", InstrumentCategory.KEYS) {
        override fun createSound(level: Int) = InstrumentSound(PluckSynth())
        override fun play(sound: InstrumentSound, level: Int, time: Double) {
            sound.synth.asDynamic().triggerAttack(frequencyOf(level, octaveShift), time)
        }
    },
    PAD("Pad", InstrumentCategory.KEYS, duration = "2n") {
        override fun createSound(level: Int) = InstrumentSound(
            Synth(js("({ oscillator: { type: 'fattriangle', count: 3, spread: 30 }, envelope: { attack: 0.4, decay: 0.2, sustain: 0.8, release: 1.5 }, volume: -8 })")),
        )
    },

    // --- FX ------------------------------------------------------------------------------
    ZAP("Zap", InstrumentCategory.FX, octaveShift = 1, duration = "16n") {
        // A fast downward pitch sweep: lasers and glitchy blips.
        override fun createSound(level: Int) = InstrumentSound(
            MembraneSynth(js("({ pitchDecay: 0.05, octaves: 6, envelope: { attack: 0.001, decay: 0.2, sustain: 0 }, volume: -6 })")),
        )
    },
    NOISE("Noise", InstrumentCategory.FX, duration = "4n") {
        // A swell of white noise; it has no pitch, so the level doesn't matter.
        override fun createSound(level: Int) = InstrumentSound(
            NoiseSynth(js("({ noise: { type: 'white' }, envelope: { attack: 0.3, decay: 0.6, sustain: 0 }, volume: -12 })")),
        )
        override fun play(sound: InstrumentSound, level: Int, time: Double) {
            sound.synth.asDynamic().triggerAttackRelease(duration, time)
        }
        override fun describe(level: Int) = "Noise"
    },
    SILENT("Silent", InstrumentCategory.FX) {
        override fun createSound(level: Int): InstrumentSound? = null
        override fun play(sound: InstrumentSound, level: Int, time: Double) {}
        override fun describe(level: Int) = "Silent"
    };

    open fun synthKey(level: Int) = 0
    abstract fun createSound(level: Int): InstrumentSound?

    open fun play(sound: InstrumentSound, level: Int, time: Double) {
        sound.synth.asDynamic().triggerAttackRelease(frequencyOf(level, octaveShift), duration, time)
    }

    open fun describe(level: Int) = noteName(level, octaveShift)
}

/**
 * The drum kit, one sound per level, bottom to top: low sounds low on screen, cymbals
 * high. It repeats every seven levels, whatever the scale.
 */
enum class DrumSound(val label: String) {
    KICK("Kick") {
        override fun create() = MembraneSynth()
        override fun play(synth: ToneAudioNode, time: Double) {
            synth.asDynamic().triggerAttackRelease("C1", "8n", time)
        }
    },
    SNARE("Snare") {
        override fun create() =
            NoiseSynth(js("({ noise: { type: 'white' }, envelope: { attack: 0.001, decay: 0.2, sustain: 0 } })"))
        override fun play(synth: ToneAudioNode, time: Double) {
            synth.asDynamic().triggerAttackRelease("16n", time)
        }
    },
    CLAP("Clap") {
        override fun create() =
            NoiseSynth(js("({ noise: { type: 'pink' }, envelope: { attack: 0.001, decay: 0.12, sustain: 0 } })"))
        override fun play(synth: ToneAudioNode, time: Double) {
            synth.asDynamic().triggerAttackRelease("32n", time)
        }
    },
    LOW_TOM("Low tom") {
        override fun create() = MembraneSynth(js("({ octaves: 4, pitchDecay: 0.1 })"))
        override fun play(synth: ToneAudioNode, time: Double) {
            synth.asDynamic().triggerAttackRelease("G2", "8n", time)
        }
    },
    HIGH_TOM("High tom") {
        override fun create() = MembraneSynth(js("({ octaves: 4, pitchDecay: 0.1 })"))
        override fun play(synth: ToneAudioNode, time: Double) {
            synth.asDynamic().triggerAttackRelease("D3", "8n", time)
        }
    },
    CLOSED_HAT("Closed hi-hat") {
        override fun create() =
            MetalSynth(js("({ envelope: { attack: 0.001, decay: 0.06, release: 0.01 }, volume: -14 })"))
        override fun play(synth: ToneAudioNode, time: Double) {
            synth.asDynamic().triggerAttackRelease(300, "32n", time)
        }
    },
    OPEN_HAT("Open hi-hat") {
        override fun create() =
            MetalSynth(js("({ envelope: { attack: 0.001, decay: 0.4, release: 0.2 }, volume: -16 })"))
        override fun play(synth: ToneAudioNode, time: Double) {
            synth.asDynamic().triggerAttackRelease(300, "8n", time)
        }
    };

    abstract fun create(): ToneAudioNode
    abstract fun play(synth: ToneAudioNode, time: Double)

    companion object {
        /** The drum on [level]. */
        fun at(level: Int) = entries[level % entries.size]
    }
}
