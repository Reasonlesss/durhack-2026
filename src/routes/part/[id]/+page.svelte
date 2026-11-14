<script lang="ts">
	import { page } from '$app/state';
	import PartView from '#lib/components/PartView.svelte';
	import { songStore } from '#lib/song.svelte.ts';

	const part = $derived(songStore.part(page.params.id ?? ''));
</script>

{#if part}
	<PartView {part} />
{:else}
	<p class="missing">That part no longer exists. <a href="/">Back to the song</a></p>
{/if}

<style>
	.missing {
		padding: 24px;
		color: var(--muted);
	}
	.missing a {
		color: var(--accent);
	}
</style>
