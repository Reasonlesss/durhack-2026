/** Shared UI state that several components need: selection and the one open popover. */
export type Selection =
	| { kind: 'cell'; partId: string; sectionId: string }
	| { kind: 'part'; partId: string }
	| { kind: 'section'; sectionId: string }
	| null;

class UiState {
	selection = $state<Selection>(null);
	/** Id of the open popover (harmony pad, menus). Only one at a time. */
	popover = $state<string | null>(null);

	togglePopover = (id: string) => (this.popover = this.popover === id ? null : id);
	closePopover = () => {
		const was = this.popover !== null;
		this.popover = null;
		return was;
	};
}

export const ui = new UiState();
