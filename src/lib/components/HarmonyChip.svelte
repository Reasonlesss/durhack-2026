<!-- Shows a harmony as "feel · chord" and opens the pad. With a sectionId it edits that
     section's override and shows "inherits" when there is none. -->
<script lang="ts">
	import HarmonyPad from './HarmonyPad.svelte';
	import Popover from './Popover.svelte';
	import { songStore } from '#lib/song.svelte.ts';
	import { FEEL_COLOURS } from '#lib/types.ts';

	let { sectionId }: { sectionId?: string } = $props();

	const section = $derived(sectionId ? songStore.section(sectionId) : undefined);
	const own = $derived(sectionId ? section?.harmony : songStore.song.harmony);
	const shown = $derived(own ?? songStore.song.harmony);
	const label = $derived(sectionId ? `Feel for ${section?.name ?? 'section'}` : 'Song feel');
</script>

<Popover id="harmony:{sectionId ?? 'song'}" {label} class="chip">
	{#snippet trigger()}
		<span class="chip" class:inherits={!own}>
			<span class="dot" style="background: {FEEL_COLOURS[shown.feel]}"></span>
			{own ? `${own.feel} · ${own.chord}` : 'inherits'}
		</span>
	{/snippet}
	<HarmonyPad value={shown} onchange={(h) => songStore.setHarmony(h, sectionId)} />
	{#if sectionId}
		<button class="reset" disabled={!own} onclick={() => songStore.setHarmony(null, sectionId)}>Use the song's feel</button>
	{/if}
</Popover>

<style>
	.chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 0 10px;
		font-family: var(--mono);
		font-size: 12px;
		white-space: nowrap;
	}
	.inherits {
		color: var(--muted);
	}
	.dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		flex: none;
	}
	.inherits .dot {
		opacity: 0.5;
	}
	.reset {
		all: unset;
		box-sizing: border-box;
		margin-top: 10px;
		width: 100%;
		min-height: 44px;
		text-align: center;
		border-radius: var(--r-sm);
		border: 1px solid var(--line);
		cursor: pointer;
		font-size: 13px;
	}
	.reset:hover:not(:disabled) {
		background: var(--hover);
	}
	.reset:focus-visible {
		outline: 2px solid var(--accent);
	}
	.reset:disabled {
		color: var(--muted);
		cursor: default;
	}
</style>
