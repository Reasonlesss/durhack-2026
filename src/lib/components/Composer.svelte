<!-- The arrangement grid: rows are parts, columns are sections, cell width follows bars.
     Owns pointer painting: press on a cell to flip it, keep dragging to paint that value. -->
<script lang="ts">
	import Icon from './Icon.svelte';
	import LoopBracket from './LoopBracket.svelte';
	import PartRow from './PartRow.svelte';
	import Playhead from './Playhead.svelte';
	import SectionHeader from './SectionHeader.svelte';
	import { uid } from '#lib/ids.ts';
	import { songStore } from '#lib/song.svelte.ts';
	import { transport } from '#lib/transport.svelte.ts';
	import { cellKey } from '#lib/types.ts';

	const sections = $derived(songStore.song.sections);
	const parts = $derived(songStore.song.parts);
	const totalBars = $derived(sections.reduce((n, s) => n + s.bars, 0));
	const columns = $derived(`var(--rail) ${sections.map((s) => `minmax(0, ${s.bars}fr)`).join(' ')} var(--add)`);
	// Explicit rows so overlays can span the whole grid with `grid-row: 1 / -1`.
	const rows = $derived(`40px auto repeat(${parts.length}, auto) auto`);
	const span = $derived(`2 / span ${sections.length}`);
	const current = $derived(transport.isPlaying ? transport.position.sectionIndex : -1);

	let stroke: { value: boolean; id: string; done: Set<string> } | null = null;

	function cellFrom(el: Element | null) {
		const b = el?.closest<HTMLElement>('[data-cell]');
		return b ? { partId: b.dataset.part!, sectionId: b.dataset.section! } : null;
	}

	function paint(c: { partId: string; sectionId: string }) {
		const k = cellKey(c.partId, c.sectionId);
		if (!stroke || stroke.done.has(k)) return;
		stroke.done.add(k);
		if (songStore.isOn(c.partId, c.sectionId) !== stroke.value) songStore.paintCells([c], stroke.value, stroke.id);
	}

	function down(e: PointerEvent) {
		const c = cellFrom(e.target as Element);
		if (!c || e.button !== 0) return;
		stroke = { value: !songStore.isOn(c.partId, c.sectionId), id: uid('stroke_'), done: new Set() };
		paint(c);
	}
	// Uses elementFromPoint so painting also works with touch (where the pointer is captured).
	function move(e: PointerEvent) {
		if (!stroke) return;
		const c = cellFrom(document.elementFromPoint(e.clientX, e.clientY));
		if (c) paint(c);
	}
</script>

<svelte:window onpointerup={() => (stroke = null)} onpointercancel={() => (stroke = null)} />

<div class="scroller">
	<div
		class="composer"
		role="presentation"
		style="grid-template-columns: {columns}; grid-template-rows: {rows}; min-width: calc(var(--rail) + var(--add) + {totalBars * 40}px)"
		onpointerdown={down}
		onpointermove={move}
	>
		{#if current >= 0}
			<div class="now" style="grid-column: {current + 2}" aria-hidden="true"></div>
		{/if}

		<div class="corner" style="grid-row: 1 / 3; grid-column: 1">
			<span>parts</span>
		</div>
		<div style="grid-row: 1; grid-column: {span}"><LoopBracket /></div>

		<div class="headrow">
			{#each sections as section, i (section.id)}
				<SectionHeader {section} index={i} current={i === current} />
			{/each}
			<button class="icon add" style="grid-column: -2" aria-label="Add a section" title="Add a section" onclick={() => songStore.addSection()}>
				<Icon name="plus" />
			</button>
		</div>

		{#each parts as part, i (part.id)}
			<PartRow {part} index={i} firstCell={i === 0} />
		{/each}

		<div class="addrow" style="grid-row: {parts.length + 3}">
			<button class="addpart" onclick={() => songStore.addPart()}><Icon name="plus" size={16} /> part</button>
		</div>

		<div class="playhead" style="grid-column: {span}; grid-row: 1 / {parts.length + 3}"><Playhead /></div>
	</div>
</div>

<style>
	.scroller {
		flex: 1;
		overflow: auto;
	}
	.composer {
		--rail: 300px;
		--add: 56px;
		display: grid;
		position: relative;
		user-select: none;
	}
	.now {
		grid-row: 1 / -1;
		background: color-mix(in srgb, var(--accent) 6%, transparent);
		pointer-events: none;
		transition: grid-column var(--t);
	}
	.corner {
		position: sticky;
		left: 0;
		z-index: 4;
		display: flex;
		align-items: flex-end;
		padding: 0 12px 12px;
		background: var(--ground);
		border-right: 1px solid var(--line);
		border-bottom: 1px solid var(--line);
		font-family: var(--mono);
		font-size: 11px;
		color: var(--muted);
	}
	.headrow {
		display: grid;
		grid-template-columns: subgrid;
		grid-column: 2 / -1;
		grid-row: 2;
		border-bottom: 1px solid var(--line);
	}
	.add {
		align-self: center;
		justify-self: center;
	}
	.addrow {
		grid-column: 1;
		position: sticky;
		left: 0;
		padding: 8px;
	}
	.addpart {
		all: unset;
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 44px;
		padding: 0 14px;
		border-radius: var(--r-md);
		border: 1px dashed var(--line-strong);
		color: var(--muted);
		font-family: var(--mono);
		font-size: 13px;
		cursor: pointer;
		transition:
			color var(--t),
			border-color var(--t);
	}
	.addpart:hover {
		color: var(--text);
		border-color: var(--muted);
	}
	.addpart:focus-visible {
		outline: 2px solid var(--accent);
	}
	.playhead {
		display: grid;
		pointer-events: none;
	}
</style>
