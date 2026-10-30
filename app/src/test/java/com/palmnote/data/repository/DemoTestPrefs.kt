package com.palmnote.data.repository

import com.palmnote.data.datastore.PreferencesManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

/**
 * 仓库层按「演示开关」分流取数（示例模式只看 isDemo 行）。
 * 不测演示口径的用例统一用「关演示」——否则真实数据会被过滤掉。
 */
internal fun demoOffPrefs(): PreferencesManager =
    mockk { every { lifeDemoMode } returns flowOf(false) }
