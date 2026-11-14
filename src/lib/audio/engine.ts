/**
 * The synth. Every sound is generated here with plain Web Audio nodes; no samples.
 *
 * Signal path per note:
 *   voice → pan → [series effects: muffle / crunch / distortion, blended by strength]
 *         → dry bus ─────────────────────────────┐
 *         → sends: dreamy (reverb), echo (delay) ─┴→ master → limiter → speakers
 */
import { decayOf, mtof, panOf, velocity, voice, type DrumKind } from '../music';
import type { Effect, Harmony, Note } from '../types';
import { effectParams } from '../effects';

const MAX_VOICES = 56;

export interface FxHit {
	effect: Effect;
	/** 0.4 at the edge → 1 at the centre. */
	strength: number;
}

let ctx: AudioContext | null = null;
let master: GainNode;
let dryBus: GainNode;
let noise: AudioBuffer;
const irCache = new Map<number, AudioBuffer>();
const shaperCurves = new Map<string, Float32Array<ArrayBuffer>>();
/** Long-lived send effects, one per effect outline id. */
const sends = new Map<string, { input: GainNode; update: (e: Effect, bpm: number) => void }>();
/** Live voices, oldest first, for voice stealing. */
const live: { out: GainNode; end: number }[] = [];

export function audio(): AudioContext {
	if (ctx) return ctx;
	ctx = new AudioContext({ latencyHint: 'interactive' });
	const limiter = ctx.createDynamicsCompressor();
	limiter.threshold.value = -8;
	limiter.knee.value = 2;
	limiter.ratio.value = 20;
	limiter.attack.value = 0.002;
	limiter.release.value = 0.12;
	master = ctx.createGain();
	master.gain.value = 0.7;
	master.connect(limiter).connect(ctx.destination);
	dryBus = ctx.createGain();
	dryBus.connect(master);
	noise = makeNoise(ctx, 2);
	return ctx;
}

/** Seconds between scheduling a sound and hearing it; used to line visuals up with audio. */
export const outputDelay = () => (ctx ? (ctx.outputLatency || ctx.baseLatency || 0) : 0);

export interface PlayOpts {
	time: number;
	harmony: Harmony;
	fx: FxHit[];
	/** Extra gain, e.g. 1/√n when several notes land together. */
	gain?: number;
	bpm: number;
	/** Shorter preview sound (dragging a note across rows). */
	short?: boolean;
}

/** Schedule one note. */
export function playNote(n: Note, row: number, o: PlayOpts) {
	if (n.inst === 'pause') return;
	const c = audio();
	const t = Math.max(o.time, c.currentTime);
	const vel = velocity(n.size) * (o.gain ?? 1);
	const decay = o.short ? 0.18 : decayOf(n.rot);
	const v = voice(n.inst, row, o.harmony);

	const out = c.createGain();
	const pan = c.createStereoPanner();
	pan.pan.value = panOf(n.x);
	out.connect(pan);
	const end = connectFx(pan, o.fx, o.bpm);

	let stop: number;
	if (v.drum) stop = drum(c, out, t, v.drum.kind, v.drum.tune, vel, n.size);
	else stop = synth(c, out, t, n.inst, v.midi, vel, decay, n.rot);
	steal(out, stop, t);
	return end;
}

// ---- voices ----------------------------------------------------------------------

function env(c: AudioContext, g: GainNode, t: number, peak: number, attack: number, decay: number) {
	g.gain.setValueAtTime(0.0001, t);
	g.gain.linearRampToValueAtTime(peak, t + attack);
	g.gain.exponentialRampToValueAtTime(0.0001, t + attack + decay);
	return t + attack + decay + 0.02;
}

function osc(c: AudioContext, type: OscillatorType, freq: number, t: number, stop: number, dest: AudioNode, detune = 0) {
	const o = c.createOscillator();
	o.type = type;
	o.frequency.value = freq;
	o.detune.value = detune;
	o.connect(dest);
	o.start(t);
	o.stop(stop);
	return o;
}

