package io.github.clearkit.starter.hook.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartTrackerTest {
    private val values = HashMap<String, Any?>(mapOf("a" to false, "b" to 1))
    private val tracker = RestartTracker()

    /** 模拟一次写入：先记原值、写入、再比较。 */
    private fun write(vararg changes: Pair<String, Any?>) {
        val keys = changes.map { it.first }
        tracker.before(keys, values::get)
        changes.forEach { (k, v) -> values[k] = v }
        tracker.after(keys, values::get)
    }

    @Test
    fun changedValueIsPendingAndChangingBackClearsIt() {
        write("a" to true)
        assertEquals(setOf("a"), tracker.pending)
        write("a" to false)
        assertTrue(tracker.pending.isEmpty())
    }

    @Test
    fun batchThenRollbackClearsTheBar() {
        // 批量改两项，再回滚到原值（之前批量 / 回滚不记原值，提示条清不掉）。
        write("a" to true, "b" to 2)
        assertEquals(setOf("a", "b"), tracker.pending)
        write("a" to false, "b" to 1)
        assertTrue(tracker.pending.isEmpty())
    }

    @Test
    fun baselineIsTheValueBeforeTheFirstChange() {
        write("b" to 2)
        write("b" to 3)
        assertEquals(setOf("b"), tracker.pending)
        write("b" to 1)
        assertTrue(tracker.pending.isEmpty())
    }
}
