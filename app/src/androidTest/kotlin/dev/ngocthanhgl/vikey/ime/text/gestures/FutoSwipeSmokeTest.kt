/*
 * Copyright (C) 2026 ViKey Telex contributors
 *
 * Licensed under the GNU General Public License v3.0 or later.
 */

package dev.ngocthanhgl.vikey.ime.text.gestures

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FutoSwipeSmokeTest {

    private val qwerty = mapOf(
        'q' to (0.0f to 0.0f), 'w' to (0.166f to 0.0f), 'e' to (0.222f to 0.0f),
        'r' to (0.333f to 0.0f), 't' to (0.444f to 0.0f), 'y' to (0.5f to 0.0f),
        'u' to (0.666f to 0.0f), 'i' to (0.777f to 0.0f), 'o' to (0.888f to 0.0f),
        'p' to (1.0f to 0.0f),
        'a' to (0.055f to 0.5f), 's' to (0.166f to 0.5f), 'd' to (0.277f to 0.5f),
        'f' to (0.389f to 0.5f), 'g' to (0.5f to 0.5f), 'h' to (0.611f to 0.5f),
        'j' to (0.722f to 0.5f), 'k' to (0.833f to 0.5f), 'l' to (0.944f to 0.5f),
        'z' to (0.111f to 1.0f), 'x' to (0.277f to 1.0f), 'c' to (0.389f to 1.0f),
        'v' to (0.5f to 1.0f), 'b' to (0.611f to 1.0f), 'n' to (0.722f to 1.0f),
        'm' to (0.833f to 1.0f),
    )

    @Test
    fun recognizesSyntheticHello() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = FutoSwipeEngine(context)
        try {
            assertTrue("FUTO engine did not load", engine.awaitReady())
            engine.setLayout(
                FutoSwipeEngine.Layout(
                    letters = "abcdefghijklmnopqrstuvwxyz",
                    xs = FloatArray(26) { qwerty.getValue('a' + it).first },
                    ys = FloatArray(26) { qwerty.getValue('a' + it).second },
                )
            )

            val word = "hello"
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            val ts = ArrayList<Float>()
            var time = 0f
            var previous = qwerty.getValue(word.first())
            word.forEach { letter ->
                val target = qwerty.getValue(letter)
                for (step in 1..8) {
                    val f = step / 8f
                    xs.add(previous.first + (target.first - previous.first) * f)
                    ys.add(previous.second + (target.second - previous.second) * f)
                    ts.add(time)
                    time += 16.7f
                }
                previous = target
            }

            val words = engine.recognize(
                xs.toFloatArray(),
                ys.toFloatArray(),
                ts.toFloatArray(),
                emptyList(),
                8,
            )
            println("FUTO smoke result: $words timing=${engine.lastTiming()}")
            assertTrue("No candidates", words.isNotEmpty())
        } finally {
            engine.close()
        }
    }
}
