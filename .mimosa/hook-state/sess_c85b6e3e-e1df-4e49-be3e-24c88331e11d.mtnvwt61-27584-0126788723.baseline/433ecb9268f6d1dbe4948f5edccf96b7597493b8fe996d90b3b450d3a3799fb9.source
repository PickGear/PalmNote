package com.palmnote.data.event

import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.event.EventBus
import com.palmnote.domain.model.BillType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventBusImplTest {

    private lateinit var eventBus: EventBus

    @Before
    fun setup() {
        eventBus = EventBusImpl()
    }

    @Test
    fun `publish delivers event to subscriber`() = runTest {
        val events = mutableListOf<DomainEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.toList(events)
        }

        eventBus.publish(DomainEvent.BillCreated(1L, BillType.EXPENSE, 1000L))

        assertEquals(1, events.size)
        assertTrue(events[0] is DomainEvent.BillCreated)
        val billEvent = events[0] as DomainEvent.BillCreated
        assertEquals(1L, billEvent.billId)
        assertEquals(BillType.EXPENSE, billEvent.type)
        assertEquals(1000L, billEvent.amount)

        job.cancel()
    }

    @Test
    fun `publishAll delivers multiple events`() = runTest {
        val events = mutableListOf<DomainEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.toList(events)
        }

        eventBus.publishAll(listOf(
            DomainEvent.BillCreated(1L, BillType.EXPENSE, 100L),
            DomainEvent.BillCreated(2L, BillType.INCOME, 200L),
            DomainEvent.BillDeleted(1L)
        ))

        assertEquals(3, events.size)

        job.cancel()
    }

    @Test
    fun `multiple subscribers receive same event`() = runTest {
        val events1 = mutableListOf<DomainEvent>()
        val events2 = mutableListOf<DomainEvent>()
        val job1 = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.toList(events1)
        }
        val job2 = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.toList(events2)
        }

        eventBus.publish(DomainEvent.AssetCreated(42L))

        assertEquals(1, events1.size)
        assertEquals(1, events2.size)
        assertTrue(events1[0] is DomainEvent.AssetCreated)
        assertTrue(events2[0] is DomainEvent.AssetCreated)

        job1.cancel()
        job2.cancel()
    }
}
