package com.zjianhao.aircon.ir

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 定时关机调度。
 *
 * Electra 协议没有定时关机码，所以「运行 N 小时后关机」只能由系统闹钟在到点时
 * 唤醒 App 发一帧关机状态。用 AlarmManager 而不是协程倒计时，**App 被切到后台
 * 甚至进程被回收也能触发**。
 *
 * 注意：用户手动「强行停止」App 会清空所有闹钟，这是系统行为，无法绕过。
 */
object AutoOffScheduler {

    const val ACTION_AUTO_OFF = "com.zjianhao.aircon.action.AUTO_OFF"
    const val EXTRA_CARRIER_HZ = "extra_carrier_hz"
    const val EXTRA_PATTERN = "extra_pattern"

    private const val REQUEST_CODE = 1001

    enum class Result {
        /** 精确闹钟，到点误差极小 */
        EXACT,

        /** 系统不允许精确闹钟，退化为非精确（Doze 下可能延迟数分钟） */
        INEXACT,

        FAILED
    }

    /**
     * @param delayMillis 从现在起多久之后关机
     * @param pattern 关机状态帧的脉冲序列
     */
    fun schedule(
        context: Context,
        delayMillis: Long,
        carrierFreqHz: Int,
        pattern: IntArray
    ): Result {
        if (delayMillis <= 0) return Result.FAILED
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return Result.FAILED

        val pendingIntent = buildPendingIntent(context, carrierFreqHz, pattern)
        val triggerAt = System.currentTimeMillis() + delayMillis

        return try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

            if (canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
                Result.EXACT
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
                Result.INEXACT
            }
        } catch (e: Exception) {
            Result.FAILED
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, 0, IntArray(0))
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(
        context: Context,
        carrierFreqHz: Int,
        pattern: IntArray
    ): PendingIntent {
        val intent = Intent(context, AutoOffReceiver::class.java).apply {
            action = ACTION_AUTO_OFF
            putExtra(EXTRA_CARRIER_HZ, carrierFreqHz)
            putExtra(EXTRA_PATTERN, pattern)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
