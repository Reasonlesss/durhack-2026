<!-- Quiet bottom-left tools: undo / redo, copy a share link, save to and open from a file. -->
<script lang="ts">
	import Icon from '../components/Icon.svelte';
	import { songLink } from '../share';
	import { exportSong, importSong, songStore } from '../song.svelte';

	let fileInput: HTMLInputElement;
	let note = $state('');
	let timer: ReturnType<typeof setTimeout> | undefined;

	function tell(text: string) {
		note = text;
		clearTimeout(timer);
		timer = setTimeout(() => (note = ''), 2000);
	}

	async function share() {
		const link = await songLink(songStore.song);
		try {
			await navigator.clipboard.writeText(link);
			tell('link copied');
		} catch {
			prompt('Copy this link', link);
		}
	}

	async function open() {
		const file = fileInput.files?.[0];
		fileInput.value = '';
		if (!file) return;
		try {
			await importSong(file);
		} catch {
			tell("that file isn't a song");
		}
	}
</script>

<div class="tools">
	<button class="icon" aria-label="Undo" title="undo" disabled={!songStore.canUndo} onclick={songStore.undo}><Icon name="undo" size={16} /></button>
	<button class="icon" aria-label="Redo" title="redo" disabled={!songStore.canRedo} onclick={songStore.redo}><Icon name="redo" size={16} /></button>
	<button class="icon" aria-label="Copy a link to share this" title="copy a link to share" onclick={share}><Icon name="link" size={16} /></button>
	<button class="icon" aria-label="Save to a file" title="save to a file" onclick={exportSong}><Icon name="export" size={16} /></button>
	<button class="icon" aria-label="Open a saved file" title="open a saved file" onclick={() => fileInput.click()}><Icon name="import" size={16} /></button>
	<input type="file" accept="application/json,.json" hidden bind:this={fileInput} onchange={open} />
	{#if note}<span role="status">{note}</span>{/if}
</div>

<style>
	.tools {
		display: flex;
		align-items: center;
	}
	.icon {
		color: var(--muted);
	}
	.icon:hover:not(:disabled) {
		color: var(--text);
	}
	span {
		margin-left: 8px;
		font: 12px var(--mono);
		color: var(--muted);
	}
</style>
