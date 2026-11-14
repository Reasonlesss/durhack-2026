<script lang="ts" module>
	import type { EffectKind, Instrument } from '../types';
	import type { StarterName } from '../starters';

	export type ShelfItem =
		| { kind: 'instrument'; id: Instrument; label: string }
		| { kind: 'effect'; id: EffectKind; label: string }
		| { kind: 'starter'; id: StarterName; label: string };
</script>

<!-- The left-edge shelf: drag things out to make them, drop things back to remove them.
     Keyboard: press an item to place it in the middle of the view. -->
<script lang="ts">
	import { INSTRUMENTS } from '../music';
	import { effectIcon, noteShape } from '../shapes';

	let {
		onpick,
		onplace,
		dropping
	}: { onpick: (item: ShelfItem, e: PointerEvent) => void; onplace: (item: ShelfItem) => void; dropping: boolean } = $props();

	const instruments: ShelfItem[] = (['lead', 'chords', 'bells', 'bass', 'drums', 'stab', 'acid', 'siren', 'pause'] as const).map((id) => ({
		kind: 'instrument',
		id,
		label: INSTRUMENTS[id].label
	}));
	const effects: ShelfItem[] = (['dreamy', 'echo', 'muffle', 'crunch', 'distortion'] as const).map((id) => ({ kind: 'effect', id, label: id }));
	const starters: ShelfItem[] = (['a beat', 'a bassline', 'a break', 'a rave'] as const).map((id) => ({ kind: 'starter', id, label: id }));

	/** Tiny composite icons for starters. */
	const STARTER_ICON: Record<StarterName, [Instrument, number, number][]> = {
		'a beat': [['drums', -6, 3], ['drums', 6, -3]],
		'a bassline': [['bass', -6, 3], ['bass', 6, -2]],
		'a break': [['drums', -7, 2], ['drums', 0, -4], ['drums', 7, 2]],
		'a rave': [['stab', -5, -2], ['acid', 6, 3]]
	};
</script>

<nav class="shelf" class:dropping aria-label="Shelf: drag things onto the canvas">
	{#each [instruments, effects, starters] as group, gi}
		{#if gi}<hr />{/if}
		{#each group as item}
			<button
				aria-label="{item.label}. Drag onto the canvas, or press to place it in the middle."
				onpointerdown={(e) => {
					if (e.button !== 0) return;
					e.preventDefault();
					onpick(item, e);
				}}
				onclick={(e) => e.detail === 0 && onplace(item)}
			>
				<svg viewBox="-16 -16 32 32" width="26" height="26" aria-hidden="true">
					{#if item.kind === 'instrument'}
						{@const s = noteShape(item.id, 18)}
						<path d={s.d} fill={s.stroke ? 'none' : 'currentColor'} stroke={s.stroke ? 'currentColor' : 'none'} stroke-width="2" stroke-dasharray={s.dashed ? '3 3' : undefined} />
					{:else if item.kind === 'effect'}
						<path d={effectIcon(item.id, 11)} fill="none" stroke="var(--accent)" stroke-width="1.6" />
					{:else}
						{#each STARTER_ICON[item.id] as [inst, x, y]}
							{@const s = noteShape(inst, 11)}
							<path d={s.d} transform="translate({x} {y})" fill="currentColor" />
						{/each}
					{/if}
				</svg>
				<span class="label">{item.label}</span>
			</button>
		{/each}
	{/each}
	{#if dropping}<span class="bin" role="status">let go to remove it</span>{/if}
</nav>

<style>
	.shelf {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		position: relative;
		border-radius: 14px;
		transition: background 200ms ease;
	}
	.shelf.dropping {
		background: rgb(236 231 221 / 0.06);
		outline: 1px dashed var(--muted);
	}
	button {
		all: unset;
		position: relative;
		display: grid;
		place-items: center;
		width: 44px;
		height: 38px;
		color: var(--text);
		cursor: grab;
		touch-action: none;
	}
	svg {
		opacity: 0.42;
		transition: opacity 180ms ease;
	}
	button:hover svg,
	button:focus-visible svg {
		opacity: 1;
	}
	button:focus-visible {
		outline: 2px solid var(--accent);
		border-radius: 8px;
	}
	.label {
		position: absolute;
		left: 46px;
		white-space: nowrap;
		font: 12px var(--mono);
		color: var(--text);
		background: rgb(13 14 17 / 0.85);
		padding: 2px 6px;
		border-radius: 6px;
		opacity: 0;
		pointer-events: none;
		transition: opacity 150ms ease;
	}
	button:hover .label,
	button:focus-visible .label {
		opacity: 1;
	}
	hr {
		width: 20px;
		margin: 4px 12px;
		border: 0;
		border-top: 1px solid var(--line);
	}
	.bin {
		position: absolute;
		left: 50px;
		top: 50%;
		font: 12px var(--mono);
		color: var(--muted);
		white-space: nowrap;
	}
</style>
