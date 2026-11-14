<!-- Tempo + play. Tap = play/stop, drag up/down = faster/slower, scroll = nudge.
     Breathes slowly when stopped, pulses on every beat when playing, and shows an invite
     ring until the very first play. With a partId it plays that part; otherwise the song. -->
<script lang="ts">
	import { paceWord } from '#lib/music.ts';
	import { songStore } from '#lib/song.svelte.ts';
	import { transport } from '#lib/transport.svelte.ts';
	import { onMount } from 'svelte';

	let { partId }: { partId?: string } = $props();

	const bpm = $derived(songStore.song.bpm);
	const word = $derived(paceWord(bpm));
	const playingHere = $derived(transport.isPlaying && (partId ? transport.mode.kind === 'part' && transport.mode.partId === partId : transport.mode.kind === 'song'));

	let btn: HTMLButtonElement;
	let start: { y: number; bpm: number } | null = null;
	let dragged = false;
	let showWord = $state(false);
	let wordTimer: ReturnType<typeof setTimeout> | undefined;

	const toggle = () => (playingHere ? transport.stop() : transport.play(partId));
	function flashWord() {
		showWord = true;
		clearTimeout(wordTimer);
		wordTimer = setTimeout(() => (showWord = false), 1600);
	}

	function down(e: PointerEvent) {
		start = { y: e.clientY, bpm };
		dragged = false;
		btn.setPointerCapture(e.pointerId);
	}
	function move(e: PointerEvent) {
		if (!start) return;
		const dy = start.y - e.clientY;
		if (Math.abs(dy) > 4) dragged = true;
		if (dragged) {
			songStore.setBpm(start.bpm + dy * 0.6);
			flashWord();
		}
	}
	function up() {
		if (start && !dragged) toggle();
		start = null;
	}
	function key(e: KeyboardEvent) {
		const step = { ArrowUp: 2, ArrowDown: -2 }[e.key];
		if (!step) return;
		e.preventDefault();
		songStore.setBpm(bpm + step);
		flashWord();
	}

	onMount(() => {
		// Non-passive so scrolling here doesn't scroll anything else.
		const wheel = (e: WheelEvent) => {
			e.preventDefault();
			songStore.setBpm(bpm + (e.deltaY < 0 ? 2 : -2));
			flashWord();
		};
		btn.addEventListener('wheel', wheel, { passive: false });
		return () => btn.removeEventListener('wheel', wheel);
	});
</script>

<div class="beat">
	<span class="word" class:show={showWord} aria-hidden="true">pace · {word}</span>
	<button
		bind:this={btn}
		aria-label="{playingHere ? 'Stop' : 'Play'}. Pace: {word}. Drag up or down to change the pace."
		onpointerdown={down}
		onpointermove={move}
		onpointerup={up}
		onpointerenter={flashWord}
		onkeydown={key}
		onclick={(e) => e.detail === 0 && toggle()}
	>
		{#if !transport.everPlayed}<span class="invite" aria-hidden="true"></span>{/if}
		{#key playingHere ? Math.floor(transport.songBeat) : -1}
			<span class="ring" class:live={playingHere} style="--period: {60 / bpm}s"><span class="core"></span></span>
		{/key}
	</button>
</div>

<style>
	.beat {
		position: relative;
		display: inline-flex;
		align-items: center;
	}
	button {
		all: unset;
		position: relative;
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
	.ring {
		width: 20px;
		height: 20px;
		border-radius: 50%;
		border: 1.5px solid var(--accent);
		display: grid;
		place-items: center;
		animation: breathe 4s ease-in-out infinite;
	}
	.core {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--accent);
	}
	.ring.live {
		background: var(--accent);
		animation: pulse var(--period) ease-out forwards;
	}
	@keyframes breathe {
		50% {
			scale: 0.82;
			opacity: 0.7;
		}
	}
	@keyframes pulse {
		from {
			scale: 1.3;
			box-shadow: 0 0 14px var(--accent);
		}
		to {
			scale: 1;
			box-shadow: 0 0 0 transparent;
		}
	}
	.invite {
		position: absolute;
		inset: 4px;
		border-radius: 50%;
		border: 1.5px solid var(--accent);
		animation: invite 1.8s ease-out infinite;
		pointer-events: none;
	}
	@keyframes invite {
		from {
			scale: 0.8;
			opacity: 0.9;
		}
		to {
			scale: 1.6;
			opacity: 0;
		}
	}
	.word {
		position: absolute;
		right: 100%;
		margin-right: 6px;
		white-space: nowrap;
		font: 12px var(--mono);
		color: var(--muted);
		opacity: 0;
		transition: opacity 180ms ease;
		pointer-events: none;
	}
	.word.show {
		opacity: 1;
	}
</style>
