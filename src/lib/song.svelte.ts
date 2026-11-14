/**
 * The one song: tempo, feel, groove and the shape canvas. Every edit goes through
 * `commit`, which makes it undoable and autosaves it.
 */
import { uid } from './ids';
import { nextInstrument, rowOf, yOfRow } from './music';
import { demoSong } from './seed';
import { emptyCanvas, type Canvas, type Effect, type Harmony, type Line, type Note, type Song, type Workspace } from './types';

const STORAGE_KEY = 'shape-sequencer:canvas';
const CURRENT_KEY = 'shape-sequencer:workspace';
/** Workspace names: words, never numbers. */
const NAMES = ['one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten', 'eleven', 'twelve'];
const HISTORY_LIMIT = 200;
/** Edits sharing a coalesce key within this window become one undo step (drags, scrolls). */
const COALESCE_MS = 1200;

const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v));
const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));
const wrap = (deg: number) => ((deg % 360) + 360) % 360;

function load(): Song {
	try {
		const raw = localStorage.getItem(STORAGE_KEY);
		if (raw) return validate(JSON.parse(raw));
	} catch {
		/* corrupt save: fall back to the demo */
	}
	return demoSong();
}

/** Minimal shape check for loaded, imported or shared JSON. Throws on anything unusable. */
export function validate(data: unknown): Song {
	const s = data as Song;
	if (!s || typeof s !== 'object' || !s.harmony) throw new Error('That file is not a Shape Sequencer song.');
	s.id ??= uid('song_');
	s.name ??= 'my song';
	s.bpm = clamp(Number(s.bpm) || 100, 50, 200);
	s.swing ??= 'tight';
	// Older saves had a single canvas: it becomes the first workspace.
	const legacy = (s as unknown as { canvas?: Canvas }).canvas;
	if (!Array.isArray(s.workspaces)) s.workspaces = legacy ? [{ id: uid('w_'), name: 'one', enabled: true, canvas: legacy }] : [];
	delete (s as unknown as { canvas?: Canvas }).canvas;
	if (!s.workspaces.length) s.workspaces.push({ id: uid('w_'), name: 'one', enabled: true, canvas: emptyCanvas() });
	for (const w of s.workspaces) {
		const c = (w.canvas ?? {}) as Partial<Canvas>;
		w.canvas = { notes: c.notes ?? [], lines: c.lines ?? [], effects: c.effects ?? [] };
		w.enabled ??= true;
	}
	return s;
}

class SongStore {
	song = $state<Song>(load());
	/** The workspace being edited (UI state: switching is not an undo step). */
	current = $state<string>(localStorage.getItem(CURRENT_KEY) ?? '');
	/** The current workspace, falling back to the first if it no longer exists (e.g. after undo). */
	workspace = $derived(this.song.workspaces.find((w) => w.id === this.current) ?? this.song.workspaces[0]);
	/** Shapes of the current workspace. */
	get canvas() {
		return this.workspace.canvas;
	}
	canUndo = $state(false);
	canRedo = $state(false);
	/** Bumped on every change; lets caches (the chain graph) know when to rebuild. Not reactive. */
	version = 0;
	private past: string[] = [];
	private future: string[] = [];
	private lastKey: string | null = null;
	private lastTime = 0;

