@file:JsModule("standardized-audio-context")
@file:JsNonModule

package durhack26.music.tone

/*
 * The AudioContext Tone.js is built on (from standardized-audio-context, which Tone.js
 * itself uses). Made directly only to choose its sample rate, which Tone.js doesn't expose;
 * it must be this class rather than the browser's own, or Tone.js's nodes won't accept it.
 */
@JsName("AudioContext")
external class StandardAudioContext(options: dynamic)
