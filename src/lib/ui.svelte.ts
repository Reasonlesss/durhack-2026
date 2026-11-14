/** Shared UI state: the one open popover (the harmony pad). */
class UiState {
	popover = $state<string | null>(null);

	togglePopover = (id: string) => (this.popover = this.popover === id ? null : id);
	closePopover = () => {
		const was = this.popover !== null;
		this.popover = null;
		return was;
	};
}

export const ui = new UiState();
