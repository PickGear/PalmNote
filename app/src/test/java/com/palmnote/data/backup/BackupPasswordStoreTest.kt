package com.palmnote.data.backup

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「记住密码」的存储契约。
 *
 * Android Keystore 在 JVM 测试环境中不可用，因此这里不覆盖 save → load 的加密往返
 * （该路径依赖真实 Keystore，只能真机验证）。本测试聚焦真正容易出错的一半：
 * 记录缺失、记录损坏，以及换机/重装后"本机永远解不开"时必须自清 ——
 * 否则残留密文会让开关每次进来都错判成"已记住"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BackupPasswordStoreTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val prefs get() = context.getSharedPreferences(
        BackupPasswordStore.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Test
    fun `load returns null when no password was ever remembered`() {
        assertNull(BackupPasswordStore(context).load())
    }

    @Test
    fun `load clears an undecryptable record so it is not treated as remembered forever`() {
        // 模拟换机/重装后残留的旧密文：Keystore 密钥已随设备更换，本机永远解不开
        prefs.edit().putString(BackupPasswordStore.KEY_NAME, "AAAA").commit()

        assertNull(BackupPasswordStore(context).load())

        assertFalse(prefs.contains(BackupPasswordStore.KEY_NAME))
    }

    @Test
    fun `load clears a record that is not valid base64`() {
        prefs.edit().putString(BackupPasswordStore.KEY_NAME, "not-base64!!!").commit()

        assertNull(BackupPasswordStore(context).load())

        assertFalse(prefs.contains(BackupPasswordStore.KEY_NAME))
    }

    @Test
    fun `save blank clears the remembered record`() {
        prefs.edit().putString(BackupPasswordStore.KEY_NAME, "AAAA").commit()

        BackupPasswordStore(context).save("")

        assertFalse(prefs.contains(BackupPasswordStore.KEY_NAME))
    }

    @Test
    fun `save null clears the remembered record`() {
        prefs.edit().putString(BackupPasswordStore.KEY_NAME, "AAAA").commit()

        BackupPasswordStore(context).save(null)

        assertFalse(prefs.contains(BackupPasswordStore.KEY_NAME))
    }
}
