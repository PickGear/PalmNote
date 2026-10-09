package com.palmnote.ui.widget

import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.Goal
import com.palmnote.data.repository.GoalRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 桌面打卡核心动作（[HabitCheckInReceiver.checkIn]）跑在真实 [GoalRepositoryImpl] + in-memory Room 上。
 *
 * 1.4.0 changelog 明确「小组件数据与交互未完成」——本测试守住桌面打卡入口的写库口径：
 * 首次打卡写记录 + 累计进度 + streak 回写；当日重复打卡返回 -1 且不重复计数。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class HabitCheckInReceiverTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: GoalRepositoryImpl
    private var goalId: Long = 0L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = GoalRepositoryImpl(db.goalDao(), db.goalCheckInDao(), db)
        goalId = runBlocking {
            db.goalDao().insertGoal(Goal(title = "跑步", goalType = "HABIT", totalCount = 30))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `首次打卡写入记录并累计进度`() = runBlocking {
        val checkInId = HabitCheckInReceiver.checkIn(repo, goalId)

        assertTrue("首次打卡应返回真实记录 id", checkInId > 0)
        assertEquals(1, db.goalDao().getGoalById(goalId)!!.currentCount)
        assertEquals(1, db.goalCheckInDao().getCheckInsByGoalOnce(goalId).size)
        assertEquals(1, db.goalDao().getGoalById(goalId)!!.streak)
    }

    @Test
    fun `当日重复打卡返回-1且不重复计数`() = runBlocking {
        HabitCheckInReceiver.checkIn(repo, goalId)

        val second = HabitCheckInReceiver.checkIn(repo, goalId)

        assertEquals(-1L, second)
        assertEquals(1, db.goalDao().getGoalById(goalId)!!.currentCount)
        assertEquals(1, db.goalCheckInDao().getCheckInsByGoalOnce(goalId).size)
    }

    @Test
    fun `多次打卡进度按次数累计`() = runBlocking {
        HabitCheckInReceiver.checkIn(repo, goalId)
        // 模拟次日打卡：改系统时间的口径太重，这里直接把已有记录的日期改成昨天，
        // 让第二次 checkIn 的"今天"成为新的一天
        val yesterday = db.goalCheckInDao().getCheckInsByGoalOnce(goalId).single()
            .copy(
                date = java.time.LocalDate.now().minusDays(1)
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        db.goalCheckInDao().deleteAllByGoalId(goalId)
        db.goalCheckInDao().insertCheckIn(yesterday)

        val next = HabitCheckInReceiver.checkIn(repo, goalId)

        assertTrue(next > 0)
        assertEquals(2, db.goalDao().getGoalById(goalId)!!.currentCount)
        assertEquals(2, db.goalDao().getGoalById(goalId)!!.streak)
    }
}
