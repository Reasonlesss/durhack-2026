import { uid } from './ids';
import { around, chain, type Spec } from './starters';
import { cellKey, type Canvas, type Part, type Section, type Song } from './types';

function part(name: string, colour: string, canvas: Canvas): Part {
	return { id: uid('p_'), name, colour, bars: 1, muted: false, solo: false, canvas };
}

/** Kick, hat, snare, two quick hats, a pushing second kick: a two-beat loop. */
function groove(): Canvas {
	const c = chain(
		[['drums', 2, 30], ['drums', 15, 14], ['drums', 8, 26], ['drums', 15, 12], ['drums', 16, 12], ['drums', 3, 24]],
		[2, 2, 4, 4, 4],
		360,
		110
	);
	c.effects.push(around('crunch', [c.notes[0]], 30));
	return c;
}

/** Eight bass notes walking on chord tones over four beats, inside a large crunch. */
function bass(): Canvas {
	const c = chain(
		[0, 0, 2, 3, 1, 2, 0, 2].map((row, i): Spec => ['bass', row, i % 2 ? 18 : 26, 50]),
		[2, 2, 2, 2, 2, 2, 2],
		420,
		80
	);
	c.effects.push({ ...around('crunch', c.notes, 70), r: 320 });
	return c;
}

/** Two chords two beats apart, with a pause between, inside a dreamy circle. */
function chords(): Canvas {
	const c = chain([['chords', 6, 28, 200], ['pause', 7, 14], ['chords', 6, 24, 200]], [1, 1], 560, 150);
	c.effects.push(around('dreamy', c.notes, 90, 140));
	return c;
}

/** A 14-step melody over eight beats with a breath in the middle. */
function hook(): Canvas {
	const rows = [7, 8, 9, 11, 10, 9, -1, 12, 13, 12, 10, 9, 8, 7];
	const c = chain(
		rows.map((r): Spec => (r < 0 ? ['pause', 9, 14] : ['lead', r, r >= 12 ? 28 : 22, 90])),
		[2, 2, 1, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2],
		150,
		88
	);
	c.effects.push(around('dreamy', c.notes.slice(7, 10), 50, 220));
	c.effects.push({ ...around('echo', c.notes.slice(11), 40, 120) });
	return c;
}

/** Three high bells cycling every three beats inside a tight echo. */
function sparkle(): Canvas {
	const c = chain([['bells', 17, 20, 200], ['bells', 19, 16, 200], ['bells', 18, 18, 200]], [1, 1], 620, 100);
	c.effects.push({ ...around('echo', c.notes, 40, 90), r: 115 });
	return c;
}

/** The demo song every new visitor starts with. */
export function demoSong(): Song {
	const [g, b, c, h, s] = [
		part('Groove', '#F2B544', groove()),
		part('Bass', '#7CA7FF', bass()),
		part('Chords', '#6FD6C4', chords()),
		part('Hook', '#C9A2FF', hook()),
		part('Sparkle', '#7EE0A1', sparkle())
	];
	const sec = (name: string, bars: number, harmony?: Section['harmony']): Section => ({
		id: uid('s_'),
		name,
		bars,
		...(harmony && { harmony })
	});
	const [intro, verse, chorus, brk, outro] = [
		sec('Intro', 4),
		sec('Verse', 8),
		sec('Chorus', 8, { feel: 'sunny', chord: 'lift' }),
		sec('Break', 4, { feel: 'dreamy', chord: 'drift' }),
		sec('Outro', 4)
	];
	const cells: Record<string, boolean> = {};
	const on = (x: Section, ...ps: Part[]) => ps.forEach((p) => (cells[cellKey(p.id, x.id)] = true));
	on(intro, c, s);
	on(verse, c, s, g, b);
	on(chorus, g, b, c, h, s);
	on(brk, c, s);
	on(outro, c);

	return {
		id: uid('song_'),
		name: 'First song',
		bpm: 104,
		harmony: { feel: 'sunny', chord: 'home' },
		swing: 'tight',
		parts: [g, b, c, h, s],
		sections: [intro, verse, chorus, brk, outro],
		cells
	};
}
