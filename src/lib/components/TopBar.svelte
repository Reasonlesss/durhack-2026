<!-- Song-level controls: name, play, tempo, feel, metronome, undo/redo, save/load. -->
<script lang="ts">
	import HarmonyChip from './HarmonyChip.svelte';
	import Heartbeat from './Heartbeat.svelte';
	import Icon from './Icon.svelte';
	import { exportSong, importSong, songStore } from '#lib/song.svelte.ts';
	import { songLink } from '#lib/share.ts';
	import { transport } from '#lib/transport.svelte.ts';

	let fileInput: HTMLInputElement;
	let error = $state('');
	let copied = $state(false);

	async function share() {
		const link = await songLink(songStore.song);
		await navigator.clipboard.writeText(link).catch(() => prompt('Copy this link', link));
		copied = true;
		setTimeout(() => (copied = false), 1800);
	}

	async function onFile() {
		const file = fileInput.files?.[0];
		fileInput.value = '';
		if (!file) return;
		try {
			await importSong(file);
			error = '';
		} catch (e) {
			error = e instanceof Error ? e.message : 'Could not open that file.';
		}
	}
</script>

<header>
	<input
		class="name"
		aria-label="Song name"
		value={songStore.song.name}
		oninput={(e) => songStore.renameSong(e.currentTarget.value)}
	/>

	<div class="group">
		<button class="icon play" aria-label={transport.isPlaying ? 'Stop' : 'Play'} onclick={() => transport.toggle()}>
			<Icon name={transport.isPlaying ? 'stop' : 'play'} size={20} />
		</button>
		<Heartbeat />
		<HarmonyChip />
		<button
			class="icon"
			aria-label="Ticking sound"
			aria-pressed={transport.metronome}
			title="Ticking sound on every beat"
			onclick={() => (transport.metronome = !transport.metronome)}
		>
			<Icon name="click" />
		</button>
	</div>

	<div class="group end">
		{#if error}<span class="error" role="alert">{error}</span>{/if}
		<button class="icon" aria-label="Undo" title="Undo (Ctrl/Cmd+Z)" disabled={!songStore.canUndo} onclick={songStore.undo}>
			<Icon name="undo" />
		</button>
		<button class="icon" aria-label="Redo" title="Redo (Shift+Ctrl/Cmd+Z)" disabled={!songStore.canRedo} onclick={songStore.redo}>
			<Icon name="redo" />
		</button>
		<button class="icon" aria-label="Save song to a file" title="Save to a file" onclick={exportSong}>
			<Icon name="export" />
		</button>
		<button class="icon" aria-label={copied ? 'Link copied' : 'Copy a link to this song'} title={copied ? 'link copied' : 'copy a link to share'} onclick={share}>
			<Icon name={copied ? 'check' : 'link'} />
		</button>
		<button class="icon" aria-label="Open a song file" title="Open a file" onclick={() => fileInput.click()}>
			<Icon name="import" />
		</button>
		<input type="file" accept="application/json,.json" hidden bind:this={fileInput} onchange={onFile} />
	</div>
</header>

<style>
	header {
		display: flex;
		align-items: center;
		gap: 16px;
		padding: 6px 14px;
		border-bottom: 1px solid var(--line);
		background: var(--panel);
	}
	.name {
		font: 600 16px var(--sans);
		color: var(--text);
		background: none;
		border: 1px solid transparent;
		border-radius: var(--r-sm);
		padding: 0 10px;
		min-height: 44px;
		width: 14em;
	}
	.name:hover {
		border-color: var(--line);
	}
	.name:focus-visible {
		outline: 2px solid var(--accent);
	}
	.group {
		display: flex;
		align-items: center;
		gap: 4px;
	}
	.end {
		margin-left: auto;
	}
	.play {
		color: var(--ground);
		background: var(--accent);
	}
	.play:hover {
		background: var(--accent);
		filter: brightness(1.1);
	}
	.icon[aria-pressed='true'] {
		color: var(--accent);
		background: var(--hover);
	}
	.error {
		color: #ff9b85;
		font-size: 13px;
		margin-right: 8px;
	}
</style>