function lowpass(c: AudioContext, freq: number, q = 0.7) {
	const f = c.createBiquadFilter();
	f.type = 'lowpass';
	f.frequency.value = freq;
	f.Q.value = q;
	return f;
}

function synth(c: AudioContext, out: GainNode, t: number, inst: Note['inst'], midi: number[], vel: number, decay: number, rot: number): number {
	const g = c.createGain();
	g.connect(out);
	switch (inst) {
		case 'lead': {
			const stop = env(c, g, t, 0.32 * vel, 0.008, decay);
			osc(c, 'triangle', mtof(midi[0]), t, stop, g);
			return stop;
		}
		case 'bells': {
			const d = Math.max(0.8, decay);
			const stop = env(c, g, t, 0.26 * vel, 0.003, d);
			osc(c, 'sine', mtof(midi[0]), t, stop, g);
			// Inharmonic partial gives the metallic strike, then fades fast.
			const p = c.createGain();
			p.connect(g);
			env(c, p, t, 0.6, 0.002, 0.35);
			osc(c, 'sine', mtof(midi[0]) * 2.76, t, t + 0.4, p);
			return stop;
		}
		case 'bass': {
			const f = lowpass(c, 700);
			f.connect(g);
			const stop = env(c, g, t, 0.3 * vel, 0.006, decay);
			osc(c, 'square', mtof(midi[0]), t, stop, f);
			return stop;
		}
		case 'chords': {
			const f = lowpass(c, 2600);
			f.connect(g);
			const stop = env(c, g, t, (0.34 * vel) / Math.sqrt(midi.length), 0.03, decay + 0.15);
			for (const m of midi) osc(c, 'sawtooth', mtof(m), t, stop, f);
			return stop;
		}
		case 'stab': {
			const f = lowpass(c, 7000, 4);
			f.frequency.setValueAtTime(7000, t);
			f.frequency.exponentialRampToValueAtTime(900, t + 0.22);
			f.connect(g);
			const stop = env(c, g, t, (0.3 * vel) / Math.sqrt(midi.length * 3), 0.004, Math.max(0.25, decay));
			for (const m of midi) for (const d of [-14, 0, 14]) osc(c, 'sawtooth', mtof(m), t, stop, f, d);
			return stop;
		}
		case 'acid': {
			const q = 8 + (rot / 360) * 16;
			const f = lowpass(c, 500 + vel * 5200, q);
			const d = Math.max(0.12, Math.min(0.45, decay));
			f.frequency.setValueAtTime(500 + vel * 5200, t);
			f.frequency.exponentialRampToValueAtTime(220, t + d);
			f.connect(g);
			const stop = env(c, g, t, 0.22 * Math.max(0.5, vel), 0.004, d);
			osc(c, 'sawtooth', mtof(midi[0]), t, stop, f);
			return stop;
		}
		case 'siren': {
			const d = Math.max(1.4, decay);
			const f = lowpass(c, 2600);
			f.connect(g);
			const stop = env(c, g, t, 0.2 * vel, 0.05, d);
			const o = osc(c, 'sawtooth', mtof(midi[0]), t, stop, f);
			const base = mtof(midi[0]);
			o.frequency.setValueAtTime(base, t);
			o.frequency.exponentialRampToValueAtTime(base * 2, t + d / 2);
			o.frequency.exponentialRampToValueAtTime(base, t + d);
			return stop;
		}
		default:
			return t;
	}
}

function noiseSource(c: AudioContext, t: number, stop: number, dest: AudioNode) {
	const s = c.createBufferSource();
	s.buffer = noise;
	s.loop = true;
	s.connect(dest);
	s.start(t, Math.random() * 1.5);
	s.stop(stop);
}

