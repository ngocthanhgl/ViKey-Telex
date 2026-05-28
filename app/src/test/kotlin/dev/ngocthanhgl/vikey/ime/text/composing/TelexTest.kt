package dev.ngocthanhgl.vikey.ime.text.composing

import org.junit.Test
import org.junit.Assert.assertEquals

class TelexTest {
    private val telex = AlgorithmicTelex()

    private fun simulate(sequence: String): String {
        var current = ""
        for (char in sequence) {
            val (rm, replacement) = telex.getActions(current, char.toString())
            current = current.dropLast(rm) + replacement
        }
        return current
    }

    @Test
    fun testBasicVowels() {
        assertEquals("â", simulate("aa"))
        assertEquals("ă", simulate("aw"))
        assertEquals("ê", simulate("ee"))
        assertEquals("ô", simulate("oo"))
        assertEquals("ơ", simulate("ow"))
        assertEquals("ư", simulate("uw"))
        assertEquals("đ", simulate("dd"))
    }

    @Test
    fun testComplexVowels() {
        assertEquals("ươ", simulate("uow"))
    }

    @Test
    fun testTones() {
        assertEquals("á", simulate("as"))
        assertEquals("à", simulate("af"))
        assertEquals("ả", simulate("ar"))
        assertEquals("ã", simulate("ax"))
        assertEquals("ạ", simulate("aj"))
    }

    @Test
    fun testTonesOnClusters() {
        assertEquals("ướ", simulate("uows"))
        assertEquals("ứ", simulate("ws"))
    }

    @Test
    fun testToneCancellation() {
        assertEquals("a", simulate("asz"))
        assertEquals("Tại", simulate("Taji"))
    }

    @Test
    fun testCaseSensitivity() {
        assertEquals("Â", simulate("AA"))
        assertEquals("Ă", simulate("AW"))
        assertEquals("W", simulate("WW")) // Standalone W then W undoes to W
    }

    @Test
    fun testSpaceAndTones() {
        assertEquals("Tại s", simulate("Taji s"))
        assertEquals("Tại z", simulate("Taji z"))
    }

    @Test
    fun testDoubleW() {
        assertEquals("ư", simulate("w"))
        assertEquals("w", simulate("ww"))
        assertEquals("ưw", simulate("w" + " " + "w")) // space in between
    }

    @Test
    fun testStandaloneW() {
        assertEquals("ư", simulate("w"))
        assertEquals("kư", simulate("kw"))
        assertEquals("ă", simulate("aw"))
    }

    @Test
    fun testToneCancelMultiChar() {
        assertEquals("floris", simulate("florriss"))
    }

    @Test
    fun testDoubleWCancelMultiChar() {
        assertEquals("polkw", simulate("polkww"))
        assertEquals("polkww", simulate("polkwww"))
    }
}
