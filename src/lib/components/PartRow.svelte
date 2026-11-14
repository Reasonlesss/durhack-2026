<!-- One part: the left rail (handle, swatch, name, silence/only, preview, menu) plus its cells. -->
<script lang="ts">
	import Cell from './Cell.svelte';
	import Icon from './Icon.svelte';
	import Menu from './Menu.svelte';
	import { songStore } from '#lib/song.svelte.ts';
	import type { Part } from '#lib/types.ts';
	import { ui } from '#lib/ui.svelte.ts';
	import { tick } from 'svelte';

	let { part, index, firstCell }: { part: Part; index: number; firstCell: boolean } = $props();

	const DRAG_TYPE = 'text/x-part-index';
	const thumbBox = $derived.by(() => {
		const ns = part.canvas.notes;
		if (!ns.length) return '0 0 1440 900';
		const xs = ns.map((n) => n.x);
		const ys = ns.map((n) => n.y);
		const [x0, y0] = [Math.min(...xs) - 60, Math.min(...ys) - 60];
		return `${x0} ${y0} ${Math.max(...xs) - x0 + 60} ${Math.max(...ys) - y0 + 60}`;
	});
	let dropTarget = $state(false);
	const selected = $derived(ui.selection?.kind === 'part' && ui.selection.partId === part.id);

	/** Which cell is in the tab order: the selected one, else the very first cell of the grid. */
	function tabbable(sectionId: string, i: number) {
		const s = ui.selection;
		if (s?.kind === 'cell') return s.partId === part.id && s.sectionId === sectionId;
		return firstCell && i === 0;
	}

	async function nudge(e: KeyboardEvent) {
		const d = { ArrowUp: -1, ArrowDown: 1 }[e.key];
		if (!d) return;
		e.preventDefault();
		e.stopPropagation();
		songStore.reorderParts(index, index + d);
		await tick();
		document.querySelector<HTMLElement>(`[data-part-handle="${part.id}"]`)?.focus();
	}
</script>

<div
	class="row"
	role="group"
	aria-label={part.name}
	class:drop={dropTarget}
	style="grid-row: {index + 3}"
	ondragover={(e) => {
		if (!e.dataTransfer?.types.includes(DRAG_TYPE)) return;
		e.preventDefault();
		dropTarget = true;
	}}
	ondragleave={() => (dropTarget = false)}
	ondrop={(e) => {
		dropTarget = false;
		const from = Number(e.dataTransfer?.getData(DRAG_TYPE));
		if (!Number.isNaN(from)) songStore.reorderParts(from, index);
	}}
>
	<div class="rail" class:selected class:muted={part.muted} role="presentation" onfocusin={(e) => {
		if (!(e.target as HTMLElement).closest('[role=dialog]')) ui.selection = { kind: 'part', partId: part.id };
	}}>
		<button
			class="icon handle"
			draggable="true"
			data-part-handle={part.id}
			aria-label="Move {part.name} (drag, or use up and down arrows)"
			ondragstart={(e) => e.dataTransfer?.setData(DRAG_TYPE, String(index))}
			onkeydown={nudge}
		>
			<Icon name="grip" size={16} />
		</button>
		<span class="swatch" style="background: {part.colour}" aria-hidden="true"></span>
		<a class="name" href="/part/{part.id}" title="Open {part.name}">{part.name}</a>
		<!-- Mini picture of the part's shapes. -->
		<svg class="thumb" viewBox={thumbBox} aria-hidden="true">
			{#each part.canvas.notes as n (n.id)}<circle cx={n.x} cy={n.y} r={Math.max(14, n.size)} fill={part.colour} opacity={n.inst === 'pause' ? 0.3 : 0.9} />{/each}
		</svg>
		<button class="icon" aria-label="Silence {part.name}" aria-pressed={part.muted} title="Silence" onclick={() => songStore.toggleMute(part.id)}>
			<Icon name={part.muted ? 'mute' : 'sound'} size={16} />
		</button>
		<button class="icon" aria-label="Play only {part.name}" aria-pressed={part.solo} title="Play only this" onclick={() => songStore.toggleSolo(part.id)}>
			<Icon name="solo" size={16} />
		</button>
		<Menu
			id="part-menu:{part.id}"
			label="More for {part.name}"
			items={[
				{ label: 'Duplicate', run: () => songStore.duplicatePart(part.id) },
				{ label: 'Delete', danger: true, run: () => songStore.deletePart(part.id) }
			]}
		/>
	</div>
	{#each songStore.song.sections as section, i (section.id)}
		<Cell {part} {section} tabbable={tabbable(section.id, i)} />
	{/each}
</div>

<style>
	.row {
		display: grid;
		grid-template-columns: subgrid;
		grid-column: 1 / -1;
		border-bottom: 1px solid var(--line);
		transition: background var(--t);
	}
	.row.drop {
		background: var(--hover);
	}
	.rail {
		display: flex;
		align-items: center;
		gap: 2px;
		padding: 0 6px 0 2px;
		border-right: 1px solid var(--line);
		background: var(--panel);
		position: sticky;
		left: 0;
		z-index: 3;
	}
	.rail.selected {
		box-shadow: inset 3px 0 0 var(--accent);
	}
	.handle {
		cursor: grab;
		width: 28px;
		color: var(--muted);
	}
	.swatch {
		width: 10px;
		height: 10px;
		border-radius: 3px;
		flex: none;
	}
	.name {
		flex: 1;
		min-width: 0;
		min-height: 44px;
		display: flex;
		align-items: center;
		padding: 0 8px;
		color: var(--text);
		text-decoration: none;
		font-size: 14px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		border-radius: var(--r-sm);
	}
	.name:hover {
		background: var(--hover);
	}
	.name:focus-visible {
		outline: 2px solid var(--accent);
	}
	.muted .name,
	.muted .swatch {
		opacity: 0.45;
	}
	.thumb {
		width: 34px;
		height: 22px;
		flex: none;
		border-radius: 5px;
		border: 1px solid var(--line);
		margin-right: 4px;
	}
	.icon[aria-pressed='true'] {
		color: var(--accent);
		background: var(--hover);
	}
</style>
