<!-- App shell: global styles, feel-tinted background and accent, undo shortcuts, shared links. -->
<script lang="ts">
	import '../app.css';
	import { FEEL_GROUND } from '#lib/music.ts';
	import { songFromHash } from '#lib/share.ts';
	import { songStore } from '#lib/song.svelte.ts';
	import { transport } from '#lib/transport.svelte.ts';
	import { FEEL_COLOURS } from '#lib/types.ts';
	import { ui } from '#lib/ui.svelte.ts';
	import { onMount } from 'svelte';

	let { children } = $props();

	// Dev only: poke at the live stores from the browser console.
	if (import.meta.env.DEV) Object.assign(window, { __shape: { songStore, transport } });

	// A shared link (#song=...) replaces the current song (undoable), then the hash is cleared.
	onMount(async () => {
		try {
			const shared = await songFromHash();
			if (shared) songStore.replaceSong(shared);
		} catch {
			/* broken link: keep the current song */
		}
		if (location.hash) history.replaceState(null, '', location.pathname);
	});

	const isTyping = (t: EventTarget | null) =>
		t instanceof HTMLElement && (t.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(t.tagName));

	// Only undo/redo and Esc are global; the canvas itself has no global shortcuts.
	function keydown(e: KeyboardEvent) {
		const mod = e.metaKey || e.ctrlKey;
		if (mod && (e.key.toLowerCase() === 'z' || e.key.toLowerCase() === 'y') && !isTyping(e.target)) {
			e.preventDefault();
			e.shiftKey || e.key.toLowerCase() === 'y' ? songStore.redo() : songStore.undo();
		} else if (e.key === 'Escape') ui.closePopover();
	}
</script>

<svelte:window onkeydown={keydown} />

<div
	class="app"
	style="--accent: {FEEL_COLOURS[songStore.song.harmony.feel]}; background-color: {FEEL_GROUND[songStore.song.harmony.feel]}"
>
	{@render children()}
</div>

<style>
	.app {
		height: 100vh;
		height: 100dvh;
		display: flex;
		flex-direction: column;
		transition: background-color 0.6s ease;
	}
</style>
