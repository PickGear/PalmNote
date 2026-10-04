package com.palmnote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackImportTest {

    @Test
    fun `gpx import keeps points and calculates stats`() {
        val gpx = """
            <gpx><trk><trkseg>
              <trkpt lat="31.2304" lon="121.4737"><ele>10</ele><time>2026-09-24T01:00:00Z</time></trkpt>
              <trkpt lat="31.2314" lon="121.4747"><ele>18</ele><time>2026-09-24T01:10:00Z</time></trkpt>
              <trkpt lat="31.2324" lon="121.4757"><ele>4</ele><time>2026-09-24T01:20:00Z</time></trkpt>
            </trkseg></trk></gpx>
        """.trimIndent()

        val result = importTrack(gpx)
        assertTrue("result=$result", result is TrackImportResult.Success)
        val track = (result as TrackImportResult.Success).track
        assertEquals("points=${track.points}", 3, track.points.size)
        assertTrue("dist=${track.stats.distM}", track.stats.distM > 200)
        assertEquals("stats=${track.stats}", 1_200L, track.stats.durSec)
        assertTrue("ascent=${track.stats.ascentM}", track.stats.ascentM > 0)
        assertTrue("descent=${track.stats.descentM}", track.stats.descentM > 0)
    }

    @Test
    fun `kml overlay is rejected`() {
        val kml = """
            <kml><Document><GroundOverlay><name>layer</name></GroundOverlay>
            <Placemark><LineString><coordinates>121.47,31.23 121.48,31.24</coordinates></LineString></Placemark>
            </Document></kml>
        """.trimIndent()

        assertEquals(
            TrackImportFailure.UNSUPPORTED_KML_OVERLAY,
            (importTrack(kml) as TrackImportResult.Failure).reason
        )
    }

    @Test
    fun `empty track is rejected`() {
        assertEquals(
            TrackImportFailure.EMPTY,
            (importTrack("<gpx><trk><trkseg></trkseg></trk></gpx>") as TrackImportResult.Failure).reason
        )
    }

    @Test
    fun `jump point is removed`() {
        val raw = listOf(
            MapTrackPoint(31.2300, 121.4700),
            MapTrackPoint(48.8566, 2.3522),
            MapTrackPoint(31.2301, 121.4701)
        )

        val track = buildImportedTrack(raw)
        assertEquals(2, track.points.size)
    }

    @Test
    fun `simplification never exceeds 300 points`() {
        val raw = (0 until 2_000).map {
            MapTrackPoint(31.0 + it * 0.0001, 121.0 + it * 0.0001)
        }

        val track = buildImportedTrack(raw)
        assertTrue(track.points.size <= 300)
    }
}
