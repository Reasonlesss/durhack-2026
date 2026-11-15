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

/** The shared clock that repeating callbacks are scheduled on. */
external fun getTransport(): Transport

external interface Transport {
    /** Calls [callback] every [interval] seconds with the exact audio time to play at. */
    fun scheduleRepeat(callback: (time: Double) -> Unit, interval: Double): Int
    fun clear(id: Int)
    fun start()
    fun stop()
}

open external class ToneAudioNode {
    fun connect(destination: ToneAudioNode): ToneAudioNode
    fun toDestination(): ToneAudioNode
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
external class BitCrusher(options: dynamic = definedExternally) : ToneAudioNode
external class Chorus(options: dynamic = definedExternally) : ToneAudioNode
external class Tremolo(options: dynamic = definedExternally) : ToneAudioNode
external class Limiter(options: dynamic = definedExternally) : ToneAudioNode
