import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [
		// Client-only app (audio + localStorage), so build an SPA with a fallback page.
		sveltekit({ adapter: adapter({ fallback: 'index.html' }) })
	]
});
