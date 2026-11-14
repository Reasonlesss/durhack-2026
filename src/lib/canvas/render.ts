/**
 * Draws one frame of a part's canvas with Canvas 2D (GPU-composited): row guides, effect
 * outlines, chain lines, notes, glows, ripples and gesture previews. Pure: it reads a
 * RenderState and paints; all interaction lives in Stage.svelte.
 */
import type { Graph } from '../chain';
import { effectPolygon } from '../effects';
import { ROW0_Y, ROW_GAP, ROWS, rowOf } from '../music';
import type { Recognised } from '../recognise';
import { noteShape } from '../shapes';
import type { Canvas, Effect, Line, Note } from '../types';
import type { VisEvent } from '../vis';
import { ghostEffect, ghostInstrument } from './gestures';

export const CHALK = '#ECE7DD';
const FLASH = '#FFF4DC';
const GROUND = '#0D0E11';

export interface View {
	/** World point at the screen's top-left. */
	x: number;
	y: number;
	/** Zoom: screen px per world px. */
	k: number;
}

export interface RenderState {
	canvas: Canvas;
	graph: Graph;
	view: View;
	width: number;
	height: number;
	accent: string;
	/** Audio clock now (seconds), for flashes. -1 when there is no audio yet. */
	now: number;
	events: VisEvent[];
	hoverId: string | null;
	liftId: string | null;
	/** Row guides + side gauge while a note is dragged. */
	dragRow: number | null;
	chainDraft: { from: Note; x: number; y: number; snap: Note | null } | null;
	stroke: [number, number][] | null;
	ghost: (Recognised & { asEffect: boolean }) | null;
	/** Effect being touched (brighter outline). */
	touchedEffect: string | null;
	/** Something is held over the shelf and will be removed on release. */
	overShelf: boolean;
}

export const toScreen = (v: View, x: number, y: number): [number, number] => [(x - v.x) * v.k, (y - v.y) * v.k];
export const toWorld = (v: View, sx: number, sy: number): [number, number] => [sx / v.k + v.x, sy / v.k + v.y];
/** Screen-px radius of a note's touch area: max(56px, 2.8 × size) wide. */
export const hitRadius = (n: Note, k: number) => Math.max(28, 1.4 * n.size * k);
export const bodyRadius = (n: Note, k: number) => hitRadius(n, k) * 0.62;

const pathCache = new Map<string, Path2D>();
function notePath(n: Note) {
	const key = `${n.inst}:${Math.round(n.size)}`;
	let p = pathCache.get(key);
	if (!p) pathCache.set(key, (p = new Path2D(noteShape(n.inst, Math.round(n.size)).d)));
	return p;
}

/** Visual events active right now for an id, as progress 0..1 (or null). */
function activity(s: RenderState, kind: VisEvent['kind'], id: string, tail = 0): number | null {
	if (s.now < 0) return null;
	for (const e of s.events) {
		if (e.kind !== kind || e.id !== id) continue;
		const p = (s.now - e.at) / (e.dur + tail);
		if (p >= 0 && p <= 1) return p;
	}
	return null;
}

export function draw(g: CanvasRenderingContext2D, s: RenderState) {
	g.clearRect(0, 0, s.width, s.height);
	if (s.dragRow !== null) drawRows(g, s);
	for (const e of s.canvas.effects) drawEffect(g, s, e);
	const byId = new Map(s.canvas.notes.map((n) => [n.id, n]));
	for (const l of s.canvas.lines) {
		const a = byId.get(l.from);
		const b = byId.get(l.to);
		if (a && b) drawLine(g, s, l, a, b);
	}
	for (const n of s.canvas.notes) drawNote(g, s, n);
	if (s.chainDraft) drawDraft(g, s);
	if (s.stroke) drawStroke(g, s);
}

// ---- rows --------------------------------------------------------------------------

function drawRows(g: CanvasRenderingContext2D, s: RenderState) {
	g.save();
	for (let r = 0; r < ROWS; r++) {
		const [, y] = toScreen(s.view, 0, ROW0_Y - r * ROW_GAP);
		g.globalAlpha = r === s.dragRow ? 0.35 : 0.08;
		g.fillStyle = s.accent;
		g.fillRect(0, Math.round(y), s.width, 1);
	}
	// Side gauge: a quiet ladder on the right edge showing where the note sits.
	const x = s.width - 18;
	for (let r = 0; r < ROWS; r++) {
		const y = s.height / 2 + (ROWS / 2 - r) * 9;
		g.globalAlpha = r === s.dragRow ? 1 : 0.25;
		g.fillStyle = r === s.dragRow ? s.accent : CHALK;
		g.fillRect(x - (r === s.dragRow ? 8 : 4), y, r === s.dragRow ? 12 : 6, 2);
	}
	g.restore();
}

