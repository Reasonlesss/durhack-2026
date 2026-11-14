/**
 * Visual events scheduled to match audio (note flashes, effect flashes, travelling dots).
 * Plain arrays, not reactive: the canvas layer reads them every animation frame.
 */
export interface VisEvent {
	partId: string;
	kind: 'note' | 'effect' | 'line';
	id: string;
	/** AudioContext time the event becomes audible. */
	at: number;
	/** Seconds it lasts on screen. */
	dur: number;
}

export const vis = {
	events: [] as VisEvent[],
	push(e: VisEvent) {
		this.events.push(e);
		if (this.events.length > 2000) this.events.splice(0, 500);
	},
	/** Drop events that have finished. */
	prune(now: number) {
		this.events = this.events.filter((e) => e.at + e.dur > now - 0.6);
	}
};
