package com.palmnote.ui.life

import androidx.compose.ui.unit.dp
import com.palmnote.domain.model.ProgressForm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LifeProgressKitsTest {

    @Test
    fun `segmented fill keeps the current segment proportional`() {
        assertFills(
            expected = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            fraction = 0f
        )
        assertFills(
            expected = floatArrayOf(0.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            fraction = 0.5f / 8f
        )
        assertFills(
            expected = floatArrayOf(1f, 1f, 0.56f, 0f, 0f, 0f, 0f, 0f),
            fraction = 2.56f / 8f
        )
        assertFills(
            expected = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f),
            fraction = 1f
        )
    }

    @Test
    fun `segmented fill clamps invalid fractions`() {
        assertFills(
            expected = floatArrayOf(0f, 0f, 0f, 0f),
            fraction = -1f,
            segments = 4
        )
        assertFills(
            expected = floatArrayOf(1f, 1f, 1f, 1f),
            fraction = 2f,
            segments = 4
        )
        assertFills(
            expected = floatArrayOf(0f, 0f, 0f, 0f),
            fraction = Float.NaN,
            segments = 4
        )
        assertEquals(emptyList<Float>(), segmentFillFractions(0.5f, 0))
    }

    @Test
    fun `segment count follows countable targets with the documented fallback`() {
        assertEquals(30, progressSegmentCount(null))
        assertEquals(30, progressSegmentCount(1.0))
        assertEquals(2, progressSegmentCount(2.0))
        assertEquals(4, progressSegmentCount(4.9))
        assertEquals(60, progressSegmentCount(60.0))
        assertEquals(30, progressSegmentCount(61.0))
    }

    @Test
    fun `ring geometry matches the finalized form table`() {
        assertEquals(170.dp, ringSizeFor(ProgressForm.THICK_RING))
        assertEquals(170.dp, ringSizeFor(ProgressForm.BEADED_RING))
        assertEquals(166.dp, ringSizeFor(ProgressForm.SEGMENTED_RING))
        assertEquals(150.dp, ringSizeFor(ProgressForm.THIN_RING))
        assertEquals(26.dp, ringStrokeWidthFor(ProgressForm.THICK_RING))
        assertEquals(22.dp, ringStrokeWidthFor(ProgressForm.BEADED_RING))
        assertEquals(14.dp, ringStrokeWidthFor(ProgressForm.SEGMENTED_RING))
        assertEquals(7.dp, ringStrokeWidthFor(ProgressForm.THIN_RING))
    }

    private fun assertFills(
        expected: FloatArray,
        fraction: Float,
        segments: Int = 8
    ) {
        assertArrayEquals(expected, segmentFillFractions(fraction, segments).toFloatArray(), 0.001f)
    }
}
