/**
 * The clock. Ticks (12 per beat) are scheduled against AudioContext.currentTime with a
 * lookahead, so timing never drifts. Tempo changes apply from the next tick.
 *
 * Two modes:
 * - song: walks the sections; parts switched on in the composer run their chains, and the
 *   section's harmony applies.
 * - part: loops one part's canvas forever with the song's default harmony.
 */
import { audio, click, outputDelay, playNote, pruneSends } from './audio/engine';
import { ChainRunner, graphOf, type Due } from './chain';
import { effectPolygon, effectsFor } from './effects';
import { TICKS_PER_BEAT, rowOf } from './music';
import { songStore } from './song.svelte';
import { BEATS_PER_BAR, type Canvas, type Harmony, type Note, type Part, type Section } from './types';
import { vis } from './vis';

const LOOKAHEAD_S = 0.12;
const PUMP_MS = 25;
/** Swing words → how late the off-beats land, as a fraction of a beat. */
export const SWING = { tight: 0, loose: 0.08, lazy: 0.16 } as const;
export type Swing = keyof typeof SWING;

export interface Position {
	sectionIndex: number;
	bar: number;
	beat: number;
}

export interface TransportHooks {
	onTick?: (tick: { songBeat: number; time: number }) => void;
	onSectionEnter?: (section: Section, activeParts: Part[], harmony: Harmony, time: number) => void;
}

type Mode = { kind: 'song' } | { kind: 'part'; partId: string };

class Transport {
	isPlaying = $state(false);
	metronome = $state(false);
	mode = $state<Mode>({ kind: 'song' });
	/** Has the user ever pressed play? (Drives the heartbeat's invite ring.) */
	everPlayed = $state(false);
	/** Smooth, fractional beat; in song mode, from the song start. */
	songBeat = $state(0);
	position = $derived(positionAt(Math.floor(this.songBeat)));
	hooks: TransportHooks = {};

	private timer: ReturnType<typeof setInterval> | undefined;
	private raf = 0;
	private tick = 0;
	private nextTime = 0;
	private queue: { time: number; tick: number }[] = [];
	private sectionId: string | null = null;
	private sectionStart = 0;
	private runners = new Map<string, ChainRunner>();

	/** Start playing. Pass a part id to loop just that part's canvas. */
	play = async (partId?: string) => {
		const mode: Mode = partId ? { kind: 'part', partId } : { kind: 'song' };
		if (this.isPlaying && sameMode(mode, this.mode)) return;
		if (this.isPlaying) this.halt();
		this.mode = mode;
		const ctx = audio();
		await ctx.resume();
		const { start, end } = loopRange();
		if (mode.kind === 'part') this.tick = 0;
		else {
			const at = Math.floor(this.songBeat / BEATS_PER_BAR) * BEATS_PER_BAR * TICKS_PER_BEAT;
			this.tick = at >= start && at < end ? at : start;
		}
		this.nextTime = ctx.currentTime + 0.06;
		this.queue = [];
		this.sectionId = null;
		this.runners.forEach((r) => r.reset());
		this.isPlaying = true;
		this.everPlayed = true;
		this.timer = setInterval(this.schedule, PUMP_MS);
		this.schedule();
		this.raf = requestAnimationFrame(this.frame);
	};

	stop = () => {
		if (!this.isPlaying) return;
		this.halt();
		if (this.mode.kind === 'song') this.songBeat = songStore.timeline.starts[this.position.sectionIndex] ?? 0;
	};

	private halt() {
		clearInterval(this.timer);
		cancelAnimationFrame(this.raf);
		this.isPlaying = false;
		this.queue = [];
	}

	toggle = (partId?: string) => (this.isPlaying ? this.stop() : this.play(partId));

	seek = (sectionId: string) => {
		const i = songStore.song.sections.findIndex((s) => s.id === sectionId);
		if (i < 0) return;
		const beat = songStore.timeline.starts[i];
		this.songBeat = beat;
		if (this.isPlaying && this.mode.kind === 'song') {
			this.tick = beat * TICKS_PER_BEAT;
			this.nextTime = audio().currentTime + 0.05;
			this.queue = [];
			this.sectionId = null;
		}
	};

	private runner(partId: string) {
		return this.runners.get(partId) ?? this.runners.set(partId, new ChainRunner()).get(partId)!;
	}

