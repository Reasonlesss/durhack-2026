<!-- Bottom-right workspace switcher. Each workspace is its own canvas:
     the dot turns it on or off (enabled ones play together), the name switches to it
     (double-click to rename), + makes a new one, × removes the current one. -->
<script lang="ts">
	import { tick } from 'svelte';
	import Icon from '../components/Icon.svelte';
	import { songStore } from '../song.svelte';

	let renaming = $state<string | null>(null);

	async function startRename(id: string) {
		renaming = id;
		await tick();
		document.querySelector<HTMLInputElement>('.spaces-row input')?.select();
	}
	function finishRename(id: string, value: string) {
		if (renaming !== id) return; // Enter already saved it; ignore the blur that follows
		songStore.renameWorkspace(id, value);
		renaming = null;
	}
</script>

<div class="spaces-row" role="group" aria-label="Workspaces">
	{#each songStore.song.workspaces as w (w.id)}
		{@const current = w.id === songStore.workspace.id}
		<div class="space" class:current class:off={!w.enabled}>
			<button
				class="dot"
				aria-pressed={w.enabled}
				aria-label="{w.enabled ? 'Silence' : 'Play'} workspace {w.name}"
				title={w.enabled ? 'playing · press to silence' : 'silent · press to play'}
				onclick={() => songStore.toggleWorkspace(w.id)}
			><span></span></button>
			{#if renaming === w.id}
				<input
					aria-label="Workspace name"
					value={w.name}
					onkeydown={(e) => {
						if (e.key === 'Enter') finishRename(w.id, e.currentTarget.value);
						if (e.key === 'Escape') renaming = null;
					}}
					onblur={(e) => finishRename(w.id, e.currentTarget.value)}
				/>
			{:else}
				<button
					class="name"
					aria-current={current}
					aria-label="{current ? 'Editing' : 'Switch to'} workspace {w.name}. Double-click to rename."
					onclick={() => songStore.switchTo(w.id)}
					ondblclick={() => startRename(w.id)}
					onkeydown={(e) => e.key === 'F2' && startRename(w.id)}
				>{w.name}</button>
			{/if}
			{#if current && songStore.song.workspaces.length > 1}
				<button class="remove" aria-label="Remove workspace {w.name}" title="remove this workspace" onclick={() => songStore.removeWorkspace(w.id)}>
					<Icon name="close" size={12} />
				</button>
			{/if}
		</div>
	{/each}
	<button class="add" aria-label="New workspace" title="new workspace" onclick={songStore.addWorkspace}><Icon name="plus" size={16} /></button>
</div>

<style>
	.spaces-row {
		display: flex;
		align-items: center;
		gap: 4px;
		font: 12px var(--mono);
	}
	.space {
		display: flex;
		align-items: center;
		border-radius: 10px;
		border: 1px solid transparent;
		transition: border-color 180ms ease, background 180ms ease;
	}
	.space.current {
		border-color: var(--line);
		background: rgb(21 23 28 / 0.7);
	}
	button {
		all: unset;
		box-sizing: border-box;
		min-height: 44px;
		display: grid;
		place-items: center;
		cursor: pointer;
		border-radius: 8px;
		color: var(--muted);
		transition: color 180ms ease;
	}
	button:hover {
		color: var(--text);
	}
	button:focus-visible,
	input:focus-visible {
		outline: 2px solid var(--accent);
	}
	.dot {
		width: 32px;
	}
	.dot span {
		width: 10px;
		height: 10px;
		border-radius: 50%;
		border: 1.5px solid var(--accent);
		background: var(--accent);
		transition: background 180ms ease;
	}
	.off .dot span {
		background: transparent;
		border-color: var(--muted);
	}
	.name {
		padding: 0 10px 0 2px;
	}
	.current .name {
		color: var(--text);
	}
	.off .name {
		text-decoration: line-through;
		text-decoration-color: rgb(154 157 166 / 0.6);
	}
	.remove {
		width: 32px;
	}
	.add {
		width: 44px;
	}
	input {
		width: 8em;
		min-height: 32px;
		margin-right: 6px;
		font: 12px var(--mono);
		color: var(--text);
		background: var(--ground);
		border: 1px solid var(--line);
		border-radius: 6px;
		padding: 0 6px;
	}
</style>
