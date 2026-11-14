<!-- Tempo control. Tap = play/stop, drag up/down = faster/slower, arrow keys nudge.
     The dot pulses once per beat. -->
<script lang="ts">
	import { songStore } from '#lib/song.svelte.ts';
	import { transport } from '#lib/transport.svelte.ts';

	const bpm = $derived(songStore.song.bpm);
	const word = $derived(bpm < 75 ? 'sleepy' : bpm < 100 ? 'easy' : bpm < 125 ? 'bouncy' : bpm < 150 ? 'hyped' : 'frantic');

	let start: { y: number; bpm: number } | null = null;
	let dragged = false;

	function down(e: PointerEvent) {
		start = { y: e.clientY, bpm };
		dragged = false;
		(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
	}
	function move(e: PointerEvent) {
		if (!start) return;
		const dy = start.y - e.clientY;
		if (Math.abs(dy) > 4) dragged = true;
		if (dragged) songStore.setBpm(start.bpm + dy / 2); // 2px per step
	}
	function up() {
		if (start && !dragged) transport.toggle();
		start = null;
	}
	function key(e: KeyboardEvent) {
		const step = { ArrowUp: 1, ArrowDown: -1 }[e.key];
		if (!step) return;
		e.preventDefault();
		songStore.setBpm(bpm + step * (e.shiftKey ? 10 : 1));
	}
</script>

<div class="beat">
	<button
		aria-label="Speed: {word}, {bpm} beats a minute. Tap to play or stop, drag up or down to change."
		title="{word} · {bpm} beats a minute"
		onpointerdown={down}
		onpointermove={move}
		onpointerup={up}
		onkeydown={key}
		onclick={(e) => e.detail === 0 && transport.toggle()}
	>
		<!-- Restart the pulse on every audible beat so it stays locked to the clock. -->
		{#key Math.floor(transport.songBeat)}
			<span class="dot" class:live={transport.isPlaying} style="--period: {60 / bpm}s"></span>
		{/key}
	</button>
	<span class="word">{word}</span>
</div>

<style>
	.beat {
		display: inline-flex;
		align-items: center;
		gap: 4px;
	}
	button {
		all: unset;
		box-sizing: border-box;
		width: 44px;
		height: 44px;
		display: grid;
		place-items: center;
		border-radius: 50%;
		cursor: ns-resize;
		touch-action: none;
	}
	button:hover {
		background: var(--hover);
	}
	button:focus-visible {
		outline: 2px solid var(--accent);
	}
	.dot {
		width: 14px;
		height: 14px;
		border-radius: 50%;
		background: var(--accent);
		opacity: 0.7;
	}
	.dot.live {
		animation: pulse var(--period) ease-out forwards;
	}
	@keyframes pulse {
		0% {
			transform: scale(1.5);
			opacity: 1;
		}
		100% {
			transform: scale(1);
			opacity: 0.6;
		}
	}
	.word {
		font-family: var(--mono);
		font-size: 12px;
		color: var(--muted);
		min-width: 4.5em;
	}
</style>
