/**
 * Everything musical lives here: the key, the modes behind each feel, the chords behind
 * each row of the pad, and how a note's height becomes pitches. All notes are drawn from
 * the current chord, so nothing a user places can sound wrong.
 */
import type { Chord, Feel, Harmony, Instrument, Note } from './types';

export const ROW_GAP = 38;
/** World y of pitch row 0; rows go up from here. */
export const ROW0_Y = 860;
export const ROWS = 23;
export const TICKS_PER_BEAT = 12;

/** Semitone offsets of each mode, darker → brighter. */
const MODES: Record<Feel, number[]> = {
	spooky: [0, 1, 3, 5, 7, 8, 10], // Phrygian
	moody: [0, 2, 3, 5, 7, 8, 10], // Aeolian
	mellow: [0, 2, 3, 5, 7, 9, 10], // Dorian
	sunny: [0, 2, 4, 5, 7, 9, 11], // Ionian
	dreamy: [0, 2, 4, 6, 7, 9, 11] // Lydian
};
/** Scale degree the chord is built on: I, vi, IV, V. */
const CHORD_DEGREE: Record<Chord, number> = { home: 0, drift: 5, lift: 3, reach: 4 };
const PENTA = [0, 1, 2, 4, 5];
const G1 = 31;
const G2 = 43;

export const rowOf = (y: number) => Math.max(0, Math.min(ROWS - 1, Math.round((ROW0_Y - y) / ROW_GAP)));
export const yOfRow = (row: number) => ROW0_Y - row * ROW_GAP;
export const mtof = (m: number) => 440 * Math.pow(2, (m - 69) / 12);

/** Semitones above the key root for a (possibly >7) scale degree. */
function semis(feel: Feel, degree: number) {
	const mode = MODES[feel];
	const oct = Math.floor(degree / 7);
	return mode[((degree % 7) + 7) % 7] + 12 * oct;
}

const pentaDegree = (root: number, row: number) => root + PENTA[row % 5] + 7 * Math.floor(row / 5);
/** k-th chord tone counting up through inversions (k may be negative). */
const chordTone = (root: number, k: number) => root + 2 * (((k % 3) + 3) % 3) + 7 * Math.floor(k / 3);

export type DrumKind = 'kick' | 'snare' | 'hat';
export interface Voicing {
	/** MIDI notes to sound (empty for drums and pauses). */
	midi: number[];
	drum?: { kind: DrumKind; tune: number };
}

/** The pitches a note plays under a harmony. */
export function voice(inst: Instrument, row: number, h: Harmony): Voicing {
	const r = CHORD_DEGREE[h.chord];
	const m = (base: number, deg: number) => base + semis(h.feel, deg);
	switch (inst) {
		case 'lead':
		case 'bells':
		case 'siren':
			return { midi: [m(G2, pentaDegree(r, row))] };
		case 'acid':
			return { midi: [m(G1, pentaDegree(r, Math.min(row, 14)))] };
		case 'bass': {
			const n = Math.min(row, 8);
			return { midi: [m(G1, r + [0, 2, 4][n % 3] + 7 * Math.floor(n / 3))] };
		}
		case 'chords':
		case 'stab': {
			const k = Math.min(row - 2, 9);
			const tones = [0, 1, 2].map((i) => m(G2, chordTone(r, k + i)));
			if (inst === 'stab') tones.push(tones[0] + 12);
			return { midi: tones };
		}
		case 'drums':
			if (row <= 5) return { midi: [], drum: { kind: 'kick', tune: row / 5 } };
			if (row <= 10) return { midi: [], drum: { kind: 'snare', tune: (row - 6) / 4 } };
			return { midi: [], drum: { kind: 'hat', tune: Math.min(1, (row - 11) / 11) } };
		case 'pause':
			return { midi: [] };
	}
}

