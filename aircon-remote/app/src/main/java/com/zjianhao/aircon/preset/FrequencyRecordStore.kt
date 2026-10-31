package com.zjianhao.aircon.preset

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 成功频率记录的本地存储。
 *
 * 只保存用户确认成功的那部分，失败/取消不会进入这里。
 */
class FrequencyRecordStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 按记录时间倒序，最新在前。 */
    fun load(): List<IrFrequencyRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(fromJson(array.getJSONObject(i)))
                }
            }.sortedByDescending { it.recordedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 追加一条成功记录，返回落库后的对象。 */
    fun append(freqHz: Int, recordedAt: Long = System.currentTimeMillis()): IrFrequencyRecord {
        val all = load().toMutableList()
        val newId = (all.maxOfOrNull { it.id } ?: 0L) + 1
        val record = IrFrequencyRecord(id = newId, freqHz = freqHz, recordedAt = recordedAt)
        all += record
        save(all)
        return record
    }

    fun delete(id: Long) {
        save(load().filterNot { it.id == id })
    }

    fun clear() {
        prefs.edit().remove(KEY_RECORDS).apply()
    }

    private fun save(records: List<IrFrequencyRecord>) {
        val array = JSONArray()
        records.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun toJson(record: IrFrequencyRecord): JSONObject = JSONObject().apply {
        put("id", record.id)
        put("freqHz", record.freqHz)
        put("recordedAt", record.recordedAt)
    }

    private fun fromJson(json: JSONObject): IrFrequencyRecord = IrFrequencyRecord(
        id = json.optLong("id", 0L),
        freqHz = json.optInt("freqHz", 0),
        recordedAt = json.optLong("recordedAt", 0L)
    )

    private companion object {
        const val PREF_NAME = "aircon_frequency_records"
        const val KEY_RECORDS = "records_json"
    }
}
