/**
 * The demo song (104 beats a minute, sunny · home): five chains that all start together,
 * so they realign every few bars but keep reshuffling. Laid out left to right; the canvas
 * is infinite and zooms to fit.
 */
import { uid } from './ids';
import { around, chain, merge, type Spec } from './starters';
import type { Canvas, Song } from './types';

/** Kick, hat, snare, two quick hats, a pushing second kick: a two-beat loop. Crunch on the kick. */
function groove(): Canvas {
	const c = chain(
		[['drums', 2, 30], ['drums', 15, 14], ['drums', 8, 26], ['drums', 15, 12], ['drums', 16, 12], ['drums', 3, 24]],
		[2, 2, 4, 4, 4],
		60,
		70
	);
	c.effects.push(around('crunch', [c.notes[0]], 30));
	return c;
}

/** A 14-step melody over eight beats with a breath; the peak is dreamy, the tail echoes. */
function hook(): Canvas {
	const rows = [7, 8, 9, 11, 10, 9, -1, 12, 13, 12, 10, 9, 8, 7];
	const c = chain(
		rows.map((r): Spec => (r < 0 ? ['pause', 9, 14] : ['lead', r, r >= 12 ? 28 : 22, 90])),
		[2, 2, 1, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2],
		490,
		55
	);
	c.effects.push(around('dreamy', c.notes.slice(7, 10), 50, 220));
	c.effects.push(around('echo', c.notes.slice(11), 40, 120));
	return c;
}

/** Three high bells cycling every three beats inside a tight echo, floating above the hook. */
function sparkle(): Canvas {
	const c = chain([['bells', 17, 20, 200], ['bells', 19, 16, 200], ['bells', 18, 18, 200]], [1, 1], 700, 80);
	c.effects.push({ ...around('echo', c.notes, 40, 90), r: 115 });
	return c;
}

/** Eight bass notes walking on chord tones over four beats, inside one large crunch. */
function bass(): Canvas {
	const c = chain(
		[0, 0, 2, 3, 1, 2, 0, 2].map((row, i): Spec => ['bass', row, i % 2 ? 18 : 26, 50]),
		[2, 2, 2, 2, 2, 2, 2],
		1280,
		50
	);
	c.effects.push(around('crunch', c.notes, 20));
	return c;
}

/** Two chords two beats apart, with a pause between, inside a dreamy circle. */
function chords(): Canvas {
	const c = chain([['chords', 3, 28, 200], ['pause', 4, 14], ['chords', 3, 24, 200]], [1, 1], 1770, 120);
	c.effects.push(around('dreamy', c.notes, 40, 140));
	return c;
}

export function demoSong(): Song {
	return {
		id: uid('song_'),
		name: 'first song',
		bpm: 104,
		harmony: { feel: 'sunny', chord: 'home' },
		swing: 'tight',
		canvas: merge(groove(), hook(), sparkle(), bass(), chords())
	};
}