function drum(c: AudioContext, out: GainNode, t: number, kind: DrumKind, tune: number, vel: number, size: number): number {
	if (kind === 'kick') {
		const base = 42 + tune * 28;
		const len = 0.25 + ((size - 8) / 32) * 0.45;
		const g = c.createGain();
		g.connect(out);
		const stop = env(c, g, t, 0.9 * vel, 0.002, len);
		const o = osc(c, 'sine', base * 2.6, t, stop, g);
		o.frequency.setValueAtTime(base * 2.6, t);
		o.frequency.exponentialRampToValueAtTime(base, t + 0.09);
		const click = c.createGain();
		click.connect(out);
		env(c, click, t, 0.25 * vel, 0.001, 0.012);
		noiseSource(c, t, t + 0.02, click);
		return stop;
	}
	if (kind === 'snare') {
		const bp = c.createBiquadFilter();
		bp.type = 'bandpass';
		bp.frequency.value = 1300 + tune * 600;
		bp.Q.value = 0.8;
		const ng = c.createGain();
		bp.connect(ng).connect(out);
		const stop = env(c, ng, t, 0.6 * vel, 0.001, 0.16 + tune * 0.06);
		noiseSource(c, t, stop, bp);
		const body = c.createGain();
		body.connect(out);
		env(c, body, t, 0.35 * vel, 0.001, 0.07);
		osc(c, 'triangle', 180 + tune * 60, t, t + 0.1, body);
		return stop;
	}
	const hp = c.createBiquadFilter();
	hp.type = 'highpass';
	hp.frequency.value = 5000 + tune * 4000;
	const g = c.createGain();
	hp.connect(g).connect(out);
	const stop = env(c, g, t, 0.32 * vel, 0.001, 0.035 + (1 - tune) * 0.03);
	noiseSource(c, t, stop, hp);
	return stop;
}

// ---- effects -------------------------------------------------------------------

/** Route `src` through the note's effects; series first, then sends. Returns the final node. */
function connectFx(src: AudioNode, fx: FxHit[], bpm: number): AudioNode {
	const c = ctx!;
	let node: AudioNode = src;
	const series = fx.filter((f) => f.effect.kind === 'muffle' || f.effect.kind === 'crunch' || f.effect.kind === 'distortion');
	for (const { effect, strength } of series) node = seriesFx(c, node, effect, strength);
	node.connect(dryBus);
	for (const { effect, strength } of fx) {
		if (effect.kind !== 'dreamy' && effect.kind !== 'echo') continue;
		const send = sendFor(effect, bpm);
		const g = c.createGain();
		g.gain.value = strength;
		node.connect(g).connect(send.input);
	}
	return node;
}

/** A per-voice wet/dry blend of a series effect. */
function seriesFx(c: AudioContext, input: AudioNode, e: Effect, s: number): AudioNode {
	const p = effectParams(e);
	const out = c.createGain();
	if (e.kind === 'muffle') {
		// Blend the cutoff in log space: weak edge = barely muffled.
		const f = lowpass(c, Math.exp(Math.log(18000) * (1 - s) + Math.log(p.cutoff!) * s), p.q);
		input.connect(f).connect(out);
		return out;
	}
	const dry = c.createGain();
	dry.gain.value = 1 - s;
	input.connect(dry).connect(out);
	const wet = c.createGain();
	wet.gain.value = s;
	const pre = c.createGain();
	pre.gain.value = p.drive!;
	const shaper = c.createWaveShaper();
	shaper.curve = curve(e.kind === 'crunch' ? 'soft' : 'hard');
	shaper.oversample = '4x';
	const tone = lowpass(c, p.tone!);
	const make = c.createGain();
	make.gain.value = p.makeup!;
	if (e.kind === 'distortion') {
		const hp = c.createBiquadFilter();
		hp.type = 'highpass';
		hp.frequency.value = 70;
		input.connect(pre).connect(shaper).connect(hp).connect(tone).connect(make).connect(wet).connect(out);
	} else {
		input.connect(pre).connect(shaper).connect(tone).connect(make).connect(wet).connect(out);
	}
	return out;
}

