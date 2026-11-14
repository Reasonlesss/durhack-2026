/**
 * Shape outlines as SVG path strings centred on (0,0). The same strings feed the shelf's
 * SVG icons and the canvas renderer (via Path2D), so instruments look identical everywhere.
 */
import type { EffectKind, Instrument } from './types';

export interface ShapeDef {
	d: string;
	/** Drawn as an outline instead of filled (siren squiggle, pause ring). */
	stroke?: boolean;
	dashed?: boolean;
}

const f = (n: number) => +n.toFixed(2);
function poly(points: [number, number][]) {
	return 'M' + points.map(([x, y]) => `${f(x)} ${f(y)}`).join('L') + 'Z';
}
function regular(n: number, r: number, start = -Math.PI / 2) {
	return Array.from({ length: n }, (_, i): [number, number] => {
		const a = start + (i * Math.PI * 2) / n;
		return [Math.cos(a) * r, Math.sin(a) * r];
	});
}
function star(points: number, outer: number, inner: number) {
	return Array.from({ length: points * 2 }, (_, i): [number, number] => {
		const a = -Math.PI / 2 + (i * Math.PI) / points;
		const r = i % 2 ? inner : outer;
		return [Math.cos(a) * r, Math.sin(a) * r];
	});
}
const circle = (r: number) => `M${-r} 0a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0Z`;

/** Note shape for an instrument at diameter `size`. */
export function noteShape(inst: Instrument, size: number): ShapeDef {
	const r = size / 2;
	switch (inst) {
		case 'lead':
			return { d: circle(r) };
		case 'chords': {
			const w = r * 1.35;
			const h = r * 0.62;
			return { d: `M${f(-w + h)} ${f(-h)}H${f(w - h)}a${f(h)} ${f(h)} 0 0 1 0 ${f(2 * h)}H${f(-w + h)}a${f(h)} ${f(h)} 0 0 1 0 ${f(-2 * h)}Z` };
		}
		case 'bells':
			return { d: poly(regular(3, r * 1.18)) };
		case 'bass':
			return { d: poly(regular(4, r * 1.2, -Math.PI / 4)) };
		case 'drums':
			return { d: poly(regular(4, r * 1.2)) };
		case 'stab':
			return { d: poly(star(5, r * 1.25, r * 0.55)) };
		case 'acid':
			return {
				d: poly([
					[0.25, -1.2], [-0.55, 0.12], [-0.02, 0.12], [-0.3, 1.2], [0.6, -0.2], [0.05, -0.2], [0.35, -1.2]
				].map(([x, y]) => [x * r, y * r] as [number, number]))
			};
		case 'siren': {
			const w = r * 1.3;
			let d = `M${f(-w)} 0`;
			for (let i = 1; i <= 24; i++) {
				const x = -w + (2 * w * i) / 24;
				d += `L${f(x)} ${f(Math.sin((i / 24) * Math.PI * 3) * r * 0.55)}`;
			}
			return { d, stroke: true };
		}
		case 'pause':
			return { d: circle(r), stroke: true, dashed: true };
	}
}

/** Effect outline for the shelf icon (the canvas draws real outlines from effectPolygon). */
export function effectIcon(kind: EffectKind, r: number): string {
	switch (kind) {
		case 'dreamy':
			return circle(r);
		case 'echo':
			return poly(regular(4, r * 1.15, -Math.PI / 4));
		case 'muffle':
			return poly(regular(3, r * 1.2));
		case 'crunch':
			return poly(regular(6, r, 0));
		case 'distortion':
			return poly(star(12, r * 1.15, r * 0.8));
	}
}
