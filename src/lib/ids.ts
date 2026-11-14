/** Short random id; good enough for local-only documents. */
export function uid(prefix = ''): string {
	return prefix + Math.random().toString(36).slice(2, 10);
}
