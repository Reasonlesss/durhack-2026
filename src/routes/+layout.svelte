<!-- App shell: global styles, accent colour from the song's feel, and keyboard shortcuts. -->
<script lang="ts">
	import '../app.css';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { songStore } from '#lib/song.svelte.ts';
	import { transport } from '#lib/transport.svelte.ts';
	import { FEEL_COLOURS } from '#lib/types.ts';
	import { FEEL_GROUND } from '#lib/music.ts';
	import { songFromHash } from '#lib/share.ts';
	import { onMount } from 'svelte';
	import { ui } from '#lib/ui.svelte.ts';

	let { children } = $props();

	// A shared link (#song=...) replaces the current song (undoable), then the hash is cleared.
	onMount(async () => {
		try {
			const shared = await songFromHash();
			if (shared) songStore.replaceSong(shared as never);
		} catch {
			/* broken link: keep the current song */
		}
		if (location.hash) history.replaceState(null, '', location.pathname);
	});

	// Dev only: poke at the live stores from the browser console.
	if (import.meta.env.DEV) Object.assign(window, { __shape: { songStore, transport } });

	const onCanvas = () => page.route.id === '/part/[id]';
	const isTyping = (t: EventTarget | null) =>
		t instanceof HTMLElement && (t.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(t.tagName));

	function keydown(e: KeyboardEvent) {
		const mod = e.metaKey || e.ctrlKey;
		if (mod && e.key.toLowerCase() === 'z') {
			if (isTyping(e.target)) return; // let inputs keep their own text undo
			e.preventDefault();
			e.shiftKey ? songStore.redo() : songStore.undo();
			return;
		}
		if (mod && e.key.toLowerCase() === 'y') {
			e.preventDefault();
			songStore.redo();
			return;
		}
		if (e.key === 'Escape') {
			if (ui.closePopover()) return;
			if (page.route.id === '/part/[id]') goto('/');
			else ui.selection = null;
			return;
		}
		// The shape canvas has no global shortcuts (its notes handle their own keys).
		if (isTyping(e.target) || onCanvas()) return;
		if (e.key === ' ') {
			e.preventDefault();
			transport.toggle();
		} else if (e.key === 'Delete' || e.key === 'Backspace') {
			const s = ui.selection;
			if (s?.kind === 'part') songStore.deletePart(s.partId);
			else if (s?.kind === 'section') songStore.deleteSection(s.sectionId);
			else return;
			ui.selection = null;
			e.preventDefault();
		}
	}
</script>

<svelte:window onkeydown={keydown} />

<div
	class="app"
	style="--accent: {FEEL_COLOURS[songStore.song.harmony.feel]}; background: {FEEL_GROUND[songStore.song.harmony.feel]}"
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
