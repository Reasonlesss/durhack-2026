/**
 * Turns a drawn stroke into a shape by template matching: the stroke is resampled,
 * normalised, and compared with ideal outlines at every starting point and both
 * directions. Open strokes are rejected. Used live for the ghost preview, and on release.
 */
export type Drawn = 'circle' | 'square' | 'triangle' | 'diamond' | 'star' | 'hexagon';
type P = [number, number];

const N = 48;

function resample(pts: P[], n: number): P[] {
	const len = pts.slice(1).reduce((s, p, i) => s + Math.hypot(p[0] - pts[i][0], p[1] - pts[i][1]), 0);
	const step = len / (n - 1);
	const out: P[] = [pts[0]];
	let acc = 0;
	const src = pts.slice();
	for (let i = 1; i < src.length; i++) {
		const [ax, ay] = src[i - 1];
		const [bx, by] = src[i];
		const d = Math.hypot(bx - ax, by - ay);
		if (acc + d >= step && d > 0) {
			const t = (step - acc) / d;
			const q: P = [ax + t * (bx - ax), ay + t * (by - ay)];
			out.push(q);
			src.splice(i, 0, q);
			acc = 0;
		} else acc += d;
	}
	while (out.length < n) out.push(pts[pts.length - 1]);
	return out.slice(0, n);
}

function normalise(pts: P[]): P[] {
	const xs = pts.map((p) => p[0]);
	const ys = pts.map((p) => p[1]);
	const cx = xs.reduce((a, b) => a + b) / pts.length;
	const cy = ys.reduce((a, b) => a + b) / pts.length;
	const s = Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys)) || 1;
	return pts.map(([x, y]) => [(x - cx) / s, (y - cy) / s]);
}

function outline(points: P[]): P[] {
	return normalise(resample([...points, points[0]], N));
}
const reg = (n: number, start: number, r = 1) =>
	Array.from({ length: n }, (_, i): P => [Math.cos(start + (i * 2 * Math.PI) / n) * r, Math.sin(start + (i * 2 * Math.PI) / n) * r]);

const TEMPLATES: { name: Drawn; pts: P[] }[] = [
	{ name: 'circle', pts: outline(reg(32, 0)) },
	{ name: 'square', pts: outline(reg(4, Math.PI / 4)) },
	{ name: 'diamond', pts: outline(reg(4, 0)) },
	{ name: 'triangle', pts: outline(reg(3, -Math.PI / 2)) },
	{ name: 'triangle', pts: outline(reg(3, Math.PI / 2)) },
	{ name: 'hexagon', pts: outline(reg(6, 0)) },
	{
		name: 'star',
		pts: outline(Array.from({ length: 10 }, (_, i): P => {
			const a = -Math.PI / 2 + (i * Math.PI) / 5;
			const r = i % 2 ? 0.42 : 1;
			return [Math.cos(a) * r, Math.sin(a) * r];
		}))
	}
];

/** Best average distance over every cyclic shift and both directions. */
function distance(a: P[], b: P[]) {
	let best = Infinity;
	for (const dir of [1, -1]) {
		for (let shift = 0; shift < N; shift += 2) {
			let sum = 0;
			for (let i = 0; i < N && sum < best * N; i++) {
				const q = b[(((shift + dir * i) % N) + N) % N];
				sum += Math.hypot(a[i][0] - q[0], a[i][1] - q[1]);
			}
			best = Math.min(best, sum / N);
		}
	}
	return best;
}

export interface Recognised {
	shape: Drawn;
	/** Centre and half-size of the stroke's bounding box. */
	cx: number;
	cy: number;
	r: number;
	/** Apex pointing up or down (triangles). */
	apexUp: boolean;
}

/** Null when the stroke is too short or not closed. */
export function recognise(stroke: P[]): Recognised | null {
	if (stroke.length < 8) return null;
	const xs = stroke.map((p) => p[0]);
	const ys = stroke.map((p) => p[1]);
	const w = Math.max(...xs) - Math.min(...xs);
	const h = Math.max(...ys) - Math.min(...ys);
	const size = Math.max(w, h);
	if (size < 16) return null;
	const [sx, sy] = stroke[0];
	const [ex, ey] = stroke[stroke.length - 1];
	if (Math.hypot(ex - sx, ey - sy) > size * 0.35) return null; // open stroke
	const pts = outline(stroke);
	let best = TEMPLATES[0];
	let score = Infinity;
	for (const t of TEMPLATES) {
		const d = distance(pts, t.pts);
		if (d < score) [score, best] = [d, t];
	}
	const cx = (Math.max(...xs) + Math.min(...xs)) / 2;
	const cy = (Math.max(...ys) + Math.min(...ys)) / 2;
	const centroidY = ys.reduce((a, b) => a + b) / ys.length;
	return { shape: best.name, cx, cy, r: size / 2, apexUp: centroidY > cy };
}
