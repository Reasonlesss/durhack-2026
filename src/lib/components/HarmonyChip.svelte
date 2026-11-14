<!-- Shows a harmony as "feel · chord ˅" and opens the pad. With a sectionId it edits that
     section's override and shows "inherits" when there is none. -->
<script lang="ts">
	import HarmonyPad from './HarmonyPad.svelte';
	import Popover from './Popover.svelte';
	import { songStore } from '#lib/song.svelte.ts';
	import { previewHarmony, transport } from '#lib/transport.svelte.ts';
	import { FEEL_COLOURS, type Harmony } from '#lib/types.ts';

	let { sectionId, align = 'left' }: { sectionId?: string; align?: 'left' | 'right' } = $props();

	const section = $derived(sectionId ? songStore.section(sectionId) : undefined);
	const own = $derived(sectionId ? section?.harmony : songStore.song.harmony);
	const shown = $derived(own ?? songStore.song.harmony);
	const label = $derived(sectionId ? `How ${section?.name ?? 'this section'} feels` : 'How the song feels');

	function change(h: Harmony) {
		songStore.setHarmony(h, sectionId);
		// While stopped, play the chord so the change can be heard.
		if (!transport.isPlaying) previewHarmony(h);
	}
</script>

<Popover id="harmony:{sectionId ?? 'song'}" {label} {align}>
	{#snippet trigger()}
		<span class="chip" class:inherits={!own}>
			<span class="dot" style="background: {FEEL_COLOURS[shown.feel]}"></span>
			{own ? `${own.feel} · ${own.chord}` : 'inherits'}
			<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M6 9l6 6 6-6" /></svg>
		</span>
	{/snippet}
	<HarmonyPad value={shown} onchange={change} />
	{#if sectionId}
		<button class="reset" disabled={!own} onclick={() => songStore.setHarmony(null, sectionId)}>use the song's feel</button>
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
		width: 9px;
		height: 9px;
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
		font: 12px var(--mono);
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
