/**
 * Effect outlines: their shapes, who is inside them, what their size and turn mean,
 * and the everyday words used to describe them.
 */
import type { Effect, EffectKind, Note } from './types';

export const EFFECT_LABELS: Record<EffectKind, string> = {
	dreamy: 'dreamy',
	echo: 'echo',
	muffle: 'muffle',
	crunch: 'crunch',
	distortion: 'distortion'
};

/** Effects processed in line with the sound (the rest are sends). */
export const SERIES: EffectKind[] = ['muffle', 'crunch', 'distortion'];

const t01 = (e: Effect) => Math.max(0, Math.min(1, (e.r - 50) / 270));
const turn = (e: Effect) => (((e.rot % 360) + 360) % 360) / 360;

export interface FxParams {
	amount?: number;
	room?: number;
	tone?: number;
	beats?: number;
	feedback?: number;
	cutoff?: number;
	q?: number;
	drive?: number;
	makeup?: number;
}

const ECHO_BEATS = [0.25, 1 / 3, 0.5, 0.75, 1, 2];

export function effectParams(e: Effect): FxParams {
	const s = t01(e);
	const r = turn(e);
	switch (e.kind) {
		case 'dreamy':
			return { amount: 0.25 + s * 0.75, room: Math.min(3, Math.floor(s * 4)), tone: 1500 * Math.pow(8, r) };
		case 'echo':
			return { beats: ECHO_BEATS[Math.min(5, Math.floor(s * 6))], feedback: r * 0.78 };
		case 'muffle':
			return { cutoff: 250 * Math.pow(32, s), q: 0.7 + r * 11 };
		case 'crunch': {
			const drive = 1 + s * 14;
			return { drive, tone: 2000 * Math.pow(6, r), makeup: 1 / Math.pow(drive, 0.35) };
		}
		case 'distortion': {
			const drive = 2 + s * 58;
			return { drive, tone: 1100 * Math.pow(10, r), makeup: 0.9 / Math.pow(drive, 0.45) };
		}
	}
}

const pick = (words: string[], x: number) => words[Math.min(words.length - 1, Math.floor(x * words.length))];

/** "DREAMY · a cathedral, warm" style readout. */
export function describeEffect(e: Effect, touching: number): string {
	const s = t01(e);
	const r = turn(e);
	let what: string;
	switch (e.kind) {
		case 'dreamy':
			what = `${pick(['a small room', 'a hall', 'a cathedral', 'endless space'], s)}, ${pick(['dark', 'warm', 'bright'], r)}`;
			break;
		case 'echo':
			what = `${pick(['tight', 'quick', 'bouncy', 'lazy', 'slow', 'a canyon'], s)}, ${pick(['fade fast', 'linger', 'go on and on'], r)}`;
			break;
		case 'muffle':
			what = `${pick(['underwater', 'muffled', 'soft', 'clear'], s)}, ${pick(['smooth', 'ringing', 'whistling'], r)}`;
			break;
		case 'crunch':
			what = `${pick(['warm', 'crunchy', 'fuzzy', 'destroyed'], s)}, ${pick(['dark', 'raw', 'fizzy'], r)}`;
			break;
		case 'distortion':
			what = `${pick(['gritty', 'dirty', 'screaming', 'obliterated'], s)}, ${pick(['dark', 'raw', 'fizzy'], r)}`;
			break;
	}
	const who = touching === 0 ? 'draw it around notes' : touching === 1 ? 'touching one note' : `touching ${countWord(touching)} notes`;
	return `${e.kind.toUpperCase()} · ${what} · ${who}`;
}

function countWord(n: number) {
	return n <= 4 ? ['', 'one', 'two', 'three', 'four'][n] : n < 10 ? 'a few' : 'lots of';
}

// ---- geometry ----------------------------------------------------------------------

/** Outline points (world space) for an effect. Circles are sampled finely. */
export function effectPolygon(e: Effect): [number, number][] {
	const rot = (e.rot * Math.PI) / 180;
	const pts: [number, number][] = [];
	const push = (a: number, rad: number) => pts.push([e.x + Math.cos(a + rot) * rad, e.y + Math.sin(a + rot) * rad]);
	switch (e.kind) {
		case 'dreamy':
			for (let i = 0; i < 64; i++) push((i / 64) * Math.PI * 2, e.r);
			break;
		case 'echo':
			for (let i = 0; i < 4; i++) push(Math.PI / 4 + (i * Math.PI) / 2 - Math.PI / 2, e.r);
			break;
		case 'muffle':
			for (let i = 0; i < 3; i++) push(-Math.PI / 2 + (i * Math.PI * 2) / 3, e.r);
			break;
		case 'crunch':
			for (let i = 0; i < 6; i++) push((i * Math.PI) / 3, e.r);
			break;
		case 'distortion':
			for (let i = 0; i < 24; i++) push(-Math.PI / 2 + (i * Math.PI) / 12, i % 2 ? e.r * 0.72 : e.r);
			break;
	}
	return pts;
}

function inside(pts: [number, number][], x: number, y: number) {
	let hit = false;
	for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
		const [xi, yi] = pts[i];
		const [xj, yj] = pts[j];
		if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) hit = !hit;
	}
	return hit;
}

/** 1 at the centre → 0.4 at the edge; 0 outside. */
export function strengthAt(e: Effect, poly: [number, number][], x: number, y: number): number {
	if (!inside(poly, x, y)) return 0;
	const d = Math.hypot(x - e.x, y - e.y) / e.r;
	return 1 - 0.6 * Math.min(1, d);
}

/** Effects covering a note, series effects first. */
export function effectsFor(n: Note, effects: Effect[], polys: Map<string, [number, number][]>) {
	const hits = [];
	for (const e of effects) {
		const s = strengthAt(e, polys.get(e.id) ?? effectPolygon(e), n.x, n.y);
		if (s > 0) hits.push({ effect: e, strength: s });
	}
	return hits.sort((a, b) => Number(SERIES.includes(b.effect.kind)) - Number(SERIES.includes(a.effect.kind)));
}
