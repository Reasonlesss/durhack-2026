<!--
	The shape canvas for one part. Everything visual is painted on a <canvas> by render.ts;
	this component owns the view (pan / zoom), every gesture, and a layer of invisible but
	focusable <button>s so each note and effect works with a keyboard and screen reader.
-->
<script lang="ts">
	import { onMount } from 'svelte';
	import Hud from './Hud.svelte';
	import Onboarding from './Onboarding.svelte';
	import Shelf, { type ShelfItem } from './Shelf.svelte';
	import { ghostEffect, ghostInstrument, NOTE_STROKE_MAX } from './gestures';
	import {
		bodyRadius,
		draw,
		effectHandle,
		hitRadius,
		lineCurve,
		nearOutline,
		toScreen,
		toWorld,
		type RenderState,
		type View
	} from './render';
	import { audio } from '../audio/engine';
	import { graphOf } from '../chain';
	import { describeEffect, effectPolygon, strengthAt } from '../effects';
	import { describeNote, lineWords, rowOf, yOfRow } from '../music';
	import { recognise, type Recognised } from '../recognise';
	import { songStore } from '../song.svelte';
	import { placeAt, STARTERS } from '../starters';
	import { preview, transport } from '../transport.svelte';
	import { FEEL_COLOURS, type Effect, type Instrument, type Line, type Note, type Part } from '../types';
	import { uid } from '../ids';
	import { vis } from '../vis';
	import { ui } from '../ui.svelte';

	let { part }: { part: Part } = $props();

	let stage: HTMLElement;
	let cnv: HTMLCanvasElement;
	let shelfEl = $state<HTMLElement>();
	let width = $state(0);
	let height = $state(0);
	let view = $state<View>({ x: 0, y: 0, k: 1 });
	let lastInst: Instrument = 'lead';

	const canvas = $derived(part.canvas);
	const accent = $derived(FEEL_COLOURS[songStore.song.harmony.feel]);
	const graph = () => graphOf(part.canvas, songStore.version);

	// ---- readout chip ------------------------------------------------------------------
	let readout = $state<{ text: string; x: number; y: number; key: number } | null>(null);
	let readoutTimer: ReturnType<typeof setTimeout> | undefined;

	/** Show words beside a screen point, picking a side that doesn't cover other notes. */
	function say(text: string, sx: number, sy: number, radius = 20) {
		const w = text.length * 7.3 + 22;
		const h = 28;
		const spots: [number, number][] = [
			[sx + radius + 12, sy - h / 2],
			[sx - radius - 12 - w, sy - h / 2],
			[sx - w / 2, sy - radius - 12 - h],
			[sx - w / 2, sy + radius + 12]
		];
		const others = canvas.notes.map((n) => {
			const [x, y] = toScreen(view, n.x, n.y);
			return { x, y, r: (n.size / 2) * view.k + 6 };
		});
		const clear = ([x, y]: [number, number]) =>
			x > 60 && y > 8 && x + w < width - 8 && y + h < height - 8 &&
			!others.some((o) => o.x > x - o.r && o.x < x + w + o.r && o.y > y - o.r && o.y < y + h + o.r && Math.hypot(o.x - sx, o.y - sy) > 2);
		const [x, y] = spots.find(clear) ?? spots[0];
		readout = { text, x, y, key: Math.random() };
		clearTimeout(readoutTimer);
		readoutTimer = setTimeout(() => (readout = null), 1600);
	}

	function roleOf(n: Note) {
		const g = graph();
		return g.chained.has(n.id) ? (g.roots.has(n.id) ? 'root' : 'chained') : 'alone';
	}
	function sayNote(n: Note) {
		const [x, y] = toScreen(view, n.x, n.y);
		say(describeNote(n, roleOf(n)), x, y, (n.size / 2) * view.k * 1.3);
	}
	const touching = (e: Effect) => {
		const poly = effectPolygon(e);
		return canvas.notes.filter((n) => strengthAt(e, poly, n.x, n.y) > 0).length;
	};
	function sayEffect(e: Effect) {
		const [x, y] = toScreen(view, ...effectHandle(e));
		say(describeEffect(e, touching(e)), x, y, 10);
	}

	// ---- hint line -------------------------------------------------------------------------
	let interactions = $state(Number(localStorage.getItem('shape-sequencer:hints') ?? 0));
	function interacted() {
		interactions++;
		localStorage.setItem('shape-sequencer:hints', String(interactions));
	}

	// ---- view ------------------------------------------------------------------------------
	function fit() {
		const pts = canvas.notes.length ? canvas.notes : [{ x: 0, y: 0 }, { x: 1440, y: 900 }];
		const xs = [...pts.map((n) => n.x), 0, 1440];
		const ys = [...pts.map((n) => n.y), 0, 900];
		const [x0, x1, y0, y1] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];
		const k = Math.min(1.4, Math.min((width - 140) / (x1 - x0 + 80), (height - 80) / (y1 - y0 + 80)));
		view = { k, x: (x0 + x1) / 2 - (width / k) / 2 + 30 / k, y: (y0 + y1) / 2 - height / k / 2 };
	}
	const pointer = (e: { clientX: number; clientY: number }): [number, number] => {
		const r = stage.getBoundingClientRect();
		return [e.clientX - r.left, e.clientY - r.top];
	};

	// ---- hit testing ---------------------------------------------------------------------
	type Hit =
		| { kind: 'note'; note: Note; zone: 'body' | 'ring' }
		| { kind: 'handle'; effect: Effect }
		| { kind: 'effect'; effect: Effect }
		| { kind: 'line'; line: Line }
		| { kind: 'empty' };

	function hitTest(sx: number, sy: number): Hit {
		for (const e of [...canvas.effects].reverse()) {
			const [hx, hy] = toScreen(view, ...effectHandle(e));
			if (Math.hypot(sx - hx, sy - hy) < 14) return { kind: 'handle', effect: e };
		}
		let best: { n: Note; d: number } | null = null;
		for (const n of canvas.notes) {
			const [x, y] = toScreen(view, n.x, n.y);
			const d = Math.hypot(sx - x, sy - y);
			if (d < hitRadius(n, view.k) && (!best || d < best.d)) best = { n, d };
		}
		if (best) return { kind: 'note', note: best.n, zone: best.d < bodyRadius(best.n, view.k) ? 'body' : 'ring' };
		const byId = new Map(canvas.notes.map((n) => [n.id, n]));
		for (const l of canvas.lines) {
			const a = byId.get(l.from);
			const b = byId.get(l.to);
			if (!a || !b) continue;
			const at = lineCurve(view, a, b);
			for (let t = 0; t <= 1; t += 0.05) {
				const [x, y] = at(t);
				if (Math.hypot(sx - x, sy - y) < 10) return { kind: 'line', line: l };
			}
		}
		const [wx, wy] = toWorld(view, sx, sy);
		for (const e of [...canvas.effects].reverse()) if (nearOutline(view, e, wx, wy, 14)) return { kind: 'effect', effect: e };
		return { kind: 'empty' };
	}

	// ---- gestures --------------------------------------------------------------------------
	type Gesture =
		| { kind: 'note'; note: Note; zone: 'body' | 'ring'; off: [number, number]; row: number }
		| { kind: 'effect'; effect: Effect; off: [number, number] }
		| { kind: 'handle'; effect: Effect }
		| { kind: 'line'; line: Line }
		| { kind: 'empty' }
		| { kind: 'pan'; view: View; at: [number, number] }
		| { kind: 'shelf'; item: ShelfItem };

	let gesture: Gesture | null = null;
	let gestureId = '';
	let downAt: [number, number] = [0, 0];
	let moved = false;
	const pointers = new Map<number, [number, number]>();
	let pinch: { d: number; a: number; mid: [number, number]; view: View; size?: number; rot?: number; r?: number } | null = null;

	// Things the renderer shows mid-gesture.
	let liftId: string | null = null;
	let dragRow: number | null = null;
	let chainDraft: RenderState['chainDraft'] = null;
	let stroke: [number, number][] | null = null;
	let ghost: RenderState['ghost'] = null;
	let touchedEffect: string | null = null;
	let hoverId: string | null = null;
	let overShelf = $state(false);
	let shelfGhost = $state<{ item: ShelfItem; x: number; y: number } | null>(null);
	let cursor = $state('default');

	/** Capture can fail (e.g. the pointer is already gone); gestures still work without it. */
	function capture(id: number) {
		try {
			stage.setPointerCapture(id);
		} catch {
			/* ignore */
		}
	}

	function isOverShelf(e: PointerEvent) {
		const r = shelfEl?.getBoundingClientRect();
		return !!r && e.clientX < r.right + 24 && e.clientY > r.top - 24 && e.clientY < r.bottom + 24;
	}

	function down(e: PointerEvent) {
		if ((e.target as HTMLElement).closest('[data-chrome]')) return;
		// A click on the canvas while a panel is open only closes the panel.
		if (ui.closePopover()) return;
		const p = pointer(e);
		pointers.set(e.pointerId, p);
		capture(e.pointerId);
		if (pointers.size === 2) return startPinch();
		if (pointers.size > 2) return;
		downAt = p;
		moved = false;
		gestureId = uid('g_');
		if (e.button === 1 || e.button === 2) {
			gesture = { kind: 'pan', view: { ...view }, at: p };
			return;
		}
		const hit = hitTest(...p);
		const [wx, wy] = toWorld(view, ...p);
		if (hit.kind === 'note') gesture = { kind: 'note', note: hit.note, zone: hit.zone, off: [wx - hit.note.x, wy - hit.note.y], row: rowOf(hit.note.y) };
		else if (hit.kind === 'effect') gesture = { kind: 'effect', effect: hit.effect, off: [wx - hit.effect.x, wy - hit.effect.y] };
		else if (hit.kind === 'handle') gesture = { kind: 'handle', effect: hit.effect };
		else if (hit.kind === 'line') gesture = { kind: 'line', line: hit.line };
		else {
			gesture = { kind: 'empty' };
			stroke = [[wx, wy]];
		}
		if (gesture.kind === 'effect' || gesture.kind === 'handle') touchedEffect = gesture.effect.id;
	}

	/** Called by the shelf: start dragging a new thing out of it. */
	function pickFromShelf(item: ShelfItem, e: PointerEvent) {
		gesture = { kind: 'shelf', item };
		pointers.set(e.pointerId, pointer(e));
		capture(e.pointerId);
		shelfGhost = { item, x: e.clientX, y: e.clientY };
	}

	function move(e: PointerEvent) {
		const p = pointer(e);
		if (!pointers.has(e.pointerId)) return hover(p);
		pointers.set(e.pointerId, p);
		if (pinch) return movePinch();
		if (!gesture) return;
		if (!moved && Math.hypot(p[0] - downAt[0], p[1] - downAt[1]) > 5) moved = true;
		const [wx, wy] = toWorld(view, ...p);
		overShelf = (gesture.kind === 'note' && gesture.zone === 'body') || gesture.kind === 'effect' ? isOverShelf(e) : false;

		switch (gesture.kind) {
			case 'pan':
				view = { ...gesture.view, x: gesture.view.x - (p[0] - gesture.at[0]) / view.k, y: gesture.view.y - (p[1] - gesture.at[1]) / view.k };
				break;
			case 'shelf':
				shelfGhost = { item: gesture.item, x: e.clientX, y: e.clientY };
				overShelf = isOverShelf(e);
				break;
			case 'note': {
				if (!moved) break;
				const n = gesture.note;
				if (gesture.zone === 'body') {
					liftId = n.id;
					songStore.updateNote(part.id, n.id, { x: wx - gesture.off[0], y: wy - gesture.off[1] }, gestureId);
					const row = rowOf(n.y);
					dragRow = row;
					if (row !== gesture.row) {
						gesture.row = row;
						preview(part.id, n, true);
					}
					sayNote(n);
				} else {
					// Snap to any note within 52px of the pointer.
					let snap: Note | null = null;
					let best = 52;
					for (const o of canvas.notes) {
						if (o.id === n.id) continue;
						const [ox, oy] = toScreen(view, o.x, o.y);
						const d = Math.hypot(ox - p[0], oy - p[1]);
						if (d < best) [best, snap] = [d, o];
					}
					chainDraft = { from: n, x: wx, y: wy, snap };
				}
				break;
			}
			case 'effect':
				if (!moved) break;
				songStore.updateEffect(part.id, gesture.effect.id, { x: wx - gesture.off[0], y: wy - gesture.off[1] }, gestureId);
				sayEffect(gesture.effect);
				break;
			case 'handle': {
				const ef = gesture.effect;
				const rot = (Math.atan2(wy - ef.y, wx - ef.x) * 180) / Math.PI + 90;
				songStore.updateEffect(part.id, ef.id, { rot }, gestureId);
				sayEffect(ef);
				break;
			}
			case 'empty':
				if (!moved || !stroke) break;
				stroke.push([wx, wy]);
				if (stroke.length % 3 === 0) ghost = makeGhost(stroke);
				break;
		}
	}

	function makeGhost(pts: [number, number][]): RenderState['ghost'] {
		const r: Recognised | null = recognise(pts);
		return r ? { ...r, asEffect: r.r * 2 >= NOTE_STROKE_MAX } : null;
	}

	function up(e: PointerEvent) {
		pointers.delete(e.pointerId);
		if (pinch) {
			if (pointers.size < 2) pinch = null;
			if (pointers.size === 0) end();
			return;
		}
		const g = gesture;
		if (!g) return end();
		const p = pointer(e);
		const [wx, wy] = toWorld(view, ...p);

		switch (g.kind) {
			case 'shelf':
				if (!isOverShelf(e)) dropFromShelf(g.item, wx, wy);
				break;
			case 'note': {
				const n = g.note;
				if (!moved) {
					songStore.cycleInstrument(part.id, n.id);
					lastInst = n.inst;
					preview(part.id, n);
					sayNote(n);
				} else if (g.zone === 'body') {
					if (isOverShelf(e)) songStore.removeNote(part.id, n.id);
					else songStore.updateNote(part.id, n.id, { y: yOfRow(rowOf(n.y)) }, gestureId);
				} else if (chainDraft?.snap) {
					songStore.toggleLine(part.id, n.id, chainDraft.snap.id);
				} else {
					const [nx, ny] = toScreen(view, n.x, n.y);
					if (Math.hypot(p[0] - nx, p[1] - ny) > 40) {
						const id = songStore.addNote(part.id, { inst: n.inst, x: wx, y: wy, size: n.size, rot: n.rot }, n.id);
						const made = canvas.notes.find((x) => x.id === id);
						if (made) preview(part.id, made);
					}
				}
				break;
			}
			case 'effect':
				if (moved && isOverShelf(e)) songStore.removeEffect(part.id, g.effect.id);
				else sayEffect(g.effect);
				break;
			case 'line':
				if (!moved) {
					songStore.stepLine(part.id, g.line.id, e.shiftKey);
					const [x, y] = p;
					say(lineWords(g.line.div), x, y, 8);
				}
				break;
			case 'empty':
				if (!moved) placeNote(wx, wy);
				else if (stroke) finishStroke(stroke);
				break;
		}
		interacted();
		end();
	}

	function end() {
		gesture = null;
		liftId = null;
		dragRow = null;
		chainDraft = null;
		stroke = null;
		ghost = null;
		touchedEffect = null;
		overShelf = false;
		shelfGhost = null;
	}

	function placeNote(wx: number, wy: number) {
		const id = songStore.addNote(part.id, { inst: lastInst, x: wx, y: wy, size: 22, rot: 60 });
		const n = canvas.notes.find((x) => x.id === id);
		if (n) {
			preview(part.id, n);
			sayNote(n);
		}
		if (!transport.everPlayed) transport.play(part.id);
	}

	function finishStroke(pts: [number, number][]) {
		const r = recognise(pts);
		if (!r) return; // open strokes do nothing
		if (r.r * 2 < NOTE_STROKE_MAX) {
			lastInst = ghostInstrument(r);
			const id = songStore.addNote(part.id, { inst: lastInst, x: r.cx, y: r.cy, size: Math.max(8, Math.min(40, r.r * 1.6)), rot: 60 });
			const n = canvas.notes.find((x) => x.id === id);
			if (n) (preview(part.id, n), sayNote(n));
		} else {
			const { id: _, ...e } = ghostEffect(r);
			const id = songStore.addEffect(part.id, e);
			const made = canvas.effects.find((x) => x.id === id);
			if (made) sayEffect(made);
		}
	}

	function dropFromShelf(item: ShelfItem, wx: number, wy: number) {
		if (item.kind === 'instrument') {
			lastInst = item.id;
			const id = songStore.addNote(part.id, { inst: item.id, x: wx, y: wy, size: 22, rot: 60 });
			const n = canvas.notes.find((x) => x.id === id);
			if (n) (preview(part.id, n), sayNote(n));
		} else if (item.kind === 'effect') {
			const id = songStore.addEffect(part.id, { kind: item.id, x: wx, y: wy, r: 150, rot: 0 });
			const made = canvas.effects.find((x) => x.id === id);
			if (made) sayEffect(made);
		} else {
			songStore.addGroup(part.id, placeAt(STARTERS[item.id](), wx, wy));
			if (item.id === 'a rave' && songStore.song.bpm < 140) songStore.setBpm(140);
			if (!transport.isPlaying) transport.play(part.id);
		}
		interacted();
	}

	// ---- pinch (two pointers) -----------------------------------------------------------
	function pinchGeom() {
		const [a, b] = [...pointers.values()];
		return {
			d: Math.hypot(b[0] - a[0], b[1] - a[1]) || 1,
			a: Math.atan2(b[1] - a[1], b[0] - a[0]),
			mid: [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2] as [number, number]
		};
	}
	function startPinch() {
		const g = pinchGeom();
		stroke = null;
		ghost = null;
		const target = gesture?.kind === 'note' ? gesture.note : null;
		const eff = gesture?.kind === 'effect' ? gesture.effect : null;
		pinch = { ...g, view: { ...view }, size: target?.size, rot: target?.rot ?? eff?.rot, r: eff?.r };
		gestureId = uid('g_');
		liftId = null;
	}
	function movePinch() {
		const p = pinch!;
		const g = pinchGeom();
		const scale = g.d / p.d;
		const turn = ((g.a - p.a) * 180) / Math.PI;
		if (gesture?.kind === 'note' && p.size !== undefined) {
			songStore.updateNote(part.id, gesture.note.id, { size: p.size * scale, rot: p.rot! + turn }, gestureId);
			sayNote(gesture.note);
		} else if (gesture?.kind === 'effect' && p.r !== undefined) {
			songStore.updateEffect(part.id, gesture.effect.id, { r: p.r * scale, rot: p.rot! + turn }, gestureId);
			sayEffect(gesture.effect);
		} else {
			// Pan and zoom the view, keeping the pinched world point under the fingers.
			const k = Math.max(0.25, Math.min(4, p.view.k * scale));
			const [wx, wy] = toWorld(p.view, ...p.mid);
			view = { k, x: wx - g.mid[0] / k, y: wy - g.mid[1] / k };
		}
	}

	// ---- hover & wheel ------------------------------------------------------------------
	function hover(p: [number, number]) {
		const hit = hitTest(...p);
		hoverId = hit.kind === 'note' ? hit.note.id : null;
		touchedEffect = hit.kind === 'effect' || hit.kind === 'handle' ? hit.effect.id : null;
		cursor =
			hit.kind === 'note' ? (hit.zone === 'body' ? 'grab' : 'crosshair') :
			hit.kind === 'handle' ? 'alias' :
			hit.kind === 'effect' ? 'move' :
			hit.kind === 'line' ? 'pointer' : 'default';
	}

	function wheel(e: WheelEvent) {
		if ((e.target as HTMLElement).closest('[data-chrome]')) return;
		e.preventDefault();
		const p = pointer(e);
		const hit = hitTest(...p);
		const [wx, wy] = toWorld(view, ...p);
		const turning = e.shiftKey || Math.abs(e.deltaX) > Math.abs(e.deltaY);
		const delta = turning ? (Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY) : e.deltaY;
		const note = hit.kind === 'note' ? hit.note : null;
		const effect = hit.kind === 'effect' || hit.kind === 'handle' ? hit.effect : canvas.effects.find((x) => strengthAt(x, effectPolygon(x), wx, wy) > 0);
		if (note && !e.ctrlKey) {
			songStore.updateNote(part.id, note.id, turning ? { rot: note.rot + delta * 0.6 } : { size: note.size - delta * 0.05 }, `wheel:${note.id}`);
			sayNote(note);
		} else if (effect && !e.ctrlKey) {
			songStore.updateEffect(part.id, effect.id, turning ? { rot: effect.rot + delta * 0.4 } : { r: effect.r - delta * 0.6 }, `wheel:${effect.id}`);
			sayEffect(effect);
		} else if (e.ctrlKey) {
			const k = Math.max(0.25, Math.min(4, view.k * Math.exp(-e.deltaY * 0.01)));
			view = { k, x: wx - p[0] / k, y: wy - p[1] / k };
		} else {
			view = { ...view, x: view.x + e.deltaX / view.k, y: view.y + e.deltaY / view.k };
		}
	}

	// ---- keyboard on the hidden buttons ----------------------------------------------------
	function noteKey(n: Note, e: KeyboardEvent) {
		const moves: Record<string, Partial<Note>> = {
			ArrowUp: { y: yOfRow(rowOf(n.y) + 1) },
			ArrowDown: { y: yOfRow(rowOf(n.y) - 1) },
			ArrowLeft: { x: n.x - 20 },
			ArrowRight: { x: n.x + 20 }
		};
		if (moves[e.key]) {
			e.preventDefault();
			songStore.updateNote(part.id, n.id, moves[e.key], `key:${n.id}`);
			if (e.key === 'ArrowUp' || e.key === 'ArrowDown') preview(part.id, n, true);
			sayNote(n);
		} else if (e.key === 'Delete' || e.key === 'Backspace') {
			e.preventDefault();
			songStore.removeNote(part.id, n.id);
		}
	}
	function effectKey(ef: Effect, e: KeyboardEvent) {
		const moves: Record<string, Partial<Effect>> = {
			ArrowUp: { r: ef.r + 15 },
			ArrowDown: { r: ef.r - 15 },
			ArrowLeft: { rot: ef.rot - 15 },
			ArrowRight: { rot: ef.rot + 15 }
		};
		if (moves[e.key]) {
			e.preventDefault();
			songStore.updateEffect(part.id, ef.id, moves[e.key], `key:${ef.id}`);
			sayEffect(ef);
		} else if (e.key === 'Delete' || e.key === 'Backspace') {
			e.preventDefault();
			songStore.removeEffect(part.id, ef.id);
		}
	}

	// ---- frame loop ------------------------------------------------------------------------
	onMount(() => {
		fit();
		const g = cnv.getContext('2d')!;
		let raf = 0;
		const frame = () => {
			const dpr = window.devicePixelRatio || 1;
			if (cnv.width !== Math.round(width * dpr) || cnv.height !== Math.round(height * dpr)) {
				cnv.width = Math.round(width * dpr);
				cnv.height = Math.round(height * dpr);
			}
			g.setTransform(dpr, 0, 0, dpr, 0, 0);
			const ctx = audio();
			draw(g, {
				canvas: part.canvas,
				graph: graph(),
				view,
				width,
				height,
				accent,
				now: ctx.state === 'running' ? ctx.currentTime : -1,
				events: vis.events.filter((v) => v.partId === part.id),
				hoverId,
				liftId,
				dragRow,
				chainDraft,
				stroke,
				ghost,
				touchedEffect,
				overShelf
			});
			raf = requestAnimationFrame(frame);
		};
		raf = requestAnimationFrame(frame);
		// Wheel must be non-passive so it can stop the page from scrolling.
		stage.addEventListener('wheel', wheel, { passive: false });
		return () => {
			cancelAnimationFrame(raf);
			stage.removeEventListener('wheel', wheel);
		};
	});

	const noteLabel = (n: Note) => `${describeNote(n, roleOf(n))}. Press to change its instrument.`;
