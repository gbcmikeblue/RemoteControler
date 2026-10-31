package com.zjianhao.aircon.preset

import android.content.Context
import com.zjianhao.aircon.ir.electra.AcMode
import com.zjianhao.aircon.ir.electra.ElectraSpec
import com.zjianhao.aircon.ir.electra.ElectraState
import com.zjianhao.aircon.ir.electra.FanSpeed
import org.json.JSONArray
import org.json.JSONObject

/**
 * 预设持久化。
 *
 * 需求 3.3 明确不与任何服务端交互，因此用 SharedPreferences + JSON 本地存储，
 * 不引入 Room 等额外依赖。数据量级是个位数到几十条，够用且启动快。
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

    /** 新增或更新，返回落库后的 id。 */
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
        put("emitMode", preset.emitMode.name)
        put("carrierFreqHz", preset.carrierFreqHz)
        put("state", JSONObject().apply {
            put("power", preset.state.power)
            put("mode", preset.state.mode.name)
            put("temperature", preset.state.temperature)
            put("fan", preset.state.fan.name)
            put("sleep", preset.state.sleep)
            put("swing", preset.state.swing)
            put("timerOffHours", preset.state.timerOffHours)
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
        val stateJson = json.optJSONObject("state") ?: JSONObject()
        val state = ElectraState(
            power = stateJson.optBoolean("power", true),
            mode = AcMode.fromName(stateJson.optString("mode")),
            temperature = stateJson.optInt("temperature", 26),
            fan = FanSpeed.fromName(stateJson.optString("fan")),
            sleep = stateJson.optBoolean("sleep", true),
            swing = stateJson.optBoolean("swing", false),
            timerOffHours = stateJson.optInt("timerOffHours", 0)
        )

        val rawArray = json.optJSONArray("raw")
        val rawCommands = if (rawArray == null) emptyList() else buildList {
            for (i in 0 until rawArray.length()) {
                val inner = rawArray.optJSONArray(i) ?: continue
                val command = IntArray(inner.length()) { inner.optInt(it) }
                add(command)
            }
        }

        return Preset(
            id = json.optLong("id", 0L),
            name = json.optString("name", "未命名预设"),
            state = state,
            emitMode = EmitMode.fromName(json.optString("emitMode")),
            carrierFreqHz = json.optInt("carrierFreqHz", ElectraSpec.CARRIER_FREQ_HZ),
            rawCommands = rawCommands
        )
    }

    /** 首次启动给一条符合需求 2.3 场景的预设，开箱即可用。 */
    private fun defaultPresets(): List<Preset> = listOf(
        Preset(
            id = 1L,
            name = "睡眠 26℃ 2 小时后关",
            state = ElectraState(
                power = true,
                mode = AcMode.COOL,
                temperature = 26,
                fan = FanSpeed.AUTO,
                sleep = true,
                timerOffHours = 2
            )
        )
    )

    private companion object {
        const val PREF_NAME = "aircon_presets"
        const val KEY_PRESETS = "presets_json"
    }
}
