<!-- Vertical line at the audible position. transport.songBeat is updated every animation
     frame, so this moves smoothly. Placed over the section columns by Composer. -->
<script lang="ts">
	import { songStore } from '#lib/song.svelte.ts';
	import { transport } from '#lib/transport.svelte.ts';

	const pct = $derived((transport.songBeat / Math.max(1, songStore.timeline.totalBeats)) * 100);
</script>

<div class="track" aria-hidden="true">
	<div class="line" class:idle={!transport.isPlaying} style="transform: translateX({pct}cqw)"></div>
</div>

<style>
	.track {
		position: relative;
		pointer-events: none;
		container-type: inline-size;
		z-index: 2;
	}
	.line {
		position: absolute;
		top: 0;
		bottom: 0;
		left: 0;
		width: 2px;
		margin-left: -1px;
		background: var(--accent);
		will-change: transform;
	}
	.line.idle {
		opacity: 0.35;
	}
</style>