</script>

<div
	class="stage"
	bind:this={stage}
	bind:clientWidth={width}
	bind:clientHeight={height}
	style="cursor: {cursor}"
	role="application"
	aria-label="Shape canvas for {part.name}. Tap to place a note."
	onpointerdown={down}
	onpointermove={move}
	onpointerup={up}
	onpointercancel={up}
	oncontextmenu={(e) => e.preventDefault()}
>
	<canvas bind:this={cnv} style="width: {width}px; height: {height}px"></canvas>

	<!-- Invisible, focusable stand-ins for each note and effect (keyboard + screen readers). -->
	<div class="a11y">
		{#each canvas.notes as n (n.id)}
			{@const [x, y] = toScreen(view, n.x, n.y)}
			<button
				class="ghostbtn"
				style="left: {x}px; top: {y}px; --s: {Math.max(44, n.size * view.k * 1.4)}px"
				aria-label={noteLabel(n)}
				tabindex="0"
				onclick={(e) => {
					if (e.detail !== 0) return; // pointer taps are handled by the stage
					songStore.cycleInstrument(part.id, n.id);
					preview(part.id, n);
					sayNote(n);
				}}
				onkeydown={(e) => noteKey(n, e)}
				onfocus={() => sayNote(n)}
			></button>
		{/each}
		{#each canvas.effects as ef (ef.id)}
			{@const [x, y] = toScreen(view, ...effectHandle(ef))}
			<button
				class="ghostbtn"
				style="left: {x}px; top: {y}px; --s: 44px"
				aria-label="{describeEffect(ef, touching(ef))}. Arrow keys resize and turn it."
				onclick={(e) => e.detail === 0 && sayEffect(ef)}
				onkeydown={(e) => effectKey(ef, e)}
				onfocus={() => sayEffect(ef)}
			></button>
		{/each}
	</div>

	<p class="hint" class:gone={interactions >= 6} data-chrome>
		tap anywhere to place a note · tap a note to change its instrument · drag from a note's ring to chain · pick the feel up top · tap the heartbeat to play
	</p>

	<div class="shelf-wrap" data-chrome bind:this={shelfEl}>
		<Shelf onpick={pickFromShelf} dropping={overShelf} onplace={(item) => {
			const [wx, wy] = toWorld(view, width / 2, height / 2);
			dropFromShelf(item, wx, wy);
		}} />
	</div>

	<div class="hud" data-chrome><Hud partId={part.id} /></div>

	<a class="back" href="/" data-chrome>← the whole song · {part.name.toLowerCase()}</a>

	{#if readout}
		{#key readout.key}
			<div class="readout" style="left: {readout.x}px; top: {readout.y}px" role="status">{readout.text}</div>
		{/key}
	{/if}

	{#if shelfGhost}
		<div class="carry" class:bin={overShelf} style="left: {shelfGhost.x}px; top: {shelfGhost.y}px" aria-hidden="true">
			{overShelf ? 'let go to put it back' : shelfGhost.item.label}
		</div>
	{/if}

	<Onboarding notes={canvas.notes.length} lines={canvas.lines.length} playing={transport.isPlaying} {width} {height} />
</div>

<style>
	.stage {
		position: relative;
		flex: 1;
		overflow: hidden;
		touch-action: none;
		user-select: none;
		-webkit-user-select: none;
	}
	canvas {
		position: absolute;
		inset: 0;
		display: block;
	}
	.a11y {
		position: absolute;
		inset: 0;
		pointer-events: none;
	}
	.ghostbtn {
		position: absolute;
		width: var(--s);
		height: var(--s);
		translate: -50% -50%;
		border: 0;
		padding: 0;
		background: none;
		border-radius: 50%;
		pointer-events: none;
	}
	.ghostbtn:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: 2px;
	}
	.hint {
		position: absolute;
		top: 14px;
		left: 18px;
		right: 380px;
		margin: 0;
		font: 12px var(--mono);
		color: var(--muted);
		text-transform: lowercase;
		transition: opacity 600ms ease;
		pointer-events: none;
	}
	.hint.gone {
		opacity: 0;
	}
	.shelf-wrap {
		position: absolute;
		left: 6px;
		top: 50%;
		translate: 0 -50%;
	}
	.hud {
		position: absolute;
		top: 8px;
		right: 12px;
	}
	.back {
		position: absolute;
		left: 18px;
		bottom: 10px;
		font: 12px var(--mono);
		color: var(--muted);
		text-decoration: none;
		min-height: 44px;
		display: flex;
		align-items: center;
		padding: 0 6px;
		border-radius: var(--r-sm);
	}
	.back:hover {
		color: var(--text);
	}
	.back:focus-visible {
		outline: 2px solid var(--accent);
	}
	.readout {
		position: absolute;
		padding: 6px 11px;
		border-radius: 10px;
		background: rgb(21 23 28 / 0.92);
		border: 1px solid var(--line);
		font: 12px var(--mono);
		color: var(--text);
		white-space: nowrap;
		pointer-events: none;
		animation: fade 1.6s ease forwards;
	}
	@keyframes fade {
		0%,
		70% {
			opacity: 1;
		}
		100% {
			opacity: 0;
		}
	}
	.carry {
		position: fixed;
		translate: 12px -50%;
		padding: 4px 9px;
		border-radius: 8px;
		background: rgb(21 23 28 / 0.9);
		border: 1px dashed var(--accent);
		font: 12px var(--mono);
		color: var(--text);
		pointer-events: none;
		z-index: 20;
	}
	.carry.bin {
		border-color: var(--muted);
		color: var(--muted);
	}
</style>
