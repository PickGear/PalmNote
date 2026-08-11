package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Goal indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_goal_type_deleted ON goals(goalType, isDeleted)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_goal_category ON goals(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_goal_deleted ON goals(isDeleted)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_goal_deadline ON goals(deadline)")

        // GoalCheckIn indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_checkin_goal ON goal_check_ins(goalId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_checkin_goal_date ON goal_check_ins(goalId, date)")

        // LifeTemplate indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_template_category ON life_templates(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_template_deleted ON life_templates(isDeleted)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_template_visible ON life_templates(isHidden, isDeleted)")

        // TodoItem indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_todo_status ON todo_items(isDeleted, isCompleted)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_todo_due ON todo_items(dueDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_todo_plan ON todo_items(planId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_todo_life_item ON todo_items(lifeItemId)")

        // LifeMoment indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_moment_date ON life_moments(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_moment_deleted ON life_moments(isDeleted)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_moment_life_item ON life_moments(lifeItemId)")

        // MoodDiary indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mood_date ON mood_diaries(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mood_life_item ON mood_diaries(lifeItemId)")
    }
}