	/** Fill the lookahead window. Runs on a timer, but all times come from the audio clock. */
	private schedule = () => {
		const ctx = audio();
		while (this.nextTime < ctx.currentTime + LOOKAHEAD_S) {
			const bpm = songStore.song.bpm;
			const tickDur = 60 / bpm / TICKS_PER_BEAT;
			let parts: Part[];
			let harmony: Harmony;
			let local: number;

			if (this.mode.kind === 'song') {
				const { start, end } = loopRange();
				if (this.tick >= end || this.tick < start) this.tick = start;
				const pos = positionAt(Math.floor(this.tick / TICKS_PER_BEAT));
				const section = songStore.song.sections[pos.sectionIndex];
				const atStart = this.tick === songStore.timeline.starts[pos.sectionIndex] * TICKS_PER_BEAT;
				// New section (or a loop wrapping back to the same one): everything restarts together.
				if (section.id !== this.sectionId || atStart) {
					this.sectionId = section.id;
					this.sectionStart = this.tick;
					this.runners.forEach((r) => r.reset());
					this.hooks.onSectionEnter?.(section, songStore.activeParts[section.id] ?? [], songStore.harmonyOf(section), this.nextTime);
				}
				parts = songStore.activeParts[section.id] ?? [];
				harmony = songStore.harmonyOf(section);
				local = this.tick - this.sectionStart;
			} else {
				const p = songStore.part(this.mode.partId);
				parts = p ? [p] : [];
				harmony = songStore.song.harmony;
				local = this.tick;
			}

			const time = this.nextTime + swingOffset(this.tick, songStore.song.swing ?? 'tight', bpm);
			const due: { part: Part; d: Due }[] = [];
			for (const part of parts) {
				for (const d of this.runner(part.id).step(graphOf(part.canvas, songStore.version), local)) due.push({ part, d });
			}
			const sounding = due.filter((x) => x.d.kind === 'note' && x.d.note.inst !== 'pause').length;
			for (const { part, d } of due) {
				if (d.kind === 'note') sound(part.id, part.canvas, d.note, time, harmony, bpm, 1 / Math.sqrt(Math.max(1, sounding)));
				else vis.push({ partId: part.id, kind: 'line', id: d.line.id, at: time + outputDelay(), dur: d.ticks * tickDur });
			}

			if (this.metronome && this.tick % TICKS_PER_BEAT === 0) click(time, this.tick % (TICKS_PER_BEAT * BEATS_PER_BAR) === 0);
			this.hooks.onTick?.({ songBeat: this.tick / TICKS_PER_BEAT, time });
			this.queue.push({ time: this.nextTime, tick: this.tick });
			this.tick++;
			this.nextTime += tickDur;
		}
	};

	/** Move the playhead to what the listener hears right now. */
	private frame = () => {
		const ctx = audio();
		const now = ctx.currentTime - outputDelay();
		while (this.queue.length > 1 && this.queue[1].time <= now) this.queue.shift();
		const head = this.queue[0];
		if (head && head.time <= now) {
			const tickDur = 60 / songStore.song.bpm / TICKS_PER_BEAT;
			this.songBeat = (head.tick + Math.min(0.999, (now - head.time) / tickDur)) / TICKS_PER_BEAT;
		}
		vis.prune(ctx.currentTime);
		this.raf = requestAnimationFrame(this.frame);
	};
}

/** Play one note now-ish plus its visuals. */
function sound(partId: string, canvas: Canvas, note: Note, time: number, harmony: Harmony, bpm: number, gain = 1, short = false) {
	const fx = effectsFor(note, canvas.effects, polys(canvas));
	playNote(note, rowOf(note.y), { time, harmony, fx, gain, bpm, short });
	const at = time + outputDelay();
	vis.push({ partId, kind: 'note', id: note.id, at, dur: 0.22 });
	for (const h of fx) vis.push({ partId, kind: 'effect', id: h.effect.id, at, dur: 0.3 });
}

const polyCache = new WeakMap<Canvas, { version: number; map: Map<string, [number, number][]> }>();
function polys(canvas: Canvas) {
	const hit = polyCache.get(canvas);
	if (hit && hit.version === songStore.version) return hit.map;
	const map = new Map(canvas.effects.map((e) => [e.id, effectPolygon(e)]));
	polyCache.set(canvas, { version: songStore.version, map });
	pruneSends(new Set(songStore.song.parts.flatMap((p) => p.canvas.effects.map((e) => e.id))));
	return map;
}

/** Hear a note right away (placing, tapping, dragging across rows). */
export function preview(partId: string, note: Note, short = false) {
	const part = songStore.part(partId);
	if (!part) return;
	const ctx = audio();
	ctx.resume();
	sound(partId, part.canvas, note, ctx.currentTime + 0.01, songStore.song.harmony, songStore.song.bpm, 1, short);
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

const sameMode = (a: Mode, b: Mode) => a.kind === b.kind && (a.kind === 'song' || a.partId === (b as { partId: string }).partId);

function positionAt(songBeat: number): Position {
	const { starts } = songStore.timeline;
	const sections = songStore.song.sections;
	let i = 0;
	while (i + 1 < starts.length && starts[i + 1] <= songBeat) i++;
	const into = Math.max(0, songBeat - (starts[i] ?? 0));
	const bars = sections[i]?.bars ?? 1;
	return { sectionIndex: i, bar: Math.min(bars - 1, Math.floor(into / BEATS_PER_BAR)), beat: into % BEATS_PER_BAR };
}

/** Tick range [start, end) to loop over: the loop bracket, or the whole song. */
function loopRange() {
	const { starts, totalBeats } = songStore.timeline;
	const { sections, loop } = songStore.song;
	const T = TICKS_PER_BEAT;
	if (loop) {
		const a = sections.findIndex((s) => s.id === loop.from);
		const b = sections.findIndex((s) => s.id === loop.to);
		if (a >= 0 && b >= a) return { start: starts[a] * T, end: (starts[b] + sections[b].bars * BEATS_PER_BAR) * T };
	}
	return { start: 0, end: Math.max(totalBeats, 1) * T };
}

export const transport = new Transport();
