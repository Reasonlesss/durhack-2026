@file:JsModule("tone")
@file:JsNonModule

package durhack26.music.tone

import kotlin.js.Promise

/*
 * Bindings for the parts of Tone.js (https://tonejs.github.io) the app uses. Only what's
 * called is declared; options objects and audio params are left `dynamic`.
 */

/** Unlocks audio. Browsers only allow this from a click or key press. */
external fun start(): Promise<Unit>

/** The current audio time, in seconds. */
external fun now(): Double

/** Makes [context] the one every Tone.js node and the transport use; [disposeOld] closes the previous one. */
external fun setContext(context: Context, disposeOld: Boolean = definedExternally)

/** Tone.js's wrapper around an AudioContext: adds scheduling ([lookAhead]) on top. */
external class Context(options: dynamic)

/** The shared clock that repeating callbacks are scheduled on. */
external fun getTransport(): Transport

external interface Transport {
    /**
     * Calls [callback] every [interval] with the exact audio time to play at. The interval is
     * Tone.js time: seconds, or e.g. "8i" for 8 transport ticks, which follows [bpm].
     */
    fun scheduleRepeat(callback: (time: Double) -> Unit, interval: String): Int

    /** The tempo, in beats per minute. Set `bpm.value`; playback follows straight away. */
    val bpm: dynamic

    /** Transport ticks per beat (Tone.js's default is 192). */
    @JsName("PPQ")
    val ppq: Int
    fun clear(id: Int)
    fun start()
    fun stop()
}

open external class ToneAudioNode {
    fun connect(destination: ToneAudioNode): ToneAudioNode
    fun toDestination(): ToneAudioNode
    /** Disconnects every output. */
    fun disconnect(): ToneAudioNode
    fun dispose()
}

external class Synth(options: dynamic = definedExternally) : ToneAudioNode
external class MembraneSynth(options: dynamic = definedExternally) : ToneAudioNode
external class NoiseSynth(options: dynamic = definedExternally) : ToneAudioNode
external class MetalSynth(options: dynamic = definedExternally) : ToneAudioNode
external class MonoSynth(options: dynamic = definedExternally) : ToneAudioNode
external class PluckSynth(options: dynamic = definedExternally) : ToneAudioNode
external class FMSynth(options: dynamic = definedExternally) : ToneAudioNode
external class DuoSynth(options: dynamic = definedExternally) : ToneAudioNode

external class Reverb(options: dynamic = definedExternally) : ToneAudioNode
external class FeedbackDelay(options: dynamic = definedExternally) : ToneAudioNode
external class Distortion(options: dynamic = definedExternally) : ToneAudioNode
external class Filter(options: dynamic = definedExternally) : ToneAudioNode
external class Chorus(options: dynamic = definedExternally) : ToneAudioNode
external class Tremolo(options: dynamic = definedExternally) : ToneAudioNode
external class Compressor(options: dynamic = definedExternally) : ToneAudioNode

external class Gain(options: dynamic = definedExternally) : ToneAudioNode

/** Mixes input [a] (fade 0) with input [b] (fade 1), keeping the loudness even. */
external class CrossFade(fade: Double = definedExternally) : ToneAudioNode {
    val a: ToneAudioNode
    val b: ToneAudioNode
    val fade: dynamic
}

/** Maps each sample through [mapping] (input -1..1 → output), worked out at [length] points. */
external class WaveShaper(mapping: (Double) -> Double, length: Int = definedExternally) : ToneAudioNode {
    fun setMap(mapping: (Double) -> Double, length: Int = definedExternally): WaveShaper
}
