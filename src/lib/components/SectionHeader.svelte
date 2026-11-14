<!-- Column header for one section: drag handle, inline name, menu, length stepper, feel chip. -->
<script lang="ts">
	import HarmonyChip from './HarmonyChip.svelte';
	import Icon from './Icon.svelte';
	import Menu from './Menu.svelte';
	import { songStore } from '#lib/song.svelte.ts';
	import { transport } from '#lib/transport.svelte.ts';
	import type { Section } from '#lib/types.ts';
	import { ui } from '#lib/ui.svelte.ts';
	import { tick } from 'svelte';

	let { section, index, current }: { section: Section; index: number; current: boolean } = $props();

	const DRAG_TYPE = 'text/x-section-index';
	let dropTarget = $state(false);
	const selected = $derived(ui.selection?.kind === 'section' && ui.selection.sectionId === section.id);
	const count = $derived(songStore.song.sections.length);

	async function nudge(e: KeyboardEvent) {
		const d = { ArrowLeft: -1, ArrowRight: 1 }[e.key];
		if (!d) return;
		e.preventDefault();
		e.stopPropagation();
		songStore.reorderSections(index, index + d);
		await tick();
		document.querySelector<HTMLElement>(`[data-section-handle="${section.id}"]`)?.focus();
	}
</script>

<div
	class="head"
	class:current
	class:selected
	class:drop={dropTarget}
	style="grid-column: {index + 2}"
	role="group"
	aria-label={section.name}
	onfocusin={(e) => {
		if (!(e.target as HTMLElement).closest('[role=dialog]')) ui.selection = { kind: 'section', sectionId: section.id };
	}}
	ondragover={(e) => {
		if (!e.dataTransfer?.types.includes(DRAG_TYPE)) return;
		e.preventDefault();
		dropTarget = true;
	}}
	ondragleave={() => (dropTarget = false)}
	ondrop={(e) => {
		dropTarget = false;
		const from = Number(e.dataTransfer?.getData(DRAG_TYPE));
		if (!Number.isNaN(from)) songStore.reorderSections(from, index);
	}}
>
	<div class="line">
		<button
			class="icon handle"
			draggable="true"
			data-section-handle={section.id}
			aria-label="Move {section.name} (drag, or use left and right arrows)"
			ondragstart={(e) => e.dataTransfer?.setData(DRAG_TYPE, String(index))}
			onkeydown={nudge}
		>
			<Icon name="grip" size={16} />
		</button>
		<input
			class="name"
			aria-label="Section name"
			value={section.name}
			oninput={(e) => songStore.renameSection(section.id, e.currentTarget.value)}
		/>
		<Menu
			id="section-menu:{section.id}"
			label="More for {section.name}"
			items={[
				{ label: 'Start playing here', run: () => (transport.seek(section.id), transport.play()) },
				{ label: 'Loop this section', run: () => songStore.setLoop({ from: section.id, to: section.id }) },
				{ label: 'Cut in half', disabled: section.bars < 2, run: () => songStore.splitSection(section.id) },
				{ label: 'Add section after', run: () => songStore.addSection(section.id) },
				{ label: 'Delete', danger: true, disabled: count <= 1, run: () => songStore.deleteSection(section.id) }
			]}
		/>
	</div>
	<div class="line">
		<div class="stepper" role="group" aria-label="Length">
			<button class="icon" aria-label="Shorter" disabled={section.bars <= 1} onclick={() => songStore.setSectionBars(section.id, section.bars - 1)}>
				<Icon name="minus" size={14} />
			</button>
			<!-- Length as little ticks rather than a number. -->
			<span class="bars" aria-hidden="true">{#each Array(Math.min(section.bars, 16)) as _}<i></i>{/each}</span>
			<button class="icon" aria-label="Longer" onclick={() => songStore.setSectionBars(section.id, section.bars + 1)}>
				<Icon name="plus" size={14} />
			</button>
		</div>
		<HarmonyChip sectionId={section.id} />
	</div>
</div>

<style>
	.head {
		min-width: 0;
		padding: 4px;
		border-right: 1px solid var(--line);
		border-top: 2px solid transparent;
		transition:
			border-color var(--t),
			background var(--t);
	}
	.head.current {
		border-top-color: var(--accent);
	}
	.head.selected {
		background: var(--hover);
	}
	.head.drop {
		background: var(--hover);
		border-top-color: var(--muted);
	}
	.line {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 2px;
		min-width: 0;
	}
	.handle {
		cursor: grab;
		width: 28px;
		color: var(--muted);
	}
	.name {
		flex: 1;
		min-width: 3em;
		min-height: 44px;
		font: 600 14px var(--sans);
		color: var(--text);
		background: none;
		border: 1px solid transparent;
		border-radius: var(--r-sm);
		padding: 0 6px;
		text-overflow: ellipsis;
	}
	.name:hover {
		border-color: var(--line);
	}
	.name:focus-visible {
		outline: 2px solid var(--accent);
	}
	.stepper {
		display: flex;
		align-items: center;
	}
	.stepper .icon {
		width: 32px;
	}
	.bars {
		display: flex;
		gap: 2px;
		align-items: center;
		min-width: 1.5em;
	}
	.bars i {
		width: 3px;
		height: 10px;
		border-radius: 1px;
		background: var(--muted);
		opacity: 0.6;
	}
</style>
