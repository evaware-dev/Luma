package sweetie.evaware.renderutil.api

interface IBatch {
    fun hasPending(): Boolean
    fun flush()
}
