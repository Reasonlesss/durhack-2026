<!-- A 30-second first run: tap here → drag from its ring → tap the heartbeat.
     Shows once, ever (remembered in localStorage), and can be skipped. -->
<script lang="ts">
	import { untrack } from 'svelte';

	let { notes, lines, playing, width, height }: { notes: number; lines: number; playing: boolean; width: number; height: number } = $props();

	const KEY = 'shape-sequencer:onboarded';
	let step = $state(localStorage.getItem(KEY) ? 3 : 0);
	// Counts when each step began, so we react to the user's own action.
	let base = untrack(() => ({ notes, lines, playing }));

	$effect(() => {
		if (step === 0 && notes > base.notes) next();
		else if (step === 1 && lines > base.lines) next();
		else if (step === 2 && playing !== base.playing) next();
	});

	function next() {
		base = { notes, lines, playing };
		step++;
		if (step >= 3) localStorage.setItem(KEY, '1');
	}
	function skip() {
		step = 3;
		localStorage.setItem(KEY, '1');
	}

	const TEXT = ['tap here', 'now drag from its ring to somewhere empty', 'now tap the heartbeat'];
</script>

{#if step < 3}
	{#if step === 0}
		<div class="target" style="left: {width * 0.55}px; top: {height * 0.5}px" aria-hidden="true"></div>
	{/if}
	<div class="coach" data-chrome role="status" style={step === 2 ? 'top: 70px; right: 90px' : `left: ${width * 0.55 - 120}px; top: ${height * 0.5 + 44}px`}>
		<span>{TEXT[step]}</span>
		<button onclick={skip}>skip</button>
	</div>
{/if}

<style>
	.target {
		position: absolute;
		width: 46px;
		height: 46px;
		translate: -50% -50%;
		border-radius: 50%;
		border: 1.5px solid var(--accent);
		pointer-events: none;
		animation: breathe 1.6s ease-in-out infinite;
	}
	@keyframes breathe {
		50% {
			scale: 1.35;
			opacity: 0.4;
		}
	}
	.coach {
		position: absolute;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 4px 4px 4px 12px;
		border-radius: 10px;
		background: rgb(21 23 28 / 0.92);
		border: 1px solid var(--accent);
		font: 12px var(--mono);
		color: var(--text);
	}
	button {
		all: unset;
		min-height: 36px;
		min-width: 44px;
		text-align: center;
		color: var(--muted);
		cursor: pointer;
		border-radius: 6px;
	}
	button:hover {
		color: var(--text);
	}
	button:focus-visible {
		outline: 2px solid var(--accent);
	}
</style>
