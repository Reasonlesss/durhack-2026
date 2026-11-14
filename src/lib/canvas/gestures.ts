/** What a recognised drawn shape turns into. Small strokes make notes; large ones effects. */
import type { Recognised } from '../recognise';
import type { Effect, Instrument } from '../types';

/** World-space size (bounding box) under which a stroke becomes a note. */
export const NOTE_STROKE_MAX = 80;

export function ghostInstrument(r: Recognised): Instrument {
	return ({ circle: 'lead', square: 'bass', triangle: 'bells', diamond: 'drums', star: 'stab', hexagon: 'chords' } as const)[r.shape];
}

export function ghostEffect(r: Recognised): Effect {
	const kind = ({ circle: 'dreamy', square: 'echo', diamond: 'echo', triangle: 'muffle', hexagon: 'crunch', star: 'distortion' } as const)[r.shape];
	// Pointy outlines need a larger radius to cover what was drawn.
	const scale = kind === 'muffle' ? 1.35 : kind === 'echo' ? 1.15 : 1;
	return {
		id: 'ghost',
		kind,
		x: r.cx,
		y: r.cy,
		r: Math.max(50, Math.min(320, r.r * scale)),
		rot: r.shape === 'diamond' ? 45 : r.shape === 'triangle' && !r.apexUp ? 180 : 0
	};
}
