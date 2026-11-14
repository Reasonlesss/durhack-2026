<!-- 5×4 pad: columns are feels (darker → brighter), rows are chords (home at the bottom).
     A dot snaps to the chosen cell; drag it, click a cell, or use the arrow keys. -->
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

	/** Snap a pointer position to the nearest cell. */
	function fromPointer(e: PointerEvent) {
		const r = grid.getBoundingClientRect();
		set(Math.floor(((e.clientX - r.left) / r.width) * 5), Math.floor(((e.clientY - r.top) / r.height) * 4));
	}

	function keydown(e: KeyboardEvent) {
		const d = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[e.key];
		if (!d) return;
		e.preventDefault();
		e.stopPropagation();
		set(col + d[0], row + d[1]);
		// Keep focus on the newly chosen cell.
		queueMicrotask(() => grid.querySelector<HTMLElement>('[aria-checked="true"]')?.focus());
	}
</script>

<div class="pad">
	<div class="rowlabels" aria-hidden="true">
		{#each rows as c}<span>{c}</span>{/each}
	</div>
	<div>
		<div
			class="grid"
			role="radiogroup"
			aria-label="Feel and chord"
			tabindex="-1"
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
			{#each FEELS as f, c}
				<div class="tint" style="grid-column: {c + 1}; background: {FEEL_COLOURS[f]}"></div>
			{/each}
			{#each rows as chord, r}
				{#each FEELS as feel, c}
					{@const on = feel === value.feel && chord === value.chord}
					<button
						role="radio"
						aria-checked={on}
						aria-label="{feel}, {chord}"
						tabindex={on ? 0 : -1}
						style="grid-column: {c + 1}; grid-row: {r + 1}"
						onclick={() => set(c, r)}
					></button>
				{/each}
			{/each}
			<span
				class="dot"
				aria-hidden="true"
				style="left: {(col + 0.5) * 20}%; top: {(row + 0.5) * 25}%; background: {FEEL_COLOURS[value.feel]}"
			></span>
		</div>
		<div class="collabels" aria-hidden="true">
			{#each FEELS as f}<span style="color: {FEEL_COLOURS[f]}">{f}</span>{/each}
		</div>
	</div>
</div>

<style>
	.pad {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: 8px;
		font-family: var(--mono);
		font-size: 11px;
		color: var(--muted);
	}
	.rowlabels {
		display: grid;
		grid-template-rows: repeat(4, 44px);
		align-items: center;
		text-align: right;
	}
	.grid {
		position: relative;
		display: grid;
		grid-template-columns: repeat(5, 52px);
		grid-template-rows: repeat(4, 44px);
		border-radius: var(--r-md);
		overflow: hidden;
		touch-action: none;
		border: 1px solid var(--line);
	}
	.tint {
		grid-row: 1 / -1;
		opacity: 0.1;
		pointer-events: none;
	}
	button {
		all: unset;
		box-sizing: border-box;
		cursor: pointer;
		border-right: 1px solid var(--line);
		border-bottom: 1px solid var(--line);
		transition: background var(--t);
	}
	button:hover {
		background: var(--hover);
	}
	button:focus-visible {
		outline: 2px solid var(--text);
		outline-offset: -3px;
		border-radius: var(--r-sm);
	}
	.dot {
		position: absolute;
		width: 16px;
		height: 16px;
		margin: -8px 0 0 -8px;
		border-radius: 50%;
		pointer-events: none;
		box-shadow: 0 0 0 4px rgb(13 14 17 / 0.6);
		transition:
			left 180ms ease,
			top 180ms ease,
			background 180ms ease;
	}
	.collabels {
		display: grid;
		grid-template-columns: repeat(5, 52px);
		text-align: center;
		padding-top: 6px;
	}
</style>
