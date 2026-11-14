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

- **Song view** (`/`): the composer. Rows are parts, columns are sections; switch cells on
  to choose which parts play in which section. Each section can have its own feel and chord.
- **Part view** (`/part/[id]`): the shape canvas for one part. The heartbeat there loops just
  that part.

| Where | What |
| --- | --- |
| `src/lib/music.ts` | Key, modes, chords, height → pitch, everyday-word readouts |
| `src/lib/chain.ts` | Chain graph (cached per edit) and the phrase runner |
| `src/lib/transport.svelte.ts` | Lookahead clock (12 ticks per beat), song and part modes, swing |
| `src/lib/audio/engine.ts` | Voices, effects routing, reverb/echo sends, limiter, voice stealing |
| `src/lib/effects.ts` | Effect outlines: geometry, strength, parameters, words |
| `src/lib/recognise.ts` | Template matching for drawn shapes |
| `src/lib/canvas/` | The canvas: `Stage` (gestures), `render.ts` (Canvas 2D), shelf, HUD, onboarding |
| `src/lib/song.svelte.ts` | The one song store: every edit is undoable and autosaved |

## Things to try

- Open a part from the song view, tap empty space, then drag from a note's outer ring.
- Draw a big circle around some notes (dreamy), or a big square (echo).
- Drag "a rave" off the shelf.
- In the song view, give a section a different chord and listen to it change.
- Copy a link (top bar) and open it in another browser.

Saved data lives in `localStorage`. To start over, clear site data, or open DevTools and
run `localStorage.clear()`, then reload. In dev builds, `window.__shape` exposes the live stores.
