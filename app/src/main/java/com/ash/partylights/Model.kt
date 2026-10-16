package com.ash.partylights

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Mode(val label: String) {
    STROBE("Strobe"),
    DISCO("Disco"),
    LASERS("Lasers"),
    SWIRL("Trippy Swirl"),
    KALEIDO("Kaleidoscope"),
    TUNNEL("Tunnel"),
    AURORA("Aurora"),
    LAVA("Lava"),
    BANNER("LED Text"),
}

enum class Palette(val label: String, val colors: List<Long>) {
    RAINBOW("Rainbow", listOf(0xFFFF1744, 0xFFFF9100, 0xFFFFEA00, 0xFF00E676, 0xFF00B0FF, 0xFF3D5AFE, 0xFFD500F9)),
    NEON("Neon", listOf(0xFFFF00C8, 0xFF00FFF0, 0xFF39FF14, 0xFFFFF000, 0xFF8A2BE2)),
    FIRE("Fire", listOf(0xFFFF0000, 0xFFFF4500, 0xFFFF8C00, 0xFFFFD000, 0xFFFF2060)),
    OCEAN("Ocean", listOf(0xFF0040FF, 0xFF00A0FF, 0xFF00FFD0, 0xFF0070A0, 0xFF6040FF)),
    SUNSET("Sunset", listOf(0xFFFF5E62, 0xFFFF9966, 0xFFFFC371, 0xFFC04080, 0xFF6A3093)),
    CUSTOM("Custom", emptyList()),
}

enum class BeatSource(val label: String) { AUTO("Auto tempo"), MIC("Music (mic)"), TAP("Tap tempo") }

enum class SyncRole(val label: String) { OFF("Off"), HOST("Host"), GUEST("Join") }

/** Colours offered in the custom palette editor. */
val customSwatches: List<Long> = listOf(
    0xFFFF1744, 0xFFFF6D00, 0xFFFFEA00, 0xFF76FF03, 0xFF00E676, 0xFF1DE9B6,
    0xFF00E5FF, 0xFF2979FF, 0xFF651FFF, 0xFFD500F9, 0xFFFF4081, 0xFFFFFFFF,
)

data class PartySettings(
    val mode: Mode = Mode.SWIRL,
    val palette: Palette = Palette.RAINBOW,
    val customColors: List<Long> = listOf(0xFFFF4081, 0xFF00E5FF, 0xFF76FF03),
    val speed: Float = 1f,
    val brightness: Float = 1f,
    val beatSource: BeatSource = BeatSource.AUTO,
    val autoDj: Boolean = false,
    val autoDjBeats: Int = 16,
    val torch: Boolean = false,
    val safeMode: Boolean = true,
    val text: String = "LET'S PARTY",
    val sleepMinutes: Int = 0,
    val syncRole: SyncRole = SyncRole.OFF,
) {
    val colors: List<Long>
        get() = if (palette == Palette.CUSTOM) customColors.ifEmpty { Palette.RAINBOW.colors } else palette.colors

    /** Effect that will really be shown, respecting safe mode. */
    val effectiveMode: Mode get() = if (safeMode && mode == Mode.STROBE) Mode.DISCO else mode

    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode.name)
        put("palette", palette.name)
        put("custom", JSONArray(customColors))
        put("speed", speed.toDouble())
        put("brightness", brightness.toDouble())
        put("beat", beatSource.name)
        put("autoDj", autoDj)
        put("autoDjBeats", autoDjBeats)
        put("torch", torch)
        put("safe", safeMode)
        put("text", text)
        put("sleep", sleepMinutes)
        put("sync", syncRole.name)
    }

    companion object {
        fun fromJson(o: JSONObject): PartySettings {
            val d = PartySettings()
            val custom = o.optJSONArray("custom")?.let { a -> List(a.length()) { a.getLong(it) } } ?: d.customColors
            return PartySettings(
                mode = enumOr(o.optString("mode"), d.mode),
                palette = enumOr(o.optString("palette"), d.palette),
                customColors = custom,
                speed = o.optDouble("speed", d.speed.toDouble()).toFloat(),
                brightness = o.optDouble("brightness", d.brightness.toDouble()).toFloat(),
                beatSource = enumOr(o.optString("beat"), d.beatSource),
                autoDj = o.optBoolean("autoDj", d.autoDj),
                autoDjBeats = o.optInt("autoDjBeats", d.autoDjBeats),
                torch = o.optBoolean("torch", d.torch),
                safeMode = o.optBoolean("safe", d.safeMode),
                text = o.optString("text", d.text),
                sleepMinutes = o.optInt("sleep", d.sleepMinutes),
                syncRole = enumOr(o.optString("sync"), d.syncRole),
            )
        }
    }
}

inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

data class Scene(val name: String, val settings: PartySettings)

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("party_lights", Context.MODE_PRIVATE)

    fun load(): PartySettings = try {
        prefs.getString("settings", null)?.let { PartySettings.fromJson(JSONObject(it)) } ?: PartySettings()
    } catch (_: Exception) {
        PartySettings()
    }

    fun save(s: PartySettings) = prefs.edit().putString("settings", s.toJson().toString()).apply()

    var warningAccepted: Boolean
        get() = prefs.getBoolean("warning_ok", false)
        set(v) = prefs.edit().putBoolean("warning_ok", v).apply()

    fun loadScenes(): List<Scene> = try {
        val a = JSONArray(prefs.getString("scenes", "[]"))
        List(a.length()) {
            val o = a.getJSONObject(it)
            Scene(o.getString("name"), PartySettings.fromJson(o.getJSONObject("s")))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveScenes(list: List<Scene>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("s", it.settings.toJson())) }
        prefs.edit().putString("scenes", a.toString()).apply()
    }
}
