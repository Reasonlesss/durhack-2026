<!-- Top-right cluster on the canvas: feel, heartbeat, groove looseness, start fresh. -->
<script lang="ts">
	import HarmonyChip from '../components/HarmonyChip.svelte';
	import Heartbeat from '../components/Heartbeat.svelte';
	import { songStore } from '../song.svelte';

	let { partId }: { partId: string } = $props();

	const SWINGS = ['tight', 'loose', 'lazy'] as const;
	const swing = $derived(songStore.song.swing ?? 'tight');

	let undoUntil = $state(0);
	let timer: ReturnType<typeof setTimeout> | undefined;
	function fresh() {
		if (Date.now() < undoUntil) {
			songStore.undo();
			undoUntil = 0;
			return;
		}
		songStore.clearCanvas(partId);
		undoUntil = Date.now() + 6000;
		clearTimeout(timer);
		timer = setTimeout(() => (undoUntil = 0), 6000);
	}
</script>

<div class="hud">
	<HarmonyChip align="right" />
	<Heartbeat {partId} />
	<button
		class="word"
		aria-label="Groove: {swing}. Press to change."
		title="how the groove sits: tight, loose or lazy"
		onclick={() => songStore.setSwing(SWINGS[(SWINGS.indexOf(swing) + 1) % 3])}
	>
		groove · {swing}
	</button>
	<button class="word" onclick={fresh} aria-label={undoUntil ? 'Bring it back' : 'Start fresh: clear this canvas'}>
		<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" aria-hidden="true">
			{#if undoUntil}<path d="M9 14L4 9l5-5" /><path d="M4 9h11a5 5 0 010 10h-3" />
			{:else}<path d="M4 7h16" /><path d="M9 7V4h6v3" /><path d="M6 7l1 13h10l1-13" />{/if}
		</svg>
		{undoUntil ? 'bring it back' : 'start fresh'}
	</button>
</div>

<style>
	.hud {
		display: flex;
		align-items: center;
		gap: 6px;
	}
	.word {
		all: unset;
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 44px;
		padding: 0 10px;
		border-radius: 8px;
		font: 12px var(--mono);
		color: var(--muted);
		cursor: pointer;
		transition: color 180ms ease, background 180ms ease;
	}
	.word:hover {
		color: var(--text);
		background: var(--hover);
	}
	.word:focus-visible {
		outline: 2px solid var(--accent);
	}
</style>
