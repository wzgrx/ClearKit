package io.github.clearkit.starter.hook.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringTest {
    private fun run(damping: Float, velocity: Float = 0f): List<Float> {
        var s = SpringPhysics.State(0f, velocity)
        val out = ArrayList<Float>()
        repeat(240) { s = SpringPhysics.advance(s, 100f, 400f, damping, 1f / 120f); out += s.value }
        return out
    }

    @Test
    fun `critically damped spring reaches the target without overshooting`() {
        val xs = run(1f)
        assertTrue(xs.all { it <= 100.01f })
        assertEquals(100f, xs.last(), 0.5f)
    }

    @Test
    fun `underdamped spring overshoots once and settles`() {
        val xs = run(0.5f)
        assertTrue(xs.maxOrNull()!! > 105f)
        assertEquals(100f, xs.last(), 0.5f)
    }

    @Test
    fun `initial velocity carries the value past where it would start`() {
        val slow = run(1f)
        val fast = run(1f, velocity = 3000f)
        assertTrue(fast[3] > slow[3])
    }

    @Test
    fun `projection follows the fling direction`() {
        assertTrue(SpringPhysics.project(500f, 2000f) > 600f)
        assertTrue(SpringPhysics.project(500f, -2000f) < 400f)
        assertEquals(500f, SpringPhysics.project(500f, 0f), 0f)
    }

    @Test
    fun `spring interpolator starts at 0 and ends exactly at 1`() {
        for (d in listOf(1f, 0.7f, 0.5f)) {
            val i = SpringInterpolator(d)
            assertEquals(0f, i.getInterpolation(0f), 1e-4f)
            assertEquals(1f, i.getInterpolation(1f), 0f)
            assertTrue(i.getInterpolation(0.5f) > 0.5f)
        }
        // 欠阻尼时中途会冲过 1
        val bouncy = SpringInterpolator(0.5f)
        assertTrue((1..99).map { bouncy.getInterpolation(it / 100f) }.maxOrNull()!! > 1f)
        // 临界阻尼不会
        val calm = SpringInterpolator(1f)
        assertTrue((1..99).all { calm.getInterpolation(it / 100f) <= 1f })
    }
}
