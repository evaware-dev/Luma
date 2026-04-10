package sweetie.evaware.luma.resource

object LumaResources {
    fun <T : AutoCloseable> track(resource: T): T =
        sweetie.evaware.luma.wrapper.resource.LumaResources.track(resource)

    fun untrack(resource: AutoCloseable) =
        sweetie.evaware.luma.wrapper.resource.LumaResources.untrack(resource)

    fun closeAll() = sweetie.evaware.luma.wrapper.resource.LumaResources.closeAll()
}
