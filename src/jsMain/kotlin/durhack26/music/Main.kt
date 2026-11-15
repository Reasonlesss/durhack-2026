package durhack26.music

fun main() {
    setupAudio()  // first: everything that makes sound uses the context it sets up
    setupCanvas()
    loadDemoSong()  // remove to start with empty universes
}