// ---- effects -------------------------------------------------------------------------

export function effectHandle(e: Effect): [number, number] {
	const a = ((e.rot - 90) * Math.PI) / 180;
	return [e.x + Math.cos(a) * e.r, e.y + Math.sin(a) * e.r];
}

function tracePoly(g: CanvasRenderingContext2D, s: RenderState, pts: [number, number][], dx = 0, dy = 0, scale = 1, cx = 0, cy = 0) {
	g.beginPath();
	pts.forEach(([x, y], i) => {
		const [sx, sy] = toScreen(s.view, cx + (x - cx) * scale, cy + (y - cy) * scale);
		i ? g.lineTo(sx + dx, sy + dy) : g.moveTo(sx + dx, sy + dy);
	});
	g.closePath();
}

function drawEffect(g: CanvasRenderingContext2D, s: RenderState, e: Effect) {
	const pts = effectPolygon(e);
	const hit = activity(s, 'effect', e.id);
	const touched = s.touchedEffect === e.id;
	const glow = hit !== null ? 1 - hit : 0;
	let dx = 0;
	let dy = 0;
	let scale = 1;
	if (glow && e.kind === 'distortion') [dx, dy] = [(Math.random() - 0.5) * 4 * glow, (Math.random() - 0.5) * 4 * glow];
	if (glow && e.kind === 'crunch') scale = 1 + Math.sin(s.now * 70) * 0.015 * glow;

	g.save();
	g.strokeStyle = s.accent;
	g.lineJoin = 'round';
	if (e.kind === 'muffle') {
		tracePoly(g, s, pts, dx, dy, scale, e.x, e.y);
		g.globalAlpha = 0.05 + glow * 0.05;
		g.fillStyle = s.accent;
		g.fill();
	}
	if (e.kind === 'dreamy') {
		for (const f of [0.66, 0.36]) {
			tracePoly(g, s, pts, dx, dy, f, e.x, e.y);
			g.globalAlpha = 0.1 + glow * 0.1;
			g.lineWidth = 1;
			g.stroke();
		}
	}
	if (e.kind === 'echo') {
		for (const [i, a] of [[1, 0.22], [2, 0.1]] as const) {
			tracePoly(g, s, pts, dx + i * 7, dy + i * 7, scale, e.x, e.y);
			g.globalAlpha = a + glow * 0.15;
			g.lineWidth = 1;
			g.stroke();
		}
	}
	tracePoly(g, s, pts, dx, dy, scale, e.x, e.y);
	g.globalAlpha = touched ? 0.95 : 0.5 + glow * 0.5;
	g.lineWidth = touched || glow ? 2 : 1.5;
	if (glow || touched) {
		g.shadowColor = s.accent;
		g.shadowBlur = 16 * Math.max(glow, touched ? 0.6 : 0);
	}
	g.stroke();
	g.shadowBlur = 0;
	const [hx, hy] = toScreen(s.view, ...effectHandle(e));
	g.globalAlpha = touched ? 1 : 0.75;
	g.fillStyle = s.accent;
	g.beginPath();
	g.arc(hx, hy, 5, 0, Math.PI * 2);
	g.fill();
	g.restore();
}

// ---- lines ---------------------------------------------------------------------------

/** Gentle curve from A's edge to B's edge. Returns a point-at-t function. */
export function lineCurve(v: View, a: Note, b: Note) {
	const [ax, ay] = toScreen(v, a.x, a.y);
	const [bx, by] = toScreen(v, b.x, b.y);
	const len = Math.hypot(bx - ax, by - ay) || 1;
	const [ux, uy] = [(bx - ax) / len, (by - ay) / len];
	const ra = (a.size / 2) * v.k + 6;
	const rb = (b.size / 2) * v.k + 8;
	const [sx, sy] = [ax + ux * ra, ay + uy * ra];
	const [ex, ey] = [bx - ux * rb, by - uy * rb];
	const bend = Math.min(40, len * 0.1);
	const [cx, cy] = [(sx + ex) / 2 - uy * bend, (sy + ey) / 2 + ux * bend];
	return (t: number): [number, number] => {
		const m = 1 - t;
		return [m * m * sx + 2 * m * t * cx + t * t * ex, m * m * sy + 2 * m * t * cy + t * t * ey];
	};
}

