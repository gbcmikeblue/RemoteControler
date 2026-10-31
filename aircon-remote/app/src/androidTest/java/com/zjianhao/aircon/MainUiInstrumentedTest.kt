package com.zjianhao.aircon

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zjianhao.aircon.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 主界面端到端冒烟：红外状态卡片、默认预设渲染、一键发射全流程。
 */
@RunWith(AndroidJUnit4::class)
class MainUiInstrumentedTest {

    @get:Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun 红外状态卡片显示可用() {
        onView(withId(R.id.irStatusTitle))
            .check(matches(withText(R.string.ir_ready)))
            .check(matches(isDisplayed()))
    }

    @Test
    fun 首次启动渲染默认预设() {
        onView(withId(R.id.presetName))
            .check(matches(withText("睡眠 26℃ 2 小时后关")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun 点击一键发射完成整个序列() {
        onView(withId(R.id.emitButton)).perform(click())

        // 序列为 4 帧，帧间隔 300ms，发射本身约 0.3s
        Thread.sleep(4000)

        onView(withId(R.id.emitStatus))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.emit_done)))
    }
}
