package io.github.clearkit.starter.hook.runtime

import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads fields by name from host model objects (Gson models keep their field names). Lookups walk
 * the class hierarchy once and are cached; missing fields read as null instead of throwing.
 */
object Reflect {
    private val cache = ConcurrentHashMap<Pair<Class<*>, String>, Any>()
    private val NONE = Any()

    fun field(cls: Class<*>, name: String): Field? {
        val hit = cache.getOrPut(cls to name) {
            var c: Class<*>? = cls
            var found: Field? = null
            while (c != null && c != Any::class.java && found == null) {
                found = runCatching { c.getDeclaredField(name) }.getOrNull()
                c = c.superclass
            }
            found?.apply { isAccessible = true } ?: NONE
        }
        return hit as? Field
    }

    fun get(obj: Any?, name: String): Any? = obj?.let { o -> field(o.javaClass, name)?.let { runCatching { it.get(o) }.getOrNull() } }

    /** First non-null value among [names]. */
    fun first(obj: Any?, vararg names: String): Any? = names.firstNotNullOfOrNull { get(obj, it) }

    fun string(obj: Any?, vararg names: String): String? =
        names.firstNotNullOfOrNull { (get(obj, it) as? CharSequence)?.toString()?.takeIf(String::isNotBlank) }

    fun long(obj: Any?, vararg names: String): Long? = names.firstNotNullOfOrNull { (get(obj, it) as? Number)?.toLong() }

    fun list(obj: Any?, name: String): List<Any?> = when (val v = get(obj, name)) {
        is List<*> -> v
        is Array<*> -> v.toList()
        else -> emptyList()
    }
}