function drawLine(g: CanvasRenderingContext2D, s: RenderState, l: Line, a: Note, b: Note) {
	const at = lineCurve(s.view, a, b);
	const [sx, sy] = at(0);
	const [ex, ey] = at(1);
	g.save();
	const grad = g.createLinearGradient(sx, sy, ex, ey);
	grad.addColorStop(0, 'rgba(236,231,221,0.1)');
	grad.addColorStop(1, 'rgba(236,231,221,0.5)');
	g.strokeStyle = grad;
	g.lineWidth = 1.6;
	g.lineCap = 'round';
	const gap = l.div > 1 ? 0.035 : 0;
	for (let i = 0; i < l.div; i++) {
		const t0 = i / l.div + (i ? gap : 0);
		const t1 = (i + 1) / l.div - (i < l.div - 1 ? gap : 0);
		g.beginPath();
		for (let k = 0; k <= 16; k++) {
			const [x, y] = at(t0 + ((t1 - t0) * k) / 16);
			k ? g.lineTo(x, y) : g.moveTo(x, y);
		}
		g.stroke();
		if (l.div > 1) {
			const [mx, my] = at((t0 + t1) / 2);
			g.fillStyle = 'rgba(236,231,221,0.45)';
			g.beginPath();
			g.arc(mx, my, 2, 0, Math.PI * 2);
			g.fill();
		}
	}
	// Chevron at the arriving end.
	const [px, py] = at(0.97);
	const ang = Math.atan2(ey - py, ex - px);
	g.strokeStyle = 'rgba(236,231,221,0.55)';
	g.beginPath();
	g.moveTo(ex - Math.cos(ang - 0.5) * 8, ey - Math.sin(ang - 0.5) * 8);
	g.lineTo(ex, ey);
	g.lineTo(ex - Math.cos(ang + 0.5) * 8, ey - Math.sin(ang + 0.5) * 8);
	g.stroke();
	// Travelling dot while the phrase passes through.
	const p = activity(s, 'line', l.id);
	if (p !== null) {
		const [dx, dy] = at(p);
		g.fillStyle = s.accent;
		g.shadowColor = s.accent;
		g.shadowBlur = 10;
		g.beginPath();
		g.arc(dx, dy, 3.5, 0, Math.PI * 2);
		g.fill();
	}
	g.restore();
}

// ---- notes ---------------------------------------------------------------------------

function drawNote(g: CanvasRenderingContext2D, s: RenderState, n: Note) {
	const [x, y] = toScreen(s.view, n.x, n.y);
	if (x < -80 || y < -80 || x > s.width + 80 || y > s.height + 80) return;
	const k = s.view.k;
	const flash = activity(s, 'note', n.id);
	const ripple = activity(s, 'note', n.id, 0.4);
	const lifted = s.liftId === n.id;
	const r = (n.size / 2) * k;

	g.save();
	if (ripple !== null && n.inst !== 'pause') {
		g.strokeStyle = s.accent;
		g.globalAlpha = (1 - ripple) * 0.45;
		g.lineWidth = 1.5;
		g.beginPath();
		g.arc(x, y, r + 6 + ripple * 46 * k, 0, Math.PI * 2);
		g.stroke();
		g.globalAlpha = 1;
	}
	// Leads its chain: thin accent ring.
	if (s.graph.roots.has(n.id) && s.graph.chained.has(n.id)) {
		g.strokeStyle = s.accent;
		g.globalAlpha = 0.7;
		g.lineWidth = 1.2;
		g.beginPath();
		g.arc(x, y, r + 9, 0, Math.PI * 2);
		g.stroke();
		g.globalAlpha = 1;
	}
	if (s.hoverId === n.id || s.chainDraft?.snap?.id === n.id) {
		const snap = s.chainDraft?.snap?.id === n.id;
		g.strokeStyle = snap ? s.accent : 'rgba(236,231,221,0.35)';
		g.lineWidth = snap ? 2 : 1;
		g.setLineDash(snap ? [] : [4, 5]);
		g.beginPath();
		g.arc(x, y, snap ? r + 12 : hitRadius(n, k), 0, Math.PI * 2);
		g.stroke();
		g.setLineDash([]);
	}

	g.translate(x, y);
	g.scale(k * (lifted ? 1.3 : 1), k * (lifted ? 1.3 : 1));
	g.rotate((n.rot * Math.PI) / 180);
	const def = noteShape(n.inst, Math.round(n.size));
	const path = notePath(n);
	if (lifted) {
		g.shadowColor = 'rgba(0,0,0,0.6)';
		g.shadowBlur = 18;
		g.shadowOffsetY = 6;
	}
	if (flash !== null) {
		g.shadowColor = s.accent;
		g.shadowBlur = 24 * (1 - flash) + 6;
		g.shadowOffsetY = 0;
	}
	const colour = flash !== null ? FLASH : CHALK;
	if (def.stroke) {
		g.strokeStyle = colour;
		g.lineWidth = n.inst === 'pause' ? 1.6 : 3;
		g.lineCap = 'round';
		g.globalAlpha = n.inst === 'pause' ? 0.45 : 1;
		if (def.dashed) g.setLineDash([3, 3]);
		g.stroke(path);
	} else {
		g.fillStyle = colour;
		g.fill(path);
	}
	g.shadowBlur = 0;
	// Notch: shows which way the note is turned (how long it rings).
	if (n.inst !== 'pause') {
		g.globalAlpha = 1;
		g.fillStyle = GROUND;
		g.beginPath();
		g.arc(0, -n.size * 0.28, Math.max(1.4, n.size * 0.08), 0, Math.PI * 2);
		g.fill();
	}
	g.restore();
}

