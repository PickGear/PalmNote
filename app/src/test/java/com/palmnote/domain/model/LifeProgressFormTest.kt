package com.palmnote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeProgressFormTest {

    @Test
    fun `continuous progress field exposes the five E04 forms in order`() {
        assertEquals(
            listOf(
                ProgressForm.THICK_CAPSULE,
                ProgressForm.THICK_RING,
                ProgressForm.THIN_RING,
                ProgressForm.SEGMENTED_RING,
                ProgressForm.BEADED_RING
            ),
            FieldContracts.selectableProgressForms(FieldType.CURRENCY)
        )
    }

    @Test
    fun `segmented field exposes only the segmented ring`() {
        assertEquals(
            listOf(ProgressForm.SEGMENTED_RING),
            FieldContracts.selectableProgressForms(FieldType.CHECKLIST)
        )
        assertEquals(
            listOf(ProgressForm.SEGMENTED_RING),
            FieldContracts.selectableProgressForms(FieldType.STREAK)
        )
    }

    @Test
    fun `auto progress defaults single targets to the E04 capsule`() {
        assertEquals(
            ProgressForm.THICK_CAPSULE,
            resolveProgressForm(FieldConfig("saved", "已存金额", FieldType.CURRENCY))
        )
    }

    @Test
    fun `field without progress capability exposes no forms`() {
        assertTrue(FieldContracts.selectableProgressForms(FieldType.TEXT).isEmpty())
    }

    @Test
    fun `P1 ring overrides parse and collapse to list-card projections`() {
        assertEquals(
            ProgressStyleSetting.FIXED(ProgressForm.THIN_RING),
            FieldContract.parseProgressStyle("THIN_RING")
        )
        assertEquals(
            ProgressStyleSetting.FIXED(ProgressForm.BEADED_RING),
            FieldContract.parseProgressStyle("BEADED_RING")
        )
        assertEquals(ProgressForm.THICK_CAPSULE, projectToListCard(ProgressForm.THIN_RING))
        assertEquals(ProgressForm.SEGMENTED_BAR, projectToListCard(ProgressForm.BEADED_RING))
    }

    @Test
    fun `checklist and streak can use progress rendering`() {
        assertTrue(FieldContracts.of(FieldType.CHECKLIST).progressCapable)
        assertTrue(FieldContracts.of(FieldType.STREAK).progressCapable)
        assertEquals(ProgressForm.SEGMENTED_RING, resolveProgressForm(FieldConfig("done", "完成", FieldType.CHECKLIST)))
        assertEquals(ProgressForm.SEGMENTED_RING, resolveProgressForm(FieldConfig("streak", "连续", FieldType.STREAK)))
    }

    @Test
    fun `fixed progress style overrides the semantic default`() {
        assertEquals(
            ProgressForm.BEADED_RING,
            resolveProgressForm(
                FieldConfig(
                    "saved",
                    "已存金额",
                    FieldType.CURRENCY,
                    progressStyle = ProgressForm.BEADED_RING.name
                )
            )
        )
    }
}
