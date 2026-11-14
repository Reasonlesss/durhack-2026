/**
 * Ready-made groups: the shelf's starters and the demo song's parts. Built with a tiny
 * chain helper so each is readable as "these notes, these step lengths".
 */
import { uid } from './ids';
import { yOfRow } from './music';
import type { Canvas, Effect, Instrument, Line, Note } from './types';

export type Spec = [inst: Instrument, row: number, size?: number, rot?: number];

/** Notes laid out left to right from (x, ·), each chained to the next with `divs[i]`. */
export function chain(specs: Spec[], divs: Line['div'][], x = 0, gap = 70): Canvas {
	const notes: Note[] = specs.map(([inst, row, size = 22, rot = 60], i) => ({
		id: uid('n_'),
		inst,
		x: x + i * gap,
		y: yOfRow(row),
		size,
		rot
	}));
	const lines: Line[] = notes.slice(1).map((n, i) => ({ id: uid('l_'), from: notes[i].id, to: n.id, div: divs[i] ?? 1 }));
	return { notes, lines, effects: [] };
}

export function merge(...cs: Canvas[]): Canvas {
	return { notes: cs.flatMap((c) => c.notes), lines: cs.flatMap((c) => c.lines), effects: cs.flatMap((c) => c.effects) };
}

/** An outline fitted around some notes, with a little breathing room. */
export function around(kind: Effect['kind'], notes: Note[], pad = 60, rot = 0): Effect {
	const xs = notes.map((n) => n.x);
	const ys = notes.map((n) => n.y);
	const cx = (Math.min(...xs) + Math.max(...xs)) / 2;
	const cy = (Math.min(...ys) + Math.max(...ys)) / 2;
	const r = Math.max(...notes.map((n) => Math.hypot(n.x - cx, n.y - cy))) + pad;
	// Pointy shapes need a bigger radius for their edges to clear the notes.
	const k = kind === 'dreamy' ? 1 : kind === 'muffle' ? 2 : kind === 'distortion' ? 1.5 : 1.3;
	return { id: uid('e_'), kind, x: cx, y: cy, r: Math.min(320, Math.max(50, r * k)), rot };
}

const KICK = 2;
const SNARE = 8;
const HAT = 15;

export const STARTERS = {
	'a beat': () => chain([['drums', KICK, 28], ['drums', HAT, 14], ['drums', SNARE, 24], ['drums', HAT, 14]], [2, 2, 2]),
	'a bassline': () => chain([['bass', 0, 26, 50], ['bass', 2, 20, 40], ['bass', 3, 22, 40], ['bass', 2, 18, 40]], [2, 2, 2]),
	'a break': () => brk(),
	'a rave': () => {
		const b = brk();
		const acid = chain(
			[
				['acid', 5, 30, 200], ['acid', 5, 14, 60], ['acid', 7, 22, 120], ['acid', 5, 14, 40],
				['acid', 8, 30, 260], ['acid', 6, 14, 60], ['acid', 5, 22, 160], ['acid', 9, 16, 80]
			],
			[4, 4, 4, 4, 4, 4, 4],
			0,
			55
		);
		acid.notes.forEach((n) => (n.y -= 280));
		// Stab on beat one and on the "and" of two: stab, a beat, a half-beat pause, stab.
		const stabs = chain([['stab', 6, 26, 40], ['pause', 6, 14], ['stab', 6, 22, 40]], [1, 2], 0, 90);
		stabs.notes.forEach((n) => ((n.y -= 460), (n.x += 60)));
		return merge(b, acid, stabs);
	}
} satisfies Record<string, () => Canvas>;

export type StarterName = keyof typeof STARTERS;

/** An original chopped break over two beats, with a rolling hat chain riding above it. */
function brk(): Canvas {
	const main = chain(
		[['drums', KICK, 30], ['drums', HAT, 14], ['drums', SNARE, 9], ['drums', SNARE, 26], ['drums', KICK, 26], ['drums', SNARE, 9]],
		[2, 4, 4, 2, 4]
	);
	const hats = chain(Array.from({ length: 8 }, (_, i): Spec => ['drums', 17, i % 2 ? 10 : 15]), [4, 4, 4, 4, 4, 4, 4], 0, 50);
	hats.notes.forEach((n) => (n.y -= 120));
	return merge(main, hats);
}

/** Move a group so its middle sits at (x, y). */
export function placeAt(c: Canvas, x: number, y: number): Canvas {
	const xs = c.notes.map((n) => n.x);
	const ys = c.notes.map((n) => n.y);
	const dx = x - (Math.min(...xs) + Math.max(...xs)) / 2;
	const dy = y - (Math.min(...ys) + Math.max(...ys)) / 2;
	return {
		notes: c.notes.map((n) => ({ ...n, x: n.x + dx, y: n.y + dy })),
		lines: c.lines,
		effects: c.effects.map((e) => ({ ...e, x: e.x + dx, y: e.y + dy }))
	};
}