// ---- gesture previews ------------------------------------------------------------------

function drawDraft(g: CanvasRenderingContext2D, s: RenderState) {
	const d = s.chainDraft!;
	const [ax, ay] = toScreen(s.view, d.from.x, d.from.y);
	const [bx, by] = d.snap ? toScreen(s.view, d.snap.x, d.snap.y) : toScreen(s.view, d.x, d.y);
	g.save();
	g.strokeStyle = s.accent;
	g.lineWidth = 1.6;
	g.setLineDash([6, 6]);
	g.beginPath();
	g.moveTo(ax, ay);
	g.lineTo(bx, by);
	g.stroke();
	g.restore();
}

function drawStroke(g: CanvasRenderingContext2D, s: RenderState) {
	g.save();
	g.strokeStyle = 'rgba(236,231,221,0.5)';
	g.lineWidth = 2;
	g.lineCap = 'round';
	g.lineJoin = 'round';
	g.beginPath();
	s.stroke!.forEach(([x, y], i) => {
		const [sx, sy] = toScreen(s.view, x, y);
		i ? g.lineTo(sx, sy) : g.moveTo(sx, sy);
	});
	g.stroke();
	if (s.ghost) {
		// What the stroke will become on release.
		const gh = s.ghost;
		g.strokeStyle = s.accent;
		g.globalAlpha = 0.8;
		g.setLineDash([5, 6]);
		if (gh.asEffect) {
			const e = ghostEffect(gh);
			tracePoly(g, s, effectPolygon(e));
			g.stroke();
		} else {
			const [cx, cy] = toScreen(s.view, gh.cx, gh.cy);
			g.translate(cx, cy);
			g.scale(s.view.k, s.view.k);
			g.lineWidth = 2 / s.view.k;
			g.stroke(new Path2D(noteShape(ghostInstrument(gh), Math.max(12, Math.min(40, gh.r * 2))).d));
		}
	}
	g.restore();
}


/** Is world point (x, y) within `px` screen pixels of the effect's outline? */
export function nearOutline(v: View, e: Effect, x: number, y: number, px: number) {
	const pts = effectPolygon(e);
	const [sx, sy] = toScreen(v, x, y);
	for (let i = 0; i < pts.length; i++) {
		const [ax, ay] = toScreen(v, ...pts[i]);
		const [bx, by] = toScreen(v, ...pts[(i + 1) % pts.length]);
		if (segDist(sx, sy, ax, ay, bx, by) < px) return true;
	}
	return false;
}

export function segDist(px: number, py: number, ax: number, ay: number, bx: number, by: number) {
	const dx = bx - ax;
	const dy = by - ay;
	const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy || 1)));
	return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}

export const rowFor = (n: Note) => rowOf(n.y);
