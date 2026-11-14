<!-- Full-screen editor for one part. The note canvas is a placeholder for the note system. -->
<script lang="ts">
	import Icon from './Icon.svelte';
	import { songStore } from '#lib/song.svelte.ts';
	import { transport } from '#lib/transport.svelte.ts';
	import { PART_COLOURS, type Part } from '#lib/types.ts';

	let { part }: { part: Part } = $props();

	const sections = $derived(songStore.song.sections);
	const { starts, totalBeats } = $derived(songStore.timeline);
</script>

<div class="view">
	<header>
		<a class="back" href="/"><Icon name="back" /> song</a>
		<input
			class="name"
			aria-label="Part name"
			value={part.name}
			oninput={(e) => songStore.renamePart(part.id, e.currentTarget.value)}
		/>
		<div class="swatches" role="radiogroup" aria-label="Colour">
			{#each PART_COLOURS as c}
				<button
					role="radio"
					aria-checked={part.colour === c}
					aria-label="Colour {c}"
					style="--c: {c}"
					onclick={() => songStore.setPartColour(part.id, c)}
				></button>
			{/each}
		</div>
		<div class="stepper" role="group" aria-label="Pattern length">
			<button class="icon" aria-label="Shorter pattern" disabled={part.bars <= 1} onclick={() => songStore.setPartBars(part.id, part.bars - 1)}>
				<Icon name="minus" size={14} />
			</button>
			<span>{part.bars}<small> {part.bars === 1 ? 'bar' : 'bars'}</small></span>
			<button class="icon" aria-label="Longer pattern" onclick={() => songStore.setPartBars(part.id, part.bars + 1)}>
				<Icon name="plus" size={14} />
			</button>
		</div>
		<button class="icon play" aria-label={transport.isPlaying ? 'Stop' : 'Play'} onclick={transport.toggle}>
			<Icon name={transport.isPlaying ? 'stop' : 'play'} />
		</button>
	</header>

	<!-- Where this part plays across the song. Click a slot to switch it. -->
	<div class="strip" role="group" aria-label="Where {part.name} plays">
		{#each sections as s, i (s.id)}
			{@const on = songStore.isOn(part.id, s.id)}
			<button
				class:on
				style="flex: {s.bars}; --c: {part.colour}"
				aria-pressed={on}
				aria-label="{part.name} plays in {s.name}"
				onclick={() => songStore.toggleCell(part.id, s.id)}
			>
				<span>{s.name}</span>
			</button>
		{/each}
		<span
			class="head"
			aria-hidden="true"
			style="left: {(transport.songBeat / Math.max(1, totalBeats)) * 100}%"
			class:hidden={!transport.isPlaying}
		></span>
	</div>

	<!-- ===================================================================
	     NOTE SYSTEM PLACEHOLDER
	     The shape/note canvas mounts here and reads/writes `part.canvas`
	     (typed `null` in types.ts until the note model exists). It should
	     use part.bars for its loop length and subscribe to transport.hooks
	     for timing.
	     =================================================================== -->
	<section class="canvas" style="--c: {part.colour}" aria-label="Note canvas">
		<p>note canvas goes here</p>
		<small>{part.bars} {part.bars === 1 ? 'bar' : 'bars'} · loops inside each section it plays in · {starts.length} sections</small>
	</section>
</div>

<style>
	.view {
		display: flex;
		flex-direction: column;
		height: 100%;
	}
	header {
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 6px 14px;
		background: var(--panel);
		border-bottom: 1px solid var(--line);
	}
	.back {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 44px;
		padding: 0 12px;
		border-radius: var(--r-sm);
		color: var(--text);
		text-decoration: none;
		font-family: var(--mono);
		font-size: 13px;
	}
	.back:hover {
		background: var(--hover);
	}
	.back:focus-visible {
		outline: 2px solid var(--accent);
	}
	.name {
		font: 600 16px var(--sans);
		color: var(--text);
		background: none;
		border: 1px solid transparent;
		border-radius: var(--r-sm);
		padding: 0 10px;
		min-height: 44px;
		width: 12em;
	}
	.name:hover {
		border-color: var(--line);
	}
	.name:focus-visible {
		outline: 2px solid var(--accent);
	}
	.swatches {
		display: flex;
	}
	.swatches button {
		all: unset;
		width: 44px;
		height: 44px;
		display: grid;
		place-items: center;
		cursor: pointer;
		border-radius: var(--r-sm);
	}
	.swatches button::after {
		content: '';
		width: 18px;
		height: 18px;
		border-radius: 50%;
		background: var(--c);
		transition: box-shadow var(--t);
	}
	.swatches button[aria-checked='true']::after {
		box-shadow:
			0 0 0 3px var(--panel),
			0 0 0 5px var(--c);
	}
	.swatches button:focus-visible {
		outline: 2px solid var(--text);
	}
	.stepper {
		display: flex;
		align-items: center;
		font-family: var(--mono);
		font-size: 13px;
	}
	.stepper small {
		color: var(--muted);
	}
	.play {
		margin-left: auto;
		color: var(--ground);
		background: var(--accent);
	}
	.play:hover {
		background: var(--accent);
		filter: brightness(1.1);
	}
	.strip {
		position: relative;
		display: flex;
		gap: 4px;
		padding: 10px 14px;
		border-bottom: 1px solid var(--line);
	}
	.strip button {
		all: unset;
		box-sizing: border-box;
		min-width: 0;
		min-height: 44px;
		padding: 0 8px;
		display: flex;
		align-items: center;
		border-radius: var(--r-sm);
		border: 1px solid var(--line);
		font-family: var(--mono);
		font-size: 11px;
		color: var(--muted);
		cursor: pointer;
		transition: background var(--t);
	}
	.strip button span {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.strip button.on {
		background: color-mix(in srgb, var(--c) 30%, transparent);
		border-color: var(--c);
		color: var(--text);
	}
	.strip button:focus-visible {
		outline: 2px solid var(--text);
	}
	.head {
		position: absolute;
		top: 6px;
		bottom: 6px;
		width: 2px;
		background: var(--accent);
		pointer-events: none;
		margin-left: 14px;
		translate: -1px 0;
	}
	.head.hidden {
		display: none;
	}
	.canvas {
		flex: 1;
		margin: 14px;
		border-radius: var(--r-lg);
		border: 1px dashed color-mix(in srgb, var(--c) 45%, transparent);
		display: grid;
		place-content: center;
		text-align: center;
		gap: 6px;
	}
	.canvas p {
		margin: 0;
		font-family: var(--mono);
		font-size: 15px;
	}
	.canvas small {
		color: var(--muted);
		font-size: 13px;
	}
</style>