function sendFor(e: Effect, bpm: number) {
	let s = sends.get(e.id);
	if (!s) {
		s = e.kind === 'dreamy' ? makeReverb(ctx!) : makeEcho(ctx!);
		sends.set(e.id, s);
	}
	s.update(e, bpm);
	return s;
}

function makeReverb(c: AudioContext) {
	const input = c.createGain();
	const conv = c.createConvolver();
	const tone = lowpass(c, 6000);
	const ret = c.createGain();
	input.connect(conv).connect(tone).connect(ret).connect(master);
	let room = -1;
	return {
		input,
		update(e: Effect) {
			const p = effectParams(e);
			if (p.room !== room) {
				room = p.room!;
				conv.buffer = impulse(c, [1.2, 2.8, 5, 9][room]);
			}
			input.gain.value = p.amount!;
			tone.frequency.setTargetAtTime(p.tone!, c.currentTime, 0.05);
		}
	};
}

function makeEcho(c: AudioContext) {
	const input = c.createGain();
	const delay = c.createDelay(4);
	const fb = c.createGain();
	const tone = lowpass(c, 4500);
	input.connect(delay);
	delay.connect(tone).connect(fb).connect(delay);
	tone.connect(master);
	return {
		input,
		update(e: Effect, bpm: number) {
			const p = effectParams(e);
			delay.delayTime.setTargetAtTime((60 / bpm) * p.beats!, c.currentTime, 0.02);
			fb.gain.setTargetAtTime(p.feedback!, c.currentTime, 0.02);
		}
	};
}

/** Remove send nodes for outlines that no longer exist. */
export function pruneSends(alive: Set<string>) {
	for (const [id, s] of sends) if (!alive.has(id)) (s.input.disconnect(), sends.delete(id));
}

// ---- helpers -------------------------------------------------------------------

/** Fade out the oldest voice when too many are ringing. */
function steal(out: GainNode, end: number, t: number) {
	const now = ctx!.currentTime;
	for (let i = live.length - 1; i >= 0; i--) if (live[i].end < now) live.splice(i, 1);
	while (live.length >= MAX_VOICES) {
		const v = live.shift()!;
		v.out.gain.setTargetAtTime(0, Math.max(now, t - 0.01), 0.01);
	}
	live.push({ out, end });
}

function makeNoise(c: AudioContext, seconds: number) {
	const b = c.createBuffer(1, c.sampleRate * seconds, c.sampleRate);
	const d = b.getChannelData(0);
	for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
	return b;
}

/** Stereo decaying noise: a room that rings for `seconds`. Generated once per length. */
function impulse(c: AudioContext, seconds: number) {
	const key = seconds;
	const hit = irCache.get(key);
	if (hit) return hit;
	const len = Math.floor(c.sampleRate * seconds);
	const b = c.createBuffer(2, len, c.sampleRate);
	for (let ch = 0; ch < 2; ch++) {
		const d = b.getChannelData(ch);
		for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 3.2);
	}
	irCache.set(key, b);
	return b;
}

/** Precomputed shaper curves: gentle tanh for crunch, asymmetric hard clip for distortion. */
function curve(kind: 'soft' | 'hard') {
	const hit = shaperCurves.get(kind);
	if (hit) return hit;
	const n = 4096;
	const a = new Float32Array(new ArrayBuffer(n * 4));
	for (let i = 0; i < n; i++) {
		const x = (i / (n - 1)) * 2 - 1;
		a[i] = kind === 'soft' ? Math.tanh(x * 1.5) / Math.tanh(1.5) : Math.max(-0.8, Math.min(0.95, Math.tanh(x * (x > 0 ? 1.2 : 1.6))));
	}
	shaperCurves.set(kind, a);
	return a;
}

/** Short metronome tick. */
export function click(time: number, accent: boolean) {
	const c = audio();
	const g = c.createGain();
	g.connect(master);
	env(c, g, time, accent ? 0.12 : 0.06, 0.001, 0.04);
	osc(c, 'sine', accent ? 1500 : 1000, time, time + 0.06, g);
}
