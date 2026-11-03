package com.passportphoto.worldwide

import com.passportphoto.worldwide.imaging.Geometry
import com.passportphoto.worldwide.requirements.Requirements
import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    @Test
    fun cropPreservesRatioAndStaysInsideSource() {
        for (ratio in listOf(1f, 35f / 45, 50f / 70)) for (zoom in listOf(1f, 2f, 6f)) for (x in
            listOf(-3f, 0f, 3f)) {
            val crop = Geometry.crop(4000, 3000, ratio, zoom, x, -x)
            assertEquals(ratio, crop.width / crop.height, .0001f)
            assertTrue(crop.left >= 0 && crop.top >= 0)
            assertTrue(crop.left + crop.width <= 4000.01f && crop.top + crop.height <= 3000.01f)
        }
    }

    @Test
    fun printDimensionsUsePhysicalUnits() {
        assertEquals(144f, Geometry.points(50.8f), .001f)
        assertEquals(600, Geometry.pixels(50.8f))
        val cells = Geometry.sheet(101.6f, 152.4f, 35f, 45f)
        // Three 45 mm rows plus two 3 mm gaps need 141 mm; the printable height is 140.4 mm.
        assertEquals(4, cells.size)
        cells.forEach {
            assertEquals(35f, it.width, 0f)
            assertEquals(45f, it.height, 0f)
            assertTrue(it.left + it.width <= 95.6f)
            assertTrue(it.top + it.height <= 146.4f)
        }
    }

    @Test
    fun oversizedPhotosDoNotOverflowPage() {
        assertTrue(Geometry.sheet(101.6f, 152.4f, 100f, 150f).isEmpty())
    }

    @Test
    fun countriesAreUniqueSearchableAndKnown() {
        assertTrue(Requirements.countries.size > 200)
        assertEquals("United States", Requirements.country("US").name)
        assertEquals(
            Requirements.countries.size,
            Requirements.countries.map { it.code }.toSet().size,
        )
    }

    @Test
    fun unverifiedDocumentsNeverInheritPassportMeasurementsAsVerified() {
        assertFalse(Requirements.get("US", "Visa").verified)
        assertFalse(Requirements.get("DE", "Passport").verified)
        assertTrue(Requirements.get("US", "Passport").verified)
        assertEquals(50.8f, Requirements.get("US", "Passport").widthMm, 0f)
    }

    @Test
    fun digitalOriginalRestrictionsAreExplicit() {
        assertTrue(Requirements.get("GB", "Passport").originalOnly)
        assertEquals(600, Requirements.get("GB", "Passport").minWidth)
    }
}
