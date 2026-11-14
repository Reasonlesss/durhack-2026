<script lang="ts">
	import Stage from '#lib/canvas/Stage.svelte';
	import { songStore } from '#lib/song.svelte.ts';
</script>

<!-- Keyed by workspace: switching gives a fresh view fitted to that workspace's shapes. -->
{#key songStore.workspace.id}
	<Stage />
{/key}
