<!-- The loop region, drawn above the section headers. Drag on the track to draw a loop,
     drag the bracket to slide it, drag its ends to resize. Everything snaps to sections. -->
<script lang="ts">
	import Icon from './Icon.svelte';
	import { songStore } from '#lib/song.svelte.ts';
	import { BEATS_PER_BAR } from '#lib/types.ts';

	const sections = $derived(songStore.song.sections);
	const { starts, totalBeats } = $derived(songStore.timeline);
	const loop = $derived(songStore.song.loop);
	const from = $derived(loop ? sections.findIndex((s) => s.id === loop.from) : -1);
	const to = $derived(loop ? sections.findIndex((s) => s.id === loop.to) : -1);
	const pct = (beat: number) => (beat / Math.max(1, totalBeats)) * 100;

	let track: HTMLElement;
	let drag: { mode: 'draw' | 'move' | 'start' | 'end'; anchor: number; span: number } | null = null;

	/** Section index under the pointer. */
	function indexAt(e: PointerEvent) {
		const r = track.getBoundingClientRect();
		const beat = ((e.clientX - r.left) / r.width) * totalBeats;
		let i = 0;
		while (i + 1 < starts.length && starts[i + 1] <= beat) i++;
		return i;
	}

	function apply(a: number, b: number) {
		const [lo, hi] = [Math.min(a, b), Math.max(a, b)];
		const next = { from: sections[lo].id, to: sections[hi].id };
		if (next.from !== loop?.from || next.to !== loop?.to) songStore.setLoop(next);
	}

	function down(e: PointerEvent) {
		if ((e.target as HTMLElement).closest('button')) return;
		const i = indexAt(e);
		const part = (e.target as HTMLElement).dataset.part;
		if (loop && part === 'start') drag = { mode: 'start', anchor: to, span: 0 };
		else if (loop && part === 'end') drag = { mode: 'end', anchor: from, span: 0 };
		else if (loop && part === 'body') drag = { mode: 'move', anchor: i - from, span: to - from };
		else {
			drag = { mode: 'draw', anchor: i, span: 0 };
			apply(i, i);
		}
		track.setPointerCapture(e.pointerId);
	}

	function moveTo(e: PointerEvent) {
		if (!drag) return;
		const i = indexAt(e);
		if (drag.mode === 'move') {
			const start = Math.max(0, Math.min(sections.length - 1 - drag.span, i - drag.anchor));
			apply(start, start + drag.span);
		} else apply(drag.anchor, i);
	}
</script>

<div
	class="track"
	bind:this={track}
	role="presentation"
	title="Drag here to choose a part of the song to repeat"
	onpointerdown={down}
	onpointermove={moveTo}
	onpointerup={() => (drag = null)}
>
	{#if loop && from >= 0 && to >= 0}
		{@const left = pct(starts[from])}
		{@const width = pct(starts[to] + sections[to].bars * BEATS_PER_BAR) - left}
		<div class="bracket" data-part="body" style="left: {left}%; width: {width}%">
			<span class="end start" data-part="start"></span>
			<span class="label" data-part="body">repeat</span>
			<button class="clear" aria-label="Stop repeating, play the whole song" onclick={() => songStore.setLoop(null)}>
				<Icon name="close" size={12} />
			</button>
			<span class="end finish" data-part="end"></span>
		</div>
	{:else}
		<span class="hint">drag here to repeat a part of the song</span>
	{/if}
</div>

<style>
	.track {
		position: relative;
		height: 100%;
		cursor: crosshair;
		touch-action: none;
		border-bottom: 1px solid var(--line);
	}
	.hint {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		padding-left: 10px;
		font-family: var(--mono);
		font-size: 11px;
		color: var(--muted);
		pointer-events: none;
	}
	.bracket {
		position: absolute;
		top: 6px;
		bottom: 6px;
		display: flex;
		align-items: center;
		justify-content: center;
		border: 2px solid var(--accent);
		border-bottom: none;
		border-radius: var(--r-sm) var(--r-sm) 0 0;
		background: color-mix(in srgb, var(--accent) 12%, transparent);
		cursor: grab;
		transition:
			left var(--t),
			width var(--t);
	}
	.label {
		font-family: var(--mono);
		font-size: 11px;
		color: var(--accent);
	}
	.end {
		position: absolute;
		top: -2px;
		bottom: 0;
		width: 14px;
		cursor: ew-resize;
	}
	.start {
		left: -8px;
	}
	.finish {
		right: -8px;
	}
	.clear {
		all: unset;
		position: absolute;
		right: 12px;
		display: grid;
		place-items: center;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		color: var(--accent);
		cursor: pointer;
	}
	/* Bigger invisible hit area for touch. */
	.clear::before {
		content: '';
		position: absolute;
		inset: -11px;
	}
	.clear:hover {
		background: var(--hover);
	}
	.clear:focus-visible {
		outline: 2px solid var(--accent);
	}
</style>
