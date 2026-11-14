import { uid } from './ids';
import { demoSong } from './seed';
import { BEATS_PER_BAR, PART_COLOURS, cellKey, type Harmony, type Part, type Section, type Song } from './types';

const STORAGE_KEY = 'shape-sequencer:song';
const HISTORY_LIMIT = 200;
/** Edits sharing a coalesce key within this window become one undo step (typing, dragging). */
const COALESCE_MS = 1200;

const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v));
const clampBars = (n: number) => Math.max(1, Math.min(64, Math.round(n)));

function load(): Song {
	try {
		const raw = localStorage.getItem(STORAGE_KEY);
		if (raw) return validate(JSON.parse(raw));
	} catch {
		/* corrupt save: fall back to the demo */
	}
	return demoSong();
}

/** Minimal shape check for loaded / imported JSON. Throws on anything unusable. */
export function validate(data: unknown): Song {
	const s = data as Song;
	if (!s || typeof s !== 'object' || !Array.isArray(s.parts) || !Array.isArray(s.sections) || !s.harmony)
		throw new Error('That file is not a Shape Sequencer song.');
	s.cells ??= {};
	s.bpm = Math.max(40, Math.min(220, Number(s.bpm) || 100));
	s.parts.forEach((p) => (p.canvas = null));
	return s;
}

class SongStore {
	song = $state<Song>(load());
	private past: string[] = [];
	private future: string[] = [];
	canUndo = $state(false);
	canRedo = $state(false);
	private lastKey: string | null = null;
	private lastTime = 0;

	/** Parts that actually sound in each section, honouring mute and solo. */
	activeParts = $derived.by(() => {
		const { parts, sections, cells } = this.song;
		const anySolo = parts.some((p) => p.solo);
		const audible = parts.filter((p) => (anySolo ? p.solo : !p.muted));
		const out: Record<string, Part[]> = {};
		for (const s of sections) out[s.id] = audible.filter((p) => cells[cellKey(p.id, s.id)]);
		return out;
	});

	/** Start beat of each section plus the song length, for the transport and playhead. */
	timeline = $derived.by(() => {
		let beat = 0;
		const starts: number[] = [];
		for (const s of this.song.sections) {
			starts.push(beat);
			beat += s.bars * BEATS_PER_BAR;
		}
		return { starts, totalBeats: beat };
	});

	constructor() {
		// Autosave: JSON.stringify reads every field, so any change re-runs this.
		$effect.root(() => {
			$effect(() => localStorage.setItem(STORAGE_KEY, JSON.stringify(this.song)));
		});
	}

	// ---- history ---------------------------------------------------------

	/** Run a mutation as one undoable step. */
	private commit(fn: (s: Song) => void, coalesceKey?: string) {
		const now = Date.now();
		const merge = coalesceKey && coalesceKey === this.lastKey && now - this.lastTime < COALESCE_MS;
		if (!merge) {
			this.past.push(JSON.stringify(this.song));
			if (this.past.length > HISTORY_LIMIT) this.past.shift();
		}
		this.future = [];
		this.lastKey = coalesceKey ?? null;
		this.lastTime = now;
		fn(this.song);
		this.syncFlags();
	}

	undo = () => this.step(this.past, this.future);
	redo = () => this.step(this.future, this.past);

	private step(from: string[], to: string[]) {
		const snap = from.pop();
		if (!snap) return;
		to.push(JSON.stringify(this.song));
		this.song = JSON.parse(snap);
		this.lastKey = null;
		this.syncFlags();
	}

	private syncFlags() {
		this.canUndo = this.past.length > 0;
		this.canRedo = this.future.length > 0;
	}

	// ---- lookups ---------------------------------------------------------

	part = (id: string) => this.song.parts.find((p) => p.id === id);
	section = (id: string) => this.song.sections.find((s) => s.id === id);
	isOn = (partId: string, sectionId: string) => !!this.song.cells[cellKey(partId, sectionId)];
	harmonyOf = (s: Section): Harmony => s.harmony ?? this.song.harmony;

	// ---- song ------------------------------------------------------------

	renameSong = (name: string) => this.commit((s) => (s.name = name), 'song-name');
	setBpm = (bpm: number) =>
		this.commit((s) => (s.bpm = Math.max(40, Math.min(220, Math.round(bpm)))), 'bpm');
	/** Section id given = override for that section; null harmony clears the override. */
	setHarmony = (harmony: Harmony | null, sectionId?: string) =>
		this.commit((s) => {
			if (!sectionId) {
				if (harmony) s.harmony = { ...harmony };
				return;
			}
			const sec = s.sections.find((x) => x.id === sectionId);
			if (!sec) return;
			if (harmony) sec.harmony = { ...harmony };
			else delete sec.harmony;
		});
	setLoop = (loop: Song['loop'] | null) =>
		this.commit((s) => {
			if (loop) s.loop = { ...loop };
			else delete s.loop;
		}, 'loop');

	replaceSong = (next: Song) => {
		const fresh = validate(clone(next));
		this.commit(() => {}); // snapshot the current song so the import can be undone
		this.song = fresh;
	};

	// ---- parts -----------------------------------------------------------

