package app.muchi.music.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import kotlin.math.roundToInt

/**
 * Dolby-Atmos-style sound engine for the native player.
 *
 * True Dolby Atmos (object-based audio) is a licensed Dolby technology and cannot
 * be decoded by third-party apps. What most phones' own "Dolby Atmos" actually is —
 * and what this engine reproduces — is a chain of Android audio effects attached to
 * the player's audio session:
 *
 *   Equalizer (tone curve) -> BassBoost -> Virtualizer (3D headphone surround)
 *   -> LoudnessEnhancer -> PresetReverb (room ambience)
 *
 * It works on virtually every Android device. On phones with a system Dolby Atmos
 * (Samsung, Xiaomi, OnePlus, …) the system effect applies on top of this chain.
 */
object SoundEngine {
    const val OFF = "off"
    const val PHONE = "phone"
    const val BASS = "bass"
    const val SPATIAL = "spatial" // Atmos-style 3D virtualization
    const val DYNAMIC = "dynamic"

    @Volatile
    var mode: String = PHONE
        private set

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var reverb: PresetReverb? = null
    private var session = -1

    // Flat (no-op) band levels captured when the equalizer attaches, so presets
    // are relative to the device's default curve and never accumulate.
    private var flatLevels = FloatArray(0)

    /** Attach (or re-attach) the effect chain to an audio session id. */
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId == session) return
        release()
        session = audioSessionId
        try {
            eq = try { Equalizer(0, audioSessionId) } catch (_: Throwable) { null }
            bass = try { BassBoost(0, audioSessionId) } catch (_: Throwable) { null }
            virt = try { Virtualizer(0, audioSessionId) } catch (_: Throwable) { null }
            loud = try { LoudnessEnhancer(audioSessionId) } catch (_: Throwable) { null }
            reverb = try { PresetReverb(0, audioSessionId) } catch (_: Throwable) { null }
        } catch (_: Throwable) {
            // Some devices reject individual effects; the rest still work.
        }
        flatLevels = try {
            val e = eq
            if (e != null) FloatArray(e.numberOfBands.toInt()) { i -> e.getBandLevel(i.toShort()).toFloat() } else FloatArray(0)
        } catch (_: Throwable) {
            FloatArray(0)
        }
        apply(mode)
    }

    /** Change preset and push it to the attached effects (safe before attach too). */
    fun apply(mode: String) {
        this.mode = if (mode in setOf(OFF, PHONE, BASS, SPATIAL, DYNAMIC)) mode else PHONE
        val on = this.mode != OFF
        applyEq(this.mode)
        applyEffect(bass) { b, m ->
            if (m == OFF) { b.setStrength(0); b.enabled = false }
            else { b.setStrength(bassFor(m)); b.enabled = true }
        }
        applyEffect(virt) { v, m ->
            if (m == OFF) { v.setStrength(0); v.enabled = false }
            else { v.setStrength(virtFor(m)); v.enabled = true }
        }
        applyEffect(loud) { l, m ->
            val gain = loudFor(m)
            if (m == OFF || gain <= 0) { l.setTargetGain(0); l.enabled = false }
            else { l.setTargetGain(gain); l.enabled = true }
        }
        applyEffect(reverb) { r, m ->
            val preset = reverbFor(m)
            if (m == OFF || preset == PresetReverb.PRESET_NONE) { r.setPreset(PresetReverb.PRESET_NONE); r.enabled = false }
            else { r.setPreset(preset); r.enabled = true }
        }
    }

    /** Detach everything (player teardown / session change). */
    fun release() {
        try { eq?.let { it.enabled = false; it.release() } } catch (_: Throwable) {}
        try { bass?.let { it.enabled = false; it.release() } } catch (_: Throwable) {}
        try { virt?.let { it.enabled = false; it.release() } } catch (_: Throwable) {}
        try { loud?.let { it.enabled = false; it.release() } } catch (_: Throwable) {}
        try { reverb?.let { it.enabled = false; it.release() } } catch (_: Throwable) {}
        eq = null; bass = null; virt = null; loud = null; reverb = null
        session = -1
        flatLevels = FloatArray(0)
    }

    // --- presets -----------------------------------------------------------

    // 5-point curve in millibels: [low, low-mid, mid, high-mid, high]
    private fun curveFor(m: String): FloatArray = when (m) {
        OFF -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
        PHONE -> floatArrayOf(250f, 80f, -60f, 80f, 250f)
        BASS -> floatArrayOf(600f, 300f, -120f, 80f, 200f)
        SPATIAL -> floatArrayOf(200f, 40f, -120f, 40f, 200f)
        DYNAMIC -> floatArrayOf(300f, 100f, 0f, 100f, 300f)
        else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
    }

    private fun bassFor(m: String): Short = when (m) {
        PHONE -> 150
        BASS -> 650
        SPATIAL -> 300
        DYNAMIC -> 350
        else -> 0
    }

    private fun virtFor(m: String): Short = when (m) {
        PHONE -> 250
        BASS -> 120
        SPATIAL -> 850 // strong 3D surround — the "Atmos" feel
        DYNAMIC -> 350
        else -> 0
    }

    private fun loudFor(m: String): Int = if (m == DYNAMIC) 1100 else 0

    private fun reverbFor(m: String): Short = when (m) {
        SPATIAL -> PresetReverb.PRESET_SMALLROOM
        DYNAMIC -> PresetReverb.PRESET_MEDIUMROOM
        else -> PresetReverb.PRESET_NONE
    }

    // --- internals ---------------------------------------------------------

    private fun applyEq(m: String) {
        val e = eq ?: return
        try {
            val n = e.numberOfBands.toInt()
            if (n == 0) return
            val range = e.bandLevelRange
            if (range.size < 2) return
            val min = range[0].toInt()
            val max = range[1].toInt()
            val curve = curveFor(m)
            for (i in 0 until n) {
                val pos = if (n <= 1) 0f else i.toFloat() / (n - 1).toFloat()
                val idx = (pos * (curve.size - 1).toFloat()).roundToInt().coerceIn(0, curve.size - 1)
                val flat = if (i < flatLevels.size) flatLevels[i] else 0f
                val target = (flat + curve[idx]).toInt().coerceIn(min, max)
                e.setBandLevel(i.toShort(), target.toShort())
            }
            e.enabled = m != OFF
        } catch (_: Throwable) {
        }
    }

    private inline fun <T> applyEffect(effect: T?, block: (T, String) -> Unit) {
        val e = effect ?: return
        try {
            block(e, mode)
        } catch (_: Throwable) {
        }
    }
}
