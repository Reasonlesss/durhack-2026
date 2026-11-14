<!-- "More" button with a short list of actions. -->
<script lang="ts">
	import Icon from './Icon.svelte';
	import Popover from './Popover.svelte';
	import { ui } from '#lib/ui.svelte.ts';

	type Item = { label: string; run: () => void; danger?: boolean; disabled?: boolean };
	let { id, label, items }: { id: string; label: string; items: Item[] } = $props();

	function pick(item: Item) {
		ui.closePopover();
		item.run();
	}
</script>

<Popover {id} {label} align="right">
	{#snippet trigger()}<Icon name="more" />{/snippet}
	<ul role="menu">
		{#each items as item}
			<li role="none">
				<button role="menuitem" class:danger={item.danger} disabled={item.disabled} onclick={() => pick(item)}>{item.label}</button>
			</li>
		{/each}
	</ul>
</Popover>

<style>
	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		min-width: 170px;
	}
	button {
		all: unset;
		box-sizing: border-box;
		width: 100%;
		min-height: 44px;
		padding: 0 12px;
		border-radius: var(--r-sm);
		cursor: pointer;
		font-size: 14px;
	}
	button:hover,
	button:focus-visible {
		background: var(--hover);
	}
	button:focus-visible {
		outline: 2px solid var(--accent);
	}
	button:disabled {
		color: var(--muted);
		cursor: default;
	}
	.danger {
		color: #ff9b85;
	}
</style>
