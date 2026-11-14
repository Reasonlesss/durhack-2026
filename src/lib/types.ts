/** Mood of the harmony, ordered darker → brighter. */
export type Feel = 'spooky' | 'moody' | 'mellow' | 'sunny' | 'dreamy';
/** Which chord of the key we sit on, from resting ("home") to most tense ("reach"). */
export type Chord = 'home' | 'drift' | 'lift' | 'reach';

export interface Harmony {
	feel: Feel;
	chord: Chord;
}

/** One shape-sequencer canvas: a self-contained looping pattern. */
export interface Part {
	id: string;
	name: string;
	colour: string;
	bars: number;
	muted: boolean;
	solo: boolean;
	/** The shape canvas: notes, the lines that chain them, and effect outlines. */
	canvas: Canvas;
}

/** A slice of the song timeline (Intro, Verse, ...). No harmony = inherit song default. */
export interface Section {
	id: string;
	name: string;
	bars: number;
	harmony?: Harmony;
}

export interface Song {
	id: string;
	name: string;
	bpm: number;
	harmony: Harmony;
	parts: Part[];
	sections: Section[];
	/** Key `${partId}:${sectionId}`; missing = off. */
	cells: Record<string, boolean>;
	/** How late off-beats land: tight (none), loose, lazy. */
	swing: 'tight' | 'loose' | 'lazy';
	/** Inclusive section range to loop over; unset = whole song. */
	loop?: { from: string; to: string };
}

export type Instrument = 'lead' | 'chords' | 'bells' | 'bass' | 'drums' | 'stab' | 'acid' | 'siren' | 'pause';
export type EffectKind = 'dreamy' | 'echo' | 'muffle' | 'crunch' | 'distortion';

/** One shape on the canvas. Height = pitch, size = loudness, rotation = how long it rings. */
export interface Note {
	id: string;
	inst: Instrument;
	/** World coordinates (the canvas is infinite; pitch rows are measured from ROW0_Y). */
	x: number;
	y: number;
	/** 8–40 */
	size: number;
	/** 0–360 */
	rot: number;
}

/** Directed: after `from` plays, `to` plays. `div` splits the one-beat step into 1–4 pieces. */
export interface Line {
	id: string;
	from: string;
	to: string;
	div: 1 | 2 | 3 | 4;
}

/** A large outline; notes inside are processed by it. */
export interface Effect {
	id: string;
	kind: EffectKind;
	x: number;
	y: number;
	/** Radius 50–320 */
	r: number;
	rot: number;
}

export interface Canvas {
	notes: Note[];
	lines: Line[];
	effects: Effect[];
}

export const emptyCanvas = (): Canvas => ({ notes: [], lines: [], effects: [] });

export const FEELS: Feel[] = ['spooky', 'moody', 'mellow', 'sunny', 'dreamy'];
/** Bottom → top in the harmony pad. */
export const CHORDS: Chord[] = ['home', 'drift', 'lift', 'reach'];

export const FEEL_COLOURS: Record<Feel, string> = {
	spooky: '#7EE0A1',
	moody: '#7CA7FF',
	mellow: '#6FD6C4',
	sunny: '#F2B544',
	dreamy: '#C9A2FF'
};

export const PART_COLOURS = ['#7EE0A1', '#7CA7FF', '#6FD6C4', '#F2B544', '#C9A2FF', '#FF9B85', '#E8E07A'];

export const BEATS_PER_BAR = 4;

export const cellKey = (partId: string, sectionId: string) => `${partId}:${sectionId}`;