	addPart = () => {
		const id = uid('p_');
		this.commit((s) =>
			s.parts.push({
				id,
				name: `Part ${s.parts.length + 1}`,
				colour: PART_COLOURS[s.parts.length % PART_COLOURS.length],
				bars: 1,
				muted: false,
				solo: false,
				canvas: null
			})
		);
		return id;
	};
	renamePart = (id: string, name: string) =>
		this.commit((s) => withPart(s, id, (p) => (p.name = name)), `part-name:${id}`);
	setPartColour = (id: string, colour: string) =>
		this.commit((s) => withPart(s, id, (p) => (p.colour = colour)));
	setPartBars = (id: string, bars: number) =>
		this.commit((s) => withPart(s, id, (p) => (p.bars = clampBars(bars))));
	toggleMute = (id: string) => this.commit((s) => withPart(s, id, (p) => (p.muted = !p.muted)));
	toggleSolo = (id: string) => this.commit((s) => withPart(s, id, (p) => (p.solo = !p.solo)));

	duplicatePart = (id: string) => {
		const newId = uid('p_');
		this.commit((s) => {
			const i = s.parts.findIndex((p) => p.id === id);
			if (i < 0) return;
			s.parts.splice(i + 1, 0, { ...clone(s.parts[i]), id: newId, name: `${s.parts[i].name} copy`, solo: false });
			for (const sec of s.sections) if (s.cells[cellKey(id, sec.id)]) s.cells[cellKey(newId, sec.id)] = true;
		});
		return newId;
	};
	deletePart = (id: string) =>
		this.commit((s) => {
			s.parts = s.parts.filter((p) => p.id !== id);
			for (const sec of s.sections) delete s.cells[cellKey(id, sec.id)];
		});
	reorderParts = (from: number, to: number) => this.commit((s) => move(s.parts, from, to));

	// ---- sections --------------------------------------------------------

	addSection = (afterId?: string) => {
		const id = uid('s_');
		this.commit((s) => {
			const i = afterId ? s.sections.findIndex((x) => x.id === afterId) + 1 : s.sections.length;
			s.sections.splice(i || s.sections.length, 0, { id, name: `Section ${s.sections.length + 1}`, bars: 4 });
		});
		return id;
	};
	renameSection = (id: string, name: string) =>
		this.commit((s) => withSection(s, id, (x) => (x.name = name)), `section-name:${id}`);
	setSectionBars = (id: string, bars: number) =>
		this.commit((s) => withSection(s, id, (x) => (x.bars = clampBars(bars))), `section-bars:${id}`);

	/** Cut a section in half; the new second half keeps the same parts and harmony. */
	splitSection = (id: string) =>
		this.commit((s) => {
			const i = s.sections.findIndex((x) => x.id === id);
			const sec = s.sections[i];
			if (!sec || sec.bars < 2) return;
			const second: Section = { ...clone(sec), id: uid('s_'), name: `${sec.name} 2`, bars: Math.floor(sec.bars / 2) };
			sec.bars -= second.bars;
			s.sections.splice(i + 1, 0, second);
			for (const p of s.parts) if (s.cells[cellKey(p.id, id)]) s.cells[cellKey(p.id, second.id)] = true;
		});
	deleteSection = (id: string) =>
		this.commit((s) => {
			if (s.sections.length <= 1) return;
			s.sections = s.sections.filter((x) => x.id !== id);
			for (const p of s.parts) delete s.cells[cellKey(p.id, id)];
			if (s.loop && (s.loop.from === id || s.loop.to === id)) delete s.loop;
		});
	reorderSections = (from: number, to: number) =>
		this.commit((s) => {
			move(s.sections, from, to);
			// Keep the loop bracket contiguous and in order after a move.
			if (s.loop) {
				const a = s.sections.findIndex((x) => x.id === s.loop!.from);
				const b = s.sections.findIndex((x) => x.id === s.loop!.to);
				if (a > b) s.loop = { from: s.loop.to, to: s.loop.from };
			}
		});

	// ---- cells -----------------------------------------------------------

	toggleCell = (partId: string, sectionId: string) =>
		this.commit((s) => setCell(s, partId, sectionId, !s.cells[cellKey(partId, sectionId)]));

	/** Set many cells at once. Pass the same stroke id for one drag so it undoes in one go. */
	paintCells = (keys: { partId: string; sectionId: string }[], value: boolean, strokeId?: string) =>
		this.commit((s) => keys.forEach((k) => setCell(s, k.partId, k.sectionId, value)), strokeId);
}

function withPart(s: Song, id: string, fn: (p: Part) => void) {
	const p = s.parts.find((x) => x.id === id);
	if (p) fn(p);
}
function withSection(s: Song, id: string, fn: (x: Section) => void) {
	const x = s.sections.find((y) => y.id === id);
	if (x) fn(x);
}
function setCell(s: Song, partId: string, sectionId: string, on: boolean) {
	if (on) s.cells[cellKey(partId, sectionId)] = true;
	else delete s.cells[cellKey(partId, sectionId)];
}
function move<T>(arr: T[], from: number, to: number) {
	if (from === to || from < 0 || to < 0 || from >= arr.length || to >= arr.length) return;
	const [item] = arr.splice(from, 1);
	arr.splice(to, 0, item);
}

export const songStore = new SongStore();

// ---- export / import -----------------------------------------------------

export function exportSong() {
	const { song } = songStore;
	const blob = new Blob([JSON.stringify(song, null, 2)], { type: 'application/json' });
	const a = document.createElement('a');
	a.href = URL.createObjectURL(blob);
	a.download = `${song.name.replace(/[^\w-]+/g, '-').toLowerCase() || 'song'}.json`;
	a.click();
	URL.revokeObjectURL(a.href);
}

export async function importSong(file: File) {
	songStore.replaceSong(validate(JSON.parse(await file.text())));
}
