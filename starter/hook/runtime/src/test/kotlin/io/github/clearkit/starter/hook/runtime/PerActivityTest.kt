package io.github.clearkit.starter.hook.runtime

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PerActivityTest {
    @Test
    fun valuesAreDisposedAndRemovedWhenTheActivityIsDestroyed() {
        val disposed = ArrayList<String>()
        val holder = PerActivity<String> { disposed += it }
        val a = Activity()
        val b = Activity()
        holder[a] = "a"
        holder[b] = "b"
        HostActivities.dispatchDestroyed(a)
        assertNull(holder[a])
        assertEquals("b", holder[b])
        assertEquals(listOf("a"), disposed)
        assertEquals(listOf(b), holder.activities)
        HostActivities.dispatchDestroyed(b)
    }

    @Test
    fun removeDoesNotDispose() {
        val disposed = ArrayList<String>()
        val holder = PerActivity<String> { disposed += it }
        val a = Activity()
        holder[a] = "a"
        assertEquals("a", holder.remove(a))
        HostActivities.dispatchDestroyed(a)
        assertTrue(disposed.isEmpty())
    }

    @Test
    fun subscriptionsAreUndoneAndResumedIsForgottenOnDestroy() {
        val seen = ArrayList<Activity>()
        val undo = HostActivities.onResumed { seen += it }
        val a = Activity()
        HostActivities.dispatchResumed(a)
        assertSame(a, HostActivities.resumed())
        undo()
        HostActivities.dispatchResumed(Activity())
        assertEquals(listOf(a), seen)
        val last = HostActivities.resumed()!!
        HostActivities.dispatchDestroyed(last)
        assertNull(HostActivities.resumed())
    }

    @Test
    fun aFailingListenerDoesNotStopTheOthers() {
        val seen = ArrayList<Activity>()
        val u1 = HostActivities.onCreated { error("bad") }
        val u2 = HostActivities.onCreated { seen += it }
        val a = Activity()
        HostActivities.dispatchCreated(a)
        assertEquals(listOf(a), seen)
        u1(); u2()
    }
}
