package com.zjianhao.aircon

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zjianhao.aircon.preset.FrequencyRecordStore
import com.zjianhao.aircon.preset.PresetStore
import com.zjianhao.aircon.ui.FrequencyTestActivity
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 频率测试记录：确认成功才入库、失败不入库、成功记录可一键生成预设。
 */
@RunWith(AndroidJUnit4::class)
class FrequencyTestInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val rule = ActivityScenarioRule(FrequencyTestActivity::class.java)

    @Before
    fun resetStores() {
        FrequencyRecordStore(context).clear()
        PresetStore(context).save(emptyList())
    }

    @Test
    fun 确认成功才保存记录并可应用为预设() {
        onView(withId(R.id.btnTest)).perform(click())
        Thread.sleep(3000)

        onView(withText(R.string.test_result_success)).inRoot(isDialog()).perform(click())
        Thread.sleep(800)

        val records = FrequencyRecordStore(context).load()
        assertEquals("成功确认后应保存 1 条记录", 1, records.size)
        assertEquals(38000, records[0].freqHz)

        // ② 记录页：验证列表渲染
        onView(withText(R.string.tab_records)).perform(click())
        Thread.sleep(500)
        onView(allOf(withId(R.id.freqLabel), withText("38kHz")))
            .check(matches(isDisplayed()))

        // ③ 一键预设
        onView(withText(R.string.action_apply_preset)).perform(click())
        Thread.sleep(800)

        val presets = PresetStore(context).load()
        assertTrue("应生成带 38kHz 载波的预设", presets.any { it.carrierFreqHz == 38000 })
    }

    @Test
    fun 选择失败不保存记录() {
        onView(withId(R.id.btnTest)).perform(click())
        Thread.sleep(3000)

        onView(withText(R.string.test_result_failure)).inRoot(isDialog()).perform(click())
        Thread.sleep(800)

        assertTrue("失败不应入库", FrequencyRecordStore(context).load().isEmpty())
    }
}
