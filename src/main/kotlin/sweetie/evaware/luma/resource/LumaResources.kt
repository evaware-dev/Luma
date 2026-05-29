package sweetie.evaware.luma.resource

object LumaResources {
    private val resources = LinkedHashSet<AutoCloseable>()

    fun <T : AutoCloseable> track(resource: T): T {
        synchronized(resources) {
            resources.add(resource)
        }
        return resource
    }

    fun untrack(resource: AutoCloseable) {
        synchronized(resources) {
            resources.remove(resource)
        }
    }

    fun closeAll() {
        val snapshot = synchronized(resources) {
            val arr = resources.toTypedArray()
            resources.clear()
            arr
        }

        for (index in snapshot.lastIndex downTo 0) {
            try {
                snapshot[index].close()
            } catch (_: Throwable) {
            }
        }
    }
}
