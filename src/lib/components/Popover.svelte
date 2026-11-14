<!-- A trigger button plus a floating panel. Only one popover is open app-wide (ui.popover);
     clicking outside or pressing Esc closes it. -->
<script lang="ts">
	import type { Snippet } from 'svelte';
	import { ui } from '#lib/ui.svelte.ts';

	let {
		id,
		label,
		trigger,
		children,
		align = 'left',
		class: cls = ''
	}: { id: string; label: string; trigger: Snippet; children: Snippet; align?: 'left' | 'right'; class?: string } = $props();

	let root: HTMLElement;
	const open = $derived(ui.popover === id);

	function outside(e: PointerEvent) {
		if (open && !root.contains(e.target as Node)) ui.closePopover();
	}
</script>

<svelte:window onpointerdown={outside} />

<div class="pop {cls}" bind:this={root}>
	<button class="trigger" aria-label={label} aria-expanded={open} aria-haspopup="dialog" onclick={() => ui.togglePopover(id)}>
		{@render trigger()}
	</button>
	{#if open}
		<div class="panel" class:right={align === 'right'} role="dialog" aria-label={label}>
			{@render children()}
		</div>
	{/if}
</div>

<style>
	.pop {
		position: relative;
		display: inline-flex;
	}
	.trigger {
		all: unset;
		box-sizing: border-box;
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 44px;
		min-width: 44px;
		justify-content: center;
		border-radius: var(--r-sm);
		cursor: pointer;
		color: var(--text);
		transition: background var(--t);
	}
	.trigger:hover,
	.trigger[aria-expanded='true'] {
		background: var(--hover);
	}
	.trigger:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: 2px;
	}
	.panel {
		position: absolute;
		top: calc(100% + 6px);
		left: 0;
		z-index: 50;
		background: var(--panel);
		border: 1px solid var(--line);
		border-radius: var(--r-lg);
		padding: 10px;
		box-shadow: 0 12px 32px rgb(0 0 0 / 0.45);
		animation: pop-in var(--t) ease-out;
	}
	.panel.right {
		left: auto;
		right: 0;
	}
	@keyframes pop-in {
		from {
			opacity: 0;
			transform: translateY(-4px);
		}
	}
</style>
