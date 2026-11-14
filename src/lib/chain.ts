/**
 * Chains: lines turn notes into phrases. This module builds the (cached) phrase graph for
 * a canvas and runs it tick by tick.
 *
 * - Roots are notes with no incoming line (pauses never lead). A loop with no root is led
 *   by its largest note.
 * - A root fires on its instrument's period, plays its whole phrase (arrival = shortest
 *   path through line durations, each note once), and won't restart until it has finished.
 */
import { INSTRUMENTS, TICKS_PER_BEAT } from './music';
import type { Canvas, Line, Note } from './types';

export const lineTicks = (l: Line) => TICKS_PER_BEAT / l.div;

export interface Phrase {
	root: Note;
	/** Ticks between possible starts. */
	period: number;
	/** Notes in the phrase with their arrival tick (root at 0). */
	steps: { note: Note; tick: number }[];
	/** Lines the phrase travels along, with the tick the travelling dot sets off. */
	travels: { line: Line; tick: number }[];
	/** Tick after the last note. */
	length: number;
}

export interface Graph {
	phrases: Phrase[];
	roots: Set<string>;
	/** Notes that are part of a chain of 2 or more. */
	chained: Set<string>;
}

const cache = new WeakMap<Canvas, { version: number; graph: Graph }>();

/** Phrase graph for a canvas. Recomputed only when `version` (bumped on every edit) moves. */
export function graphOf(canvas: Canvas, version: number): Graph {
	const hit = cache.get(canvas);
	if (hit && hit.version === version) return hit.graph;
	const graph = build(canvas);
	cache.set(canvas, { version, graph });
	return graph;
}

function build({ notes, lines }: Canvas): Graph {
	const byId = new Map(notes.map((n) => [n.id, n]));
	const out = new Map<string, Line[]>();
	const incoming = new Set<string>();
	const chained = new Set<string>();
	for (const l of lines) {
		if (!byId.has(l.from) || !byId.has(l.to)) continue;
		(out.get(l.from) ?? out.set(l.from, []).get(l.from)!).push(l);
		incoming.add(l.to);
		chained.add(l.from).add(l.to);
	}

	const phrases: Phrase[] = [];
	const covered = new Set<string>();
	const lead = (root: Note) => {
		const p = phrase(root, out, byId);
		p.steps.forEach((s) => covered.add(s.note.id));
		phrases.push(p);
	};
	for (const n of notes) if (!incoming.has(n.id) && n.inst !== 'pause') lead(n);
	// Leftover loops (and chains hanging off a pause): led by their largest note.
	for (;;) {
		const rest = notes.filter((n) => !covered.has(n.id) && n.inst !== 'pause');
		if (!rest.length) break;
		lead(rest.reduce((a, b) => (b.size > a.size ? b : a)));
	}
	return { phrases, roots: new Set(phrases.map((p) => p.root.id)), chained };
}

/** Dijkstra from the root; line durations are the edge weights. */
function phrase(root: Note, out: Map<string, Line[]>, byId: Map<string, Note>): Phrase {
	const dist = new Map<string, number>([[root.id, 0]]);
	const done = new Set<string>();
	const travels: Phrase['travels'] = [];
	for (;;) {
		let cur: string | null = null;
		for (const [id, d] of dist) if (!done.has(id) && (cur === null || d < dist.get(cur)!)) cur = id;
		if (cur === null) break;
		done.add(cur);
		for (const l of out.get(cur) ?? []) {
			const d = dist.get(cur)! + lineTicks(l);
			travels.push({ line: l, tick: dist.get(cur)! });
			if (!dist.has(l.to) || d < dist.get(l.to)!) dist.set(l.to, d);
		}
	}
	const steps = [...dist].map(([id, tick]) => ({ note: byId.get(id)!, tick }));
	const last = Math.max(...steps.map((s) => s.tick));
	return {
		root,
		period: INSTRUMENTS[root.inst].period * TICKS_PER_BEAT,
		steps,
		travels,
		length: last + 1
	};
}

export type Due = { kind: 'note'; note: Note } | { kind: 'line'; line: Line; ticks: number };

/** Runs one canvas's phrases against a tick counter. */
export class ChainRunner {
	private busyUntil = new Map<string, number>();
	private pending = new Map<number, Due[]>();

	reset() {
		this.busyUntil.clear();
		this.pending.clear();
	}

	/** Everything that happens at `tick` (ticks count from when this canvas started). */
	step(graph: Graph, tick: number): Due[] {
		for (const p of graph.phrases) {
			if (tick % p.period !== 0 || tick < (this.busyUntil.get(p.root.id) ?? 0)) continue;
			this.busyUntil.set(p.root.id, tick + p.length);
			for (const s of p.steps) this.queue(tick + s.tick, { kind: 'note', note: s.note });
			for (const t of p.travels) this.queue(tick + t.tick, { kind: 'line', line: t.line, ticks: lineTicks(t.line) });
		}
		const due = this.pending.get(tick) ?? [];
		this.pending.delete(tick);
		return due;
	}

	private queue(tick: number, d: Due) {
		(this.pending.get(tick) ?? this.pending.set(tick, []).get(tick)!).push(d);
	}
}
