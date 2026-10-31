package com.zjianhao.aircon.ir

/**
 * 红外脉冲序列（IntArray，单位微秒）的通用工具与校验。
 *
 * 序列约定：交替出现，下标 0 必须是载波（mark），奇数下标是间隔（space）。
 */
object PulsePattern {

    /**
     * 需求 4.3：单次 pattern 总时长必须小于 2 秒，否则系统拒绝发射。
     * 这里取 2,000,000us 作为硬上限。
     */
    const val MAX_TOTAL_DURATION_US = 2_000_000

    fun totalDurationUs(pattern: IntArray): Long {
        var sum = 0L
        for (value in pattern) sum += value
        return sum
    }

    /**
     * @return null 表示合法，否则返回可直接展示给用户的错误说明
     */
    fun validate(pattern: IntArray): String? {
        if (pattern.isEmpty()) return "红外脉冲序列为空"
        if (pattern.size % 2 != 0) return "红外脉冲序列必须成对（载波段与间隔段）出现"
        pattern.forEachIndexed { index, value ->
            if (value <= 0) return "第 ${index + 1} 段时长非法：$value us"
        }
        val total = totalDurationUs(pattern)
        if (total > MAX_TOTAL_DURATION_US) {
            return "单次发射总时长 $total us 超过 2 秒上限"
        }
        return null
    }
}

/** 便捷的脉冲序列构造器。 */
class PulsePatternBuilder(initialCapacity: Int = 256) {

    private val pulses = ArrayList<Int>(initialCapacity)

    fun mark(us: Int) = apply { pulses.add(us) }

    fun space(us: Int) = apply { pulses.add(us) }

    fun bit(markUs: Int, spaceUs: Int) = apply {
        pulses.add(markUs)
        pulses.add(spaceUs)
    }

    fun size(): Int = pulses.size

    fun build(): IntArray = pulses.toIntArray()
}
