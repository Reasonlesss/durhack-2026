<!-- "How it feels": 5 feels (darker → brighter) × 4 chords (home at the bottom).
     One slider-like control: drag the glowing dot (it snaps to cells) or use arrow keys. -->
<script lang="ts">
	import { CHORDS, FEELS, FEEL_COLOURS, type Harmony } from '#lib/types.ts';

	let { value, onchange }: { value: Harmony; onchange: (h: Harmony) => void } = $props();

	const rows = [...CHORDS].reverse(); // reach on top, home at the bottom
	const col = $derived(FEELS.indexOf(value.feel));
	const row = $derived(rows.indexOf(value.chord));

	let grid: HTMLElement;
	let dragging = false;

	function set(c: number, r: number) {
		c = Math.max(0, Math.min(4, c));
		r = Math.max(0, Math.min(3, r));
		const next = { feel: FEELS[c], chord: rows[r] };
		if (next.feel !== value.feel || next.chord !== value.chord) onchange(next);
	}

	function fromPointer(e: PointerEvent) {
		const b = grid.getBoundingClientRect();
		set(Math.floor(((e.clientX - b.left) / b.width) * 5), Math.floor(((e.clientY - b.top) / b.height) * 4));
	}

	function keydown(e: KeyboardEvent) {
		const d = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[e.key];
		if (!d) return;
		e.preventDefault();
		e.stopPropagation();
		set(col + d[0], row + d[1]);
	}
</script>

<div class="pad">
	<header>
		<span class="title">how it feels</span>
		<span class="now" style="color: {FEEL_COLOURS[value.feel]}">{value.feel} · {value.chord}</span>
	</header>
	<div class="body">
		<div class="rowlabels" aria-hidden="true">
			{#each rows as c}<span>{c}</span>{/each}
		</div>
		<div
			class="grid"
			role="slider"
			tabindex="0"
			aria-label="How it feels. Left and right: darker or brighter. Up and down: change the chord."
			aria-valuemin={0}
			aria-valuemax={19}
			aria-valuenow={col * 4 + (3 - row)}
			aria-valuetext="{value.feel}, {value.chord}"
			bind:this={grid}
			onkeydown={keydown}
			onpointerdown={(e) => {
				dragging = true;
				grid.setPointerCapture(e.pointerId);
				fromPointer(e);
			}}
			onpointermove={(e) => dragging && fromPointer(e)}
			onpointerup={() => (dragging = false)}
		>
			{#each rows as chord, r}
				{#each FEELS as feel, c}
					{@const on = feel === value.feel && chord === value.chord}
					<span class="cell" class:on style="grid-column: {c + 1}; grid-row: {r + 1}; --c: {FEEL_COLOURS[feel]}"></span>
				{/each}
			{/each}
			<span
				class="dot"
				aria-hidden="true"
				style="left: {(col + 0.5) * 20}%; top: {(row + 0.5) * 25}%; --c: {FEEL_COLOURS[value.feel]}"
			></span>
		</div>
		<span></span>
		<div class="collabels" aria-hidden="true">
			{#each FEELS as f}<span style="color: {FEEL_COLOURS[f]}">{f}</span>{/each}
		</div>
	</div>
	<p class="caption">darker ← → brighter · up and down change the chord</p>
</div>

<style>
	.pad {
		width: 318px;
		font-family: var(--mono);
		font-size: 11px;
		color: var(--muted);
	}
	header {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		margin-bottom: 10px;
	}
	.title {
		text-transform: uppercase;
		letter-spacing: 0.08em;
		color: var(--text);
	}
	.now {
		font-size: 12px;
	}
	.body {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: 6px 8px;
	}
	.rowlabels {
		display: grid;
		grid-template-rows: repeat(4, 1fr);
		align-items: center;
		text-align: right;
	}
	.grid {
		position: relative;
		display: grid;
		grid-template-columns: repeat(5, 1fr);
		grid-template-rows: repeat(4, 44px);
		gap: 3px;
		touch-action: none;
		cursor: pointer;
		border-radius: 10px;
	}
	.grid:focus-visible {
		outline: 2px solid var(--text);
		outline-offset: 3px;
	}
	.cell {
		border-radius: 6px;
		background: color-mix(in srgb, var(--c) 12%, transparent);
		transition: background 180ms ease;
	}
	.cell.on {
		background: color-mix(in srgb, var(--c) 45%, transparent);
	}
	.dot {
		position: absolute;
		width: 14px;
		height: 14px;
		margin: -7px 0 0 -7px;
		border-radius: 50%;
		background: var(--c);
		box-shadow: 0 0 14px 3px var(--c);
		pointer-events: none;
		transition: left 160ms ease, top 160ms ease, background 160ms ease, box-shadow 160ms ease;
	}
	.collabels {
		display: grid;
		grid-template-columns: repeat(5, 1fr);
		text-align: center;
	}
	.caption {
		margin: 10px 0 0;
		text-align: center;
	}
</style>
