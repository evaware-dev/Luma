package sweetie.evaware.luma.api

interface CloseableResource : AutoCloseable {
    val isClosed: Boolean

    fun requireOpen()
}

abstract class CloseableResourceBase : CloseableResource {
    final override var isClosed = false
        private set

    protected fun markClosed(): Boolean {
        if (isClosed) return false
        isClosed = true
        return true
    }

    final override fun requireOpen() {
        check(!isClosed) { "Resource is closed" }
    }

    protected fun reopenResource() {
        isClosed = false
    }
}
