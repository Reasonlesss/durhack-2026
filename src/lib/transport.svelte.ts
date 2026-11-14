/**
 * The clock. Ticks (12 per beat) are scheduled against AudioContext.currentTime with a
 * lookahead, so timing never drifts and tempo changes apply from the next tick. Each tick
 * asks the chain runner what's due and plays it.
 */
import { audio, click, outputDelay, playNote, pruneSends } from './audio/engine';
import { ChainRunner, graphOf } from './chain';
import { effectPolygon, effectsFor } from './effects';
import { TICKS_PER_BEAT, rowOf } from './music';
import { songStore } from './song.svelte';
import { BEATS_PER_BAR, type Canvas, type Harmony, type Note } from './types';
import { vis } from './vis';

const LOOKAHEAD_S = 0.12;
const PUMP_MS = 25;
/** Swing words → how late the off-beats land, as a fraction of a beat. */
export const SWING = { tight: 0, loose: 0.08, lazy: 0.16 } as const;
export type Swing = keyof typeof SWING;

export interface TransportHooks {
	onTick?: (tick: { beat: number; time: number }) => void;
}

class Transport {
	isPlaying = $state(false);
	metronome = $state(false);
	/** Has the user ever pressed play? (Drives the heartbeat's invite ring.) */
	everPlayed = $state(false);
	/** Smooth, fractional beat since play; drives the heartbeat pulse. */
	beat = $state(0);
	hooks: TransportHooks = {};

	private timer: ReturnType<typeof setInterval> | undefined;
	private raf = 0;
	private tick = 0;
	private nextTime = 0;
	private queue: { time: number; tick: number }[] = [];
	private runner = new ChainRunner();

	play = async () => {
		if (this.isPlaying) return;
		const ctx = audio();
		await ctx.resume();
		this.tick = 0;
		this.nextTime = ctx.currentTime + 0.06;
		this.queue = [];
		this.runner.reset();
		this.isPlaying = true;
		this.everPlayed = true;
		this.timer = setInterval(this.schedule, PUMP_MS);
		this.schedule();
		this.raf = requestAnimationFrame(this.frame);
	};

	stop = () => {
		if (!this.isPlaying) return;
		clearInterval(this.timer);
		cancelAnimationFrame(this.raf);
		this.isPlaying = false;
		this.queue = [];
	};

	toggle = () => (this.isPlaying ? this.stop() : this.play());

	/** Fill the lookahead window. Runs on a timer, but all times come from the audio clock. */
	private schedule = () => {
		const ctx = audio();
		while (this.nextTime < ctx.currentTime + LOOKAHEAD_S) {
			const { bpm, harmony, canvas, swing } = songStore.song;
			const tickDur = 60 / bpm / TICKS_PER_BEAT;
			const time = this.nextTime + swingOffset(this.tick, swing ?? 'tight', bpm);
			const due = this.runner.step(graphOf(canvas, songStore.version), this.tick);
			const sounding = due.filter((d) => d.kind === 'note' && d.note.inst !== 'pause').length;
			for (const d of due) {
				if (d.kind === 'note') sound(canvas, d.note, time, harmony, bpm, 1 / Math.sqrt(Math.max(1, sounding)));
				else vis.push({ kind: 'line', id: d.line.id, at: time + outputDelay(), dur: d.ticks * tickDur });
			}
			if (this.metronome && this.tick % TICKS_PER_BEAT === 0) click(time, this.tick % (TICKS_PER_BEAT * BEATS_PER_BAR) === 0);
			this.hooks.onTick?.({ beat: this.tick / TICKS_PER_BEAT, time });
			this.queue.push({ time: this.nextTime, tick: this.tick });
			this.tick++;
			this.nextTime += tickDur;
		}
	};

	/** Track what the listener hears right now. */
	private frame = () => {
		const ctx = audio();
		const now = ctx.currentTime - outputDelay();
		while (this.queue.length > 1 && this.queue[1].time <= now) this.queue.shift();
		const head = this.queue[0];
		if (head && head.time <= now) {
			const tickDur = 60 / songStore.song.bpm / TICKS_PER_BEAT;
			this.beat = (head.tick + Math.min(0.999, (now - head.time) / tickDur)) / TICKS_PER_BEAT;
		}
		vis.prune(ctx.currentTime);
		this.raf = requestAnimationFrame(this.frame);
	};
}

/** Play one note plus its visuals (note flash, effect flashes). */
function sound(canvas: Canvas, note: Note, time: number, harmony: Harmony, bpm: number, gain = 1, short = false) {
	const fx = effectsFor(note, canvas.effects, polys(canvas));
	playNote(note, rowOf(note.y), { time, harmony, fx, gain, bpm, short });
	if (note.inst === 'pause') return;
	const at = time + outputDelay();
	vis.push({ kind: 'note', id: note.id, at, dur: 0.22 });
	for (const h of fx) vis.push({ kind: 'effect', id: h.effect.id, at, dur: 0.3 });
}

let polyCache: { version: number; map: Map<string, [number, number][]> } | null = null;
function polys(canvas: Canvas) {
	if (polyCache?.version === songStore.version) return polyCache.map;
	const map = new Map(canvas.effects.map((e) => [e.id, effectPolygon(e)]));
	polyCache = { version: songStore.version, map };
	pruneSends(new Set(canvas.effects.map((e) => e.id)));
	return map;
}

/** Hear a note right away (placing, tapping, dragging across rows). */
export function preview(note: Note, short = false) {
	const ctx = audio();
	ctx.resume();
	const { canvas, harmony, bpm } = songStore.song;
	sound(canvas, note, ctx.currentTime + 0.01, harmony, bpm, 1, short);
}

/** Hear a chord for a harmony (the pad, while stopped). */
export function previewHarmony(h: Harmony) {
	const ctx = audio();
	ctx.resume();
	const n: Note = { id: 'preview', inst: 'chords', x: 720, y: 0, size: 24, rot: 120 };
	playNote(n, 6, { time: ctx.currentTime + 0.01, harmony: h, fx: [], bpm: songStore.song.bpm });
}

function swingOffset(tick: number, swing: Swing, bpm: number) {
	const beat = 60 / bpm;
	if (tick % TICKS_PER_BEAT === TICKS_PER_BEAT / 2) return SWING[swing] * beat;
	if (tick % (TICKS_PER_BEAT / 2) === TICKS_PER_BEAT / 4) return (SWING[swing] * beat) / 2;
	return 0;
}

export const transport = new Transport();
