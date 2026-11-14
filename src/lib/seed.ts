import { uid } from './ids';
import { cellKey, type Part, type Section, type Song } from './types';

function part(name: string, colour: string): Part {
	return { id: uid('p_'), name, colour, bars: 1, muted: false, solo: false, canvas: null };
}

/** The demo song every new visitor starts with. */
export function demoSong(): Song {
	const [groove, bass, chords, hook, sparkle] = [
		part('Groove', '#F2B544'),
		part('Bass', '#7CA7FF'),
		part('Chords', '#6FD6C4'),
		part('Hook', '#C9A2FF'),
		part('Sparkle', '#7EE0A1')
	];
	const s = (name: string, bars: number, harmony?: Section['harmony']): Section => ({
		id: uid('s_'),
		name,
		bars,
		...(harmony && { harmony })
	});
	const [intro, verse, chorus, brk, outro] = [
		s('Intro', 4),
		s('Verse', 8),
		s('Chorus', 8, { feel: 'sunny', chord: 'lift' }),
		s('Break', 4, { feel: 'dreamy', chord: 'drift' }),
		s('Outro', 4)
	];

	const cells: Record<string, boolean> = {};
	const on = (sec: Section, ...ps: Part[]) => ps.forEach((p) => (cells[cellKey(p.id, sec.id)] = true));
	on(intro, chords, sparkle);
	on(verse, chords, sparkle, groove, bass);
	on(chorus, groove, bass, chords, hook, sparkle);
	on(brk, chords, sparkle);
	on(outro, chords);

	return {
		id: uid('song_'),
		name: 'First song',
		bpm: 104,
		harmony: { feel: 'mellow', chord: 'home' },
		parts: [groove, bass, chords, hook, sparkle],
		sections: [intro, verse, chorus, brk, outro],
		cells
	};
}
