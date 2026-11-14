<!-- Full-screen shape canvas for one part. -->
<script lang="ts">
	import Stage from '#lib/canvas/Stage.svelte';
	import type { Part } from '#lib/types.ts';

	let { part }: { part: Part } = $props();
</script>

<!-- Keyed so switching parts gives a fresh view and gesture state. -->
{#key part.id}
	<Stage {part} />
{/key}
