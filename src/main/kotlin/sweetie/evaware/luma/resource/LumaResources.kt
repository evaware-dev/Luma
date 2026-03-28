package sweetie.evaware.luma.resource

object LumaResources {
    private val resources = LinkedHashSet<AutoCloseable>()

    fun <T : AutoCloseable> track(resource: T): T {
        resources += resource
        return resource
    }

    fun untrack(resource: AutoCloseable) {
        resources.remove(resource)
    }

    fun closeAll() {
        val snapshot = resources.toTypedArray()
        resources.clear()

        for (index in snapshot.lastIndex downTo 0) {
            try {
                snapshot[index].close()
            } catch (_: Throwable) {
            }
        }
    }
}
