package com.zjianhao.aircon.preset

import android.content.Context
import com.zjianhao.aircon.ir.electra.AcMode
import com.zjianhao.aircon.ir.electra.ElectraSpec
import com.zjianhao.aircon.ir.electra.ElectraState
import com.zjianhao.aircon.ir.electra.FanSpeed
import org.json.JSONArray
import org.json.JSONObject

/**
 * 预设持久化（本地 SharedPreferences + JSON，不依赖任何服务端）。
 */
class PresetStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(): List<Preset> {
        val raw = prefs.getString(KEY_PRESETS, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultPresets()
            save(defaults)
            return defaults
        }
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(fromJson(array.getJSONObject(i)))
                }
            }
        } catch (e: Exception) {
            defaultPresets()
        }
    }

    fun save(presets: List<Preset>) {
        val array = JSONArray()
        presets.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    /** 新增或更新，返回落库后的预设。 */
    fun upsert(preset: Preset): Preset {
        val all = load().toMutableList()
        return if (preset.id == 0L) {
            val newId = (all.maxOfOrNull { it.id } ?: 0L) + 1
            val created = preset.copy(id = newId)
            all += created
            save(all)
            created
        } else {
            val index = all.indexOfFirst { it.id == preset.id }
            if (index >= 0) all[index] = preset else all += preset
            save(all)
            preset
        }
    }

    fun delete(id: Long) {
        save(load().filterNot { it.id == id })
    }

    fun find(id: Long): Preset? = load().firstOrNull { it.id == id }

    // ---- 序列化 ----

    private fun toJson(preset: Preset): JSONObject = JSONObject().apply {
        put("id", preset.id)
        put("name", preset.name)
        put("carrierFreqHz", preset.carrierFreqHz)
        put("state", JSONObject().apply {
            put("power", preset.state.power)
            put("mode", preset.state.mode.name)
            put("temperature", preset.state.temperature)
            put("fan", preset.state.fan.name)
            put("swingVertical", preset.state.swingVertical)
            put("swingHorizontal", preset.state.swingHorizontal)
            put("quiet", preset.state.quiet)
            put("turbo", preset.state.turbo)
            put("iFeel", preset.state.iFeel)
            put("clean", preset.state.clean)
            put("lightToggle", preset.state.lightToggle)
            preset.state.sensorTemperature?.let { put("sensorTemperature", it) }
            put("autoOffMinutes", preset.state.autoOffMinutes)
        })
        if (preset.rawCommands.isNotEmpty()) {
            put("raw", JSONArray().apply {
                preset.rawCommands.forEach { command ->
                    put(JSONArray().apply { command.forEach { put(it) } })
                }
            })
        }
    }

    private fun fromJson(json: JSONObject): Preset {
        val s = json.optJSONObject("state") ?: JSONObject()
        val state = ElectraState(
            power = s.optBoolean("power", true),
            mode = AcMode.fromName(s.optString("mode")),
            temperature = s.optInt("temperature", 26),
            fan = FanSpeed.fromName(s.optString("fan")),
            swingVertical = s.optBoolean("swingVertical", false),
            swingHorizontal = s.optBoolean("swingHorizontal", false),
            // 兼容旧版本字段名 sleep
            quiet = s.optBoolean("quiet", s.optBoolean("sleep", false)),
            turbo = s.optBoolean("turbo", false),
            iFeel = s.optBoolean("iFeel", false),
            clean = s.optBoolean("clean", false),
            lightToggle = s.optInt("lightToggle", ElectraSpec.LIGHT_TOGGLE_OFF),
            sensorTemperature = if (s.has("sensorTemperature")) s.optInt("sensorTemperature") else null,
            // 兼容历史字段：autoOffHours / timerOffHours 都是小时，转成分钟
            autoOffMinutes = when {
                s.has("autoOffMinutes") -> s.optInt("autoOffMinutes", 0)
                s.has("autoOffHours") -> s.optInt("autoOffHours", 0) * 60
                else -> s.optInt("timerOffHours", 0) * 60
            }
        )

        val rawArray = json.optJSONArray("raw")
        val rawCommands = if (rawArray == null) emptyList() else buildList {
            for (i in 0 until rawArray.length()) {
                val inner = rawArray.optJSONArray(i) ?: continue
                add(IntArray(inner.length()) { inner.optInt(it) })
            }
        }

        return Preset(
            id = json.optLong("id", 0L),
            name = json.optString("name", "未命名预设"),
            state = state,
            carrierFreqHz = json.optInt("carrierFreqHz", ElectraSpec.CARRIER_FREQ_HZ),
            rawCommands = rawCommands
        )
    }

    /** 首次启动的默认预设，对应用户睡前场景。 */
    private fun defaultPresets(): List<Preset> = listOf(
        Preset(
            id = 1L,
            name = "睡眠 26℃ 2 小时后关",
            state = ElectraState(
                power = true,
                mode = AcMode.COOL,
                temperature = 26,
                fan = FanSpeed.AUTO,
                quiet = true,
                autoOffMinutes = 120
            )
        )
    )

    private companion object {
        const val PREF_NAME = "aircon_presets"
        const val KEY_PRESETS = "presets_json"
    }
}