export const velocity = (size: number) => Math.max(0.15, Math.min(1, (size - 8) / 28));
export const decayOf = (rot: number) => 0.12 + (rot / 360) * 1.5;
export const panOf = (x: number) => Math.max(-0.8, Math.min(0.8, ((x - 720) / 720) * 0.8));

// ---- instruments -------------------------------------------------------------

export interface InstrumentInfo {
	id: Instrument;
	/** Shelf label. */
	label: string;
	/** Used in readouts: "a firm chord". */
	noun: string;
	/** Beats between phrase starts when this note leads a chain. */
	period: number;
}

export const INSTRUMENTS: Record<Instrument, InstrumentInfo> = {
	lead: { id: 'lead', label: 'lead', noun: 'lead note', period: 4 },
	chords: { id: 'chords', label: 'chords', noun: 'chord', period: 4 },
	bells: { id: 'bells', label: 'bells', noun: 'bell', period: 3 },
	bass: { id: 'bass', label: 'bass', noun: 'bass note', period: 2 },
	drums: { id: 'drums', label: 'drums · low kick, high hat', noun: 'drum', period: 2 },
	stab: { id: 'stab', label: 'rave stab', noun: 'rave stab', period: 2 },
	acid: { id: 'acid', label: 'acid', noun: 'acid squelch', period: 2 },
	siren: { id: 'siren', label: 'siren', noun: 'siren', period: 4 },
	pause: { id: 'pause', label: 'pause', noun: 'pause', period: 4 }
};

/** Order a tap cycles through (pause is only placed from the shelf). */
export const CYCLE: Instrument[] = ['lead', 'chords', 'bells', 'bass', 'drums', 'stab', 'acid', 'siren'];
export const nextInstrument = (i: Instrument) => CYCLE[(CYCLE.indexOf(i) + 1) % CYCLE.length] ?? 'lead';

// ---- words ---------------------------------------------------------------------

const NUM = ['', 'beat', 'two beats', 'three beats', 'four beats'];
export const periodWords = (inst: Instrument) => `every ${NUM[INSTRUMENTS[inst].period]}`;

function sizeWord(size: number) {
	return size < 13 ? 'a tiny' : size < 19 ? 'a soft' : size < 27 ? 'a firm' : size < 34 ? 'a strong' : 'a huge';
}
function drumNoun(row: number) {
	return row <= 5 ? 'kick' : row <= 10 ? 'snare' : 'hat';
}
function ringWord(rot: number) {
	return rot < 60 ? 'short' : rot < 180 ? 'rings a while' : rot < 290 ? 'rings long' : 'rings and rings';
}
function heightWord(row: number) {
	return row < 6 ? 'low' : row < 12 ? 'middle' : row < 18 ? 'high' : 'very high';
}

/** Readout for a note: "a firm chord · high · starts its chain every four beats". */
export function describeNote(n: Note, role: 'root' | 'chained' | 'alone'): string {
	if (n.inst === 'pause') return 'a pause · leaves a gap in its chain';
	const row = rowOf(n.y);
	const noun = n.inst === 'drums' ? drumNoun(row) : INSTRUMENTS[n.inst].noun;
	const parts = [`${sizeWord(n.size)} ${noun}`, heightWord(row), ringWord(n.rot)];
	if (role === 'root') parts.push(`starts its chain ${periodWords(n.inst)}`);
	else if (role === 'alone') parts.push(`plays ${periodWords(n.inst)}`);
	return parts.join(' · ');
}

export const lineWords = (div: number) =>
	`this step feels ${['', 'steady', 'quicker', 'skipping', 'racing'][div]}`;

export const paceWord = (bpm: number) =>
	bpm < 75 ? 'sleepy' : bpm < 100 ? 'easy' : bpm < 125 ? 'bouncy' : bpm < 150 ? 'hyped' : 'frantic';

export const FEEL_GROUND: Record<Feel, string> = {
	spooky: '#0A100D',
	moody: '#0B0D12',
	mellow: '#0A1011',
	sunny: '#100E0B',
	dreamy: '#0F0C13'
};
