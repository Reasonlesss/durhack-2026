# Shape Sequencer

A playful music canvas for people with no music knowledge. Place shapes in a dark space:
each shape is an instrument, lines between shapes make phrases, and large outlines add
effects. Everything snaps to a key and a beat, so nothing can sound wrong.

## Run it

Needs Node 22.17 or newer.

```sh
npm install
npm run dev        # http://localhost:5173
npm run check      # types + Svelte diagnostics
npm run build      # static site in build/
```

All sound is synthesised with the Web Audio API; there are no samples. Browsers only allow
audio after a click or tap, so the first sound comes after your first interaction.

## How it fits together

One screen: the shape canvas. The demo loads with five chains (groove, hook, sparkle,
bass, chords) that all start together. Undo, share link, save and open sit bottom-left.

| Where | What |
| --- | --- |
| `src/lib/music.ts` | Key, modes, chords, height → pitch, everyday-word readouts |
| `src/lib/chain.ts` | Chain graph (cached per edit) and the phrase runner |
| `src/lib/transport.svelte.ts` | Lookahead clock (12 ticks per beat), swing |
| `src/lib/audio/engine.ts` | Voices, effects routing, reverb/echo sends, limiter, voice stealing |
| `src/lib/effects.ts` | Effect outlines: geometry, strength, parameters, words |
| `src/lib/recognise.ts` | Template matching for drawn shapes |
| `src/lib/canvas/` | The canvas: `Stage` (gestures), `render.ts` (Canvas 2D), shelf, HUD, onboarding |
| `src/lib/song.svelte.ts` | The one song store: every edit is undoable and autosaved |

## Things to try

- Tap the heartbeat (top right) to hear the demo.
- Tap empty space, tap a note, then drag from a note's outer ring to somewhere empty.
- Draw a big circle around some notes (dreamy), or a big square (echo).
- Drag "a rave" off the shelf; drag a note back onto the shelf to remove it.
- Open the feel chip and move the dot while it plays.
- Copy a link (bottom left) and open it in another browser.

Saved data lives in `localStorage`. To start over, clear site data, or open DevTools and
run `localStorage.clear()`, then reload. In dev builds, `window.__shape` exposes the live stores.
