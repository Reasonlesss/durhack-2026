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
	/* Reserved for the note/shape system. Always null until that lands. */
	canvas: null;
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
	/** Inclusive section range to loop over; unset = whole song. */
	loop?: { from: string; to: string };
}

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