	constructor() {
		// Autosave: JSON.stringify reads every field, so any change re-runs this.
		$effect.root(() => {
			$effect(() => localStorage.setItem(STORAGE_KEY, JSON.stringify(this.song)));
			$effect(() => localStorage.setItem(CURRENT_KEY, this.workspace.id));
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
		this.version++;
		this.syncFlags();
	}

	undo = () => this.step(this.past, this.future);
	redo = () => this.step(this.future, this.past);

	private step(from: string[], to: string[]) {
		const snap = from.pop();
		if (!snap) return;
		to.push(JSON.stringify(this.song));
		this.song = JSON.parse(snap);
		this.version++;
		this.lastKey = null;
		this.syncFlags();
	}

	private syncFlags() {
		this.canUndo = this.past.length > 0;
		this.canRedo = this.future.length > 0;
	}

	// ---- song ------------------------------------------------------------

	setBpm = (bpm: number) => this.commit((s) => (s.bpm = clamp(Math.round(bpm), 50, 200)), 'bpm');
	setHarmony = (h: Harmony) => this.commit((s) => (s.harmony = { ...h }));
	setSwing = (swing: Song['swing']) => this.commit((s) => (s.swing = swing));
	/** Replace everything (opening a file or a shared link). Undoable. */
	replaceSong = (next: unknown) => {
		const fresh = validate(clone(next));
		this.commit(() => {});
		this.song = fresh;
		this.version++;
	};

	// ---- workspaces ------------------------------------------------------

	switchTo = (id: string) => (this.current = id);
	/** Make a new empty workspace and switch to it. */
	addWorkspace = () => {
		const id = uid('w_');
		this.commit((s) => {
			const used = new Set(s.workspaces.map((w) => w.name));
			const name = NAMES.find((n) => !used.has(n)) ?? 'another';
			s.workspaces.push({ id, name, enabled: true, canvas: emptyCanvas() });
		});
		this.current = id;
	};
	/** Enabled workspaces play; disabled ones stay silent but keep their shapes. */
	toggleWorkspace = (id: string) =>
		this.commit((s) => {
			const w = s.workspaces.find((x) => x.id === id);
			if (w) w.enabled = !w.enabled;
		});
	renameWorkspace = (id: string, name: string) => {
		if (!name.trim() || this.song.workspaces.find((w) => w.id === id)?.name === name.trim()) return;
		this.commit((s) => {
			const w = s.workspaces.find((x) => x.id === id);
			if (w) w.name = name.trim().slice(0, 24);
		});
	};
	removeWorkspace = (id: string) => {
		if (this.song.workspaces.length <= 1) return;
		const i = this.song.workspaces.findIndex((w) => w.id === id);
		this.commit((s) => (s.workspaces = s.workspaces.filter((w) => w.id !== id)));
		if (this.current === id) this.current = this.song.workspaces[Math.max(0, i - 1)].id;
	};

	// ---- canvas ----------------------------------------------------------

	/** Edit the current workspace's canvas. */
	private edit(fn: (c: Canvas) => void, key?: string) {
		const id = this.workspace.id;
		this.commit((s) => {
			const w = s.workspaces.find((x) => x.id === id);
			if (w) fn(w.canvas);
		}, key);
	}
	private note = (c: Canvas, id: string) => c.notes.find((n) => n.id === id);
	private effect = (c: Canvas, id: string) => c.effects.find((e) => e.id === id);

	/** Place a note (snapped to its row); optionally chained from another note. */
	addNote = (n: Omit<Note, 'id'>, connectFrom?: string) => {
		const id = uid('n_');
		this.edit((c) => {
			c.notes.push({ ...n, id, y: yOfRow(rowOf(n.y)) });
			if (connectFrom) c.lines.push({ id: uid('l_'), from: connectFrom, to: id, div: 1 });
		});
		return this.note(this.canvas, id)!;
	};
	/** Move / resize / turn a note. Pass a gesture id so a whole drag is one undo step. */
	updateNote = (id: string, patch: Partial<Note>, gesture?: string) =>
		this.edit((c) => {
			const n = this.note(c, id);
			if (n) Object.assign(n, patch, { size: clamp(patch.size ?? n.size, 8, 40), rot: wrap(patch.rot ?? n.rot) });
		}, gesture);
	cycleInstrument = (id: string) =>
		this.edit((c) => {
			const n = this.note(c, id);
			if (n) n.inst = nextInstrument(n.inst);
		});
	removeNote = (id: string) =>
		this.edit((c) => {
			c.notes = c.notes.filter((n) => n.id !== id);
			c.lines = c.lines.filter((l) => l.from !== id && l.to !== id);
		});
	/** Draw a line A→B; drawing the same pair again removes it. */
	toggleLine = (from: string, to: string) =>
		this.edit((c) => {
			const i = c.lines.findIndex((l) => (l.from === from && l.to === to) || (l.from === to && l.to === from));
			if (i >= 0) c.lines.splice(i, 1);
			else c.lines.push({ id: uid('l_'), from, to, div: 1 });
		});
	/** Split a line into more pieces (1→2→3→4→1), or step back with `back`. */
	stepLine = (id: string, back = false) =>
		this.edit((c) => {
			const l = c.lines.find((x) => x.id === id);
			if (l) l.div = ((((l.div - 1 + (back ? -1 : 1)) % 4) + 4) % 4 + 1) as Line['div'];
		});
	addEffect = (e: Omit<Effect, 'id'>) => {
		const id = uid('e_');
		this.edit((c) => c.effects.push({ ...e, id, r: clamp(e.r, 50, 320) }));
		return this.effect(this.canvas, id)!;
	};
	updateEffect = (id: string, patch: Partial<Effect>, gesture?: string) =>
		this.edit((c) => {
			const e = this.effect(c, id);
			if (e) Object.assign(e, patch, { r: clamp(patch.r ?? e.r, 50, 320), rot: wrap(patch.rot ?? e.rot) });
		}, gesture);
	removeEffect = (id: string) => this.edit((c) => (c.effects = c.effects.filter((e) => e.id !== id)));
	clearCanvas = () => this.edit((c) => Object.assign(c, emptyCanvas()));
	/** Drop in a ready-made group (starters); ids are made fresh. */
	addGroup = (group: Canvas) => {
		const map = new Map<string, string>();
		this.edit((c) => {
			for (const n of group.notes) {
				map.set(n.id, uid('n_'));
				c.notes.push({ ...n, id: map.get(n.id)!, y: yOfRow(rowOf(n.y)) });
			}
			for (const l of group.lines) c.lines.push({ ...l, id: uid('l_'), from: map.get(l.from)!, to: map.get(l.to)! });
			for (const e of group.effects) c.effects.push({ ...e, id: uid('e_') });
		});
	};
}

export const songStore = new SongStore();

// ---- save / open files -----------------------------------------------------

export function exportSong() {
	const blob = new Blob([JSON.stringify(songStore.song, null, 2)], { type: 'application/json' });
	const a = document.createElement('a');
	a.href = URL.createObjectURL(blob);
	a.download = 'shape-sequencer-song.json';
	a.click();
	URL.revokeObjectURL(a.href);
}

export async function importSong(file: File) {
	songStore.replaceSong(JSON.parse(await file.text()));
}
