<!-- Shows the song's feel as "feel · chord ˅" and opens the harmony pad. -->
<script lang="ts">
	import HarmonyPad from './HarmonyPad.svelte';
	import Popover from './Popover.svelte';
	import { songStore } from '#lib/song.svelte.ts';
	import { previewHarmony, transport } from '#lib/transport.svelte.ts';
	import { FEEL_COLOURS, type Harmony } from '#lib/types.ts';

	let { align = 'left' }: { align?: 'left' | 'right' } = $props();

	const value = $derived(songStore.song.harmony);

	function change(h: Harmony) {
		songStore.setHarmony(h);
		// While stopped, play the chord so the change can be heard.
		if (!transport.isPlaying) previewHarmony(h);
	}
</script>

<Popover id="harmony" label="How it feels" {align}>
	{#snippet trigger()}
		<span class="chip">
			<span class="dot" style="background: {FEEL_COLOURS[value.feel]}"></span>
			{value.feel} · {value.chord}
			<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M6 9l6 6 6-6" /></svg>
		</span>
	{/snippet}
	<HarmonyPad {value} onchange={change} />
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
	.dot {
		width: 9px;
		height: 9px;
		border-radius: 50%;
		flex: none;
	}
</style>
