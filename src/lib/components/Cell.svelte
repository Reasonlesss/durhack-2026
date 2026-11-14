<!-- One on/off switch: does this part play in this section? Pointer painting is handled by
     Composer via the data-* attributes; this component handles keyboard use. -->
<script lang="ts">
	import { songStore } from '#lib/song.svelte.ts';
	import type { Part, Section } from '#lib/types.ts';
	import { ui } from '#lib/ui.svelte.ts';

	let { part, section, tabbable }: { part: Part; section: Section; tabbable: boolean } = $props();

	const on = $derived(songStore.isOn(part.id, section.id));

	/** Arrow keys walk the grid; moves focus to the neighbouring cell. */
	function keydown(e: KeyboardEvent) {
		const d = { ArrowLeft: [0, -1], ArrowRight: [0, 1], ArrowUp: [-1, 0], ArrowDown: [1, 0] }[e.key];
		if (!d) return;
		e.preventDefault();
		const { parts, sections } = songStore.song;
		const p = parts[parts.findIndex((x) => x.id === part.id) + d[0]];
		const s = sections[sections.findIndex((x) => x.id === section.id) + d[1]];
		if (!p || !s) return;
		document.querySelector<HTMLElement>(`[data-cell][data-part="${p.id}"][data-section="${s.id}"]`)?.focus();
	}
</script>

<button
	class="cell"
	class:on
	data-cell
	data-part={part.id}
	data-section={section.id}
	style="--c: {part.colour}"
	aria-pressed={on}
	aria-label="{part.name} plays in {section.name}"
	tabindex={tabbable ? 0 : -1}
	onfocus={() => (ui.selection = { kind: 'cell', partId: part.id, sectionId: section.id })}
	onkeydown={keydown}
	onclick={(e) => e.detail === 0 && songStore.toggleCell(part.id, section.id)}
></button>

<style>
	.cell {
		all: unset;
		box-sizing: border-box;
		display: block;
		margin: 6px 3px;
		min-height: 44px;
		border-radius: var(--r-md);
		background: rgb(236 231 221 / 0.04);
		border: 1px solid rgb(236 231 221 / 0.06);
		cursor: pointer;
		transition:
			background var(--t),
			border-color var(--t);
	}
	.cell:hover {
		border-color: var(--line-strong);
	}
	.cell.on {
		background: var(--c);
		border-color: var(--c);
	}
	.cell.on:hover {
		filter: brightness(1.08);
	}
	.cell:focus-visible {
		outline: 2px solid var(--text);
		outline-offset: 2px;
	}
</style>
