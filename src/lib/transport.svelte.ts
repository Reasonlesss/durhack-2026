import { songStore } from './song.svelte';
import { BEATS_PER_BAR, type Harmony, type Part, type Section } from './types';

/** How far ahead (seconds) we schedule beats, and how often (ms) we top the schedule up. */
const LOOKAHEAD_S = 0.12;
const TICK_MS = 25;

export interface Position {
	sectionIndex: number;
	/** 0-based bar inside the section. */
	bar: number;
	/** 0-based beat inside the bar. */
	beat: number;
}

export interface Tick extends Position {
	/** AudioContext time the beat lands at; schedule sound for exactly this time. */
	time: number;
	songBeat: number;
}

/**
 * Hooks for the future audio engine. Both fire ahead of time (inside the lookahead
 * window) with the exact AudioContext time the event happens.
 */
export interface TransportHooks {
	onTick?: (tick: Tick) => void;
	onSectionEnter?: (section: Section, activeParts: Part[], harmony: Harmony, time: number) => void;
}

class Transport {
	isPlaying = $state(false);
	metronome = $state(false);
	/** Smooth, fractional beat from the song start; drives the playhead. */
	songBeat = $state(0);
	position = $derived(positionAt(Math.floor(this.songBeat)));
	hooks: TransportHooks = {};

	private ctx: AudioContext | null = null;
	private timer: ReturnType<typeof setInterval> | undefined;
	private raf = 0;
	/** Next whole beat to schedule, and when it lands. */
	private cursor = 0;
	private nextTime = 0;
	/** Scheduled-but-not-yet-heard beats; the playhead follows the audible one. */
	private queue: { time: number; beat: number }[] = [];
	private lastSectionId: string | null = null;

	play = async () => {
		if (this.isPlaying) return;
		this.ctx ??= new AudioContext();
		await this.ctx.resume();
		const { start, end } = loopRange();
		// Resume from the top of the bar we stopped in, or from the loop start if outside it.
		const at = Math.floor(this.songBeat / BEATS_PER_BAR) * BEATS_PER_BAR;
		this.cursor = at >= start && at < end ? at : start;
		this.nextTime = this.ctx.currentTime + 0.05;
		this.queue = [];
		this.lastSectionId = null;
		this.isPlaying = true;
		this.timer = setInterval(this.schedule, TICK_MS);
		this.schedule();
		this.raf = requestAnimationFrame(this.frame);
	};

	stop = () => {
		if (!this.isPlaying) return;
		clearInterval(this.timer);
		cancelAnimationFrame(this.raf);
		this.isPlaying = false;
		this.queue = [];
		// Park at the start of the current section so the next play picks up there.
		this.songBeat = songStore.timeline.starts[this.position.sectionIndex] ?? 0;
	};

	toggle = () => (this.isPlaying ? this.stop() : this.play());

	seek = (sectionId: string) => {
		const i = songStore.song.sections.findIndex((s) => s.id === sectionId);
		if (i < 0) return;
		const beat = songStore.timeline.starts[i];
		this.songBeat = beat;
		if (this.isPlaying && this.ctx) {
			this.cursor = beat;
			this.nextTime = this.ctx.currentTime + 0.05;
			this.queue = [];
			this.lastSectionId = null;
		}
	};

	/** Fill the lookahead window with beats. Runs on a timer but times come from the audio clock. */
	private schedule = () => {
		const ctx = this.ctx!;
		const { start, end } = loopRange();
		while (this.nextTime < ctx.currentTime + LOOKAHEAD_S) {
			if (this.cursor >= end || this.cursor < start) this.cursor = start;
			const time = this.nextTime;
			const pos = positionAt(this.cursor);
			const section = songStore.song.sections[pos.sectionIndex];
			// Fires on a new section, and again when a loop wraps back to the same section's start.
			if (section && (section.id !== this.lastSectionId || (pos.bar === 0 && pos.beat === 0))) {
				this.lastSectionId = section.id;
				this.hooks.onSectionEnter?.(section, songStore.activeParts[section.id] ?? [], songStore.harmonyOf(section), time);
			}
			if (this.metronome) click(ctx, time, pos.beat === 0);
			this.hooks.onTick?.({ ...pos, time, songBeat: this.cursor });
			this.queue.push({ time, beat: this.cursor });
			this.cursor++;
			this.nextTime += 60 / songStore.song.bpm;
		}
	};

	/** Move the playhead to where the listener actually is right now. */
	private frame = () => {
		const now = this.ctx!.currentTime;
		while (this.queue.length > 1 && this.queue[1].time <= now) this.queue.shift();
		const head = this.queue[0];
		if (head && head.time <= now) {
			const frac = Math.min(0.999, (now - head.time) / (60 / songStore.song.bpm));
			this.songBeat = head.beat + frac;
		}
		this.raf = requestAnimationFrame(this.frame);
	};
}

/** Section index / bar / beat for a whole beat counted from the song start. */
function positionAt(songBeat: number): Position {
	const { starts } = songStore.timeline;
	const sections = songStore.song.sections;
	let i = 0;
	while (i + 1 < starts.length && starts[i + 1] <= songBeat) i++;
	const into = Math.max(0, songBeat - (starts[i] ?? 0));
	const bars = sections[i]?.bars ?? 1;
	return { sectionIndex: i, bar: Math.min(bars - 1, Math.floor(into / BEATS_PER_BAR)), beat: into % BEATS_PER_BAR };
}

/** Beat range [start, end) to loop over: the loop bracket, or the whole song. */
function loopRange() {
	const { starts, totalBeats } = songStore.timeline;
	const { sections, loop } = songStore.song;
	if (loop) {
		const a = sections.findIndex((s) => s.id === loop.from);
		const b = sections.findIndex((s) => s.id === loop.to);
		if (a >= 0 && b >= a) return { start: starts[a], end: starts[b] + sections[b].bars * BEATS_PER_BAR };
	}
	return { start: 0, end: Math.max(totalBeats, 1) };
}

/** A short, quiet tick. Higher on the first beat of a bar. */
function click(ctx: AudioContext, time: number, accent: boolean) {
	const osc = ctx.createOscillator();
	const gain = ctx.createGain();
	osc.frequency.value = accent ? 1500 : 1000;
	gain.gain.setValueAtTime(accent ? 0.12 : 0.06, time);
	gain.gain.exponentialRampToValueAtTime(0.0001, time + 0.04);
	osc.connect(gain).connect(ctx.destination);
	osc.start(time);
	osc.stop(time + 0.05);
}

export const transport = new Transport();
