/** Shareable song links: the song is gzipped and base64url-encoded into the URL hash. */
import type { Song } from './types';

async function pipe(bytes: Uint8Array<ArrayBuffer>, stream: CompressionStream | DecompressionStream) {
	return new Uint8Array(await new Response(new Blob([bytes]).stream().pipeThrough(stream)).arrayBuffer());
}

export async function songLink(song: Song): Promise<string> {
	const zipped = await pipe(new TextEncoder().encode(JSON.stringify(song)), new CompressionStream('gzip'));
	let bin = '';
	zipped.forEach((b) => (bin += String.fromCharCode(b)));
	const code = btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
	return `${location.origin}/#song=${code}`;
}

/** Song from the current URL hash, if there is one. */
export async function songFromHash(): Promise<unknown | null> {
	const m = location.hash.match(/song=([\w-]+)/);
	if (!m) return null;
	const bin = atob(m[1].replace(/-/g, '+').replace(/_/g, '/'));
	const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
	return JSON.parse(new TextDecoder().decode(await pipe(bytes, new DecompressionStream('gzip'))));
}
