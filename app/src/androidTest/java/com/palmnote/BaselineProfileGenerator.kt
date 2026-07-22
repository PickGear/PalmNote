package com.palmnote

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineRule.collect(packageName = "com.palmnote") {
            pressHome()
            startActivityAndWait()

            // 等待首页加载
            waitForIdle()

            // 切换到物品页
            val assetTab = device.findObject(By.text("物品"))
            assetTab?.click()
            waitForIdle()

            // 切换列表/网格
            device.findObject(By.desc("切换视图"))?.click()
            waitForIdle()
            device.findObject(By.desc("切换视图"))?.click()
            waitForIdle()

            // 切换到账本页
            device.findObject(By.text("账本"))?.click()
            waitForIdle()

            // 切换到生活页
            device.findObject(By.text("生活"))?.click()
            waitForIdle()

            // 切回首页
            device.findObject(By.text("首页"))?.click()
            waitForIdle()
        }
    }
}
