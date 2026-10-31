package com.zjianhao.aircon.ir

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 定时关机到点触发：在后台发一帧关机状态，无需 App 处于前台。
 */
class AutoOffReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutoOffScheduler.ACTION_AUTO_OFF) return

        val carrierFreqHz = intent.getIntExtra(AutoOffScheduler.EXTRA_CARRIER_HZ, 0)
        val pattern = intent.getIntArrayExtra(AutoOffScheduler.EXTRA_PATTERN)
        if (carrierFreqHz <= 0 || pattern == null || pattern.isEmpty()) {
            Log.w(TAG, "auto-off 参数缺失，忽略")
            return
        }

        val pending = goAsync()
        Thread {
            try {
                val result = IrTransmitter(context).transmit(carrierFreqHz, pattern)
                Log.i(TAG, "auto-off fired: carrier=$carrierFreqHz result=$result")
            } catch (e: Exception) {
                Log.e(TAG, "auto-off failed", e)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "AutoOffReceiver"
    }
}
