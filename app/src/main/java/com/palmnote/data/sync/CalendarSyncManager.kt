package com.palmnote.data.sync

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.palmnote.data.db.entity.Anniversary
import com.palmnote.data.repository.AnniversaryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val anniversaryRepository: AnniversaryRepository
) {
    companion object {
        const val CALENDAR_NAME = "掌记"
        const val CALENDAR_ACCOUNT = "com.palmnote.sync"
    }

    private fun getOrCreateCalendarId(): Long? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.NAME
        )
        val selection = "${CalendarContract.Calendars.NAME} = ?"
        val cursor = resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, selection, arrayOf(CALENDAR_NAME), null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(it.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
            }
        }

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "掌记同步")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4A7A5E.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, CALENDAR_ACCOUNT)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val uri = resolver.insert(
            CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build(),
            values
        )
        return uri?.lastPathSegment?.toLongOrNull()
    }

    suspend fun syncAnniversaries(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val calendarId = getOrCreateCalendarId() ?: return@withContext Result.failure(Exception("无法创建日历"))
            val resolver = context.contentResolver
            val anniversaries = anniversaryRepository.getAllAnniversaries().first().filter { !it.isDeleted && it.isYearly }

            deleteExistingEvents(resolver, calendarId)
            var count = 0
            for (anniv in anniversaries) {
                createAnniversaryEvent(resolver, calendarId, anniv)
                count++
            }
            Result.success(count)
        } catch (e: SecurityException) {
            Result.failure(Exception("缺少日历权限"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearSync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val calendarId = getOrCreateCalendarId()
            if (calendarId != null) {
                deleteExistingEvents(context.contentResolver, calendarId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deleteExistingEvents(resolver: ContentResolver, calendarId: Long) {
        resolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CALENDAR_ID} = ?",
            arrayOf(calendarId.toString())
        )
    }

    private fun createAnniversaryEvent(resolver: ContentResolver, calendarId: Long, anniv: Anniversary) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = anniv.solarDate
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            add(Calendar.HOUR_OF_DAY, 1)
        }
        val tz = TimeZone.getDefault().id

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, anniv.title.ifEmpty { "纪念日" })
            put(CalendarContract.Events.DESCRIPTION, buildString {
                if (anniv.description.isNotEmpty()) append(anniv.description).append("\n")
                if (anniv.personName.isNotEmpty()) append("人物: ${anniv.personName}")
            })
            put(CalendarContract.Events.DTSTART, cal.timeInMillis)
            put(CalendarContract.Events.DTEND, endCal.timeInMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, tz)
            if (anniv.isYearly) {
                put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
            }
        }
        resolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }
}
