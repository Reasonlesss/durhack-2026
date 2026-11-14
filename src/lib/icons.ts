/** Stroke icon paths on a 24×24 grid, rendered by Icon.svelte. */
export const ICONS = {
	play: ['M7 5l12 7-12 7z'],
	stop: ['M7 7h10v10H7z'],
	undo: ['M9 14L4 9l5-5', 'M4 9h11a5 5 0 010 10h-3'],
	redo: ['M15 14l5-5-5-5', 'M20 9H9a5 5 0 000 10h3'],
	export: ['M12 4v11', 'M7 10l5 5 5-5', 'M5 20h14'],
	import: ['M12 15V4', 'M7 9l5-5 5 5', 'M5 20h14'],
	plus: ['M12 5v14', 'M5 12h14'],
	grip: ['M9 6h.01', 'M15 6h.01', 'M9 12h.01', 'M15 12h.01', 'M9 18h.01', 'M15 18h.01'],
	more: ['M6 12h.01', 'M12 12h.01', 'M18 12h.01'],
	mute: ['M4 9h4l5-4v14l-5-4H4z', 'M17 9l5 6', 'M22 9l-5 6'],
	sound: ['M4 9h4l5-4v14l-5-4H4z', 'M17 9a4 4 0 010 6'],
	solo: ['M4 15v-3a8 8 0 0116 0v3', 'M4 15h3v5H4z', 'M17 15h3v5h-3z'],
	back: ['M19 12H5', 'M11 6l-6 6 6 6'],
	click: ['M8 20h8L13 4h-2z', 'M12 14l5-6'],
	close: ['M6 6l12 12', 'M18 6L6 18'],
	minus: ['M5 12h14'],
	link: ['M10 14a4 4 0 005.66 0l3-3a4 4 0 00-5.66-5.66l-1 1', 'M14 10a4 4 0 00-5.66 0l-3 3a4 4 0 005.66 5.66l1-1'],
	check: ['M5 12l5 5 9-10']
} as const;

export type IconName = keyof typeof ICONS;
