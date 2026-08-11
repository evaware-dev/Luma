package sweetie.evaware.luma.tools

class LifetimeStats internal constructor() {
    var frames = 0L
        internal set
    var drawCalls = 0L
        internal set
    var vertices = 0L
        internal set
    var instances = 0L
        internal set
    var primitives = 0L
        internal set
    var textureUploads = 0L
        internal set
    var uploadedBytes = 0L
        internal set
    var programsCreated = 0L
        internal set
    var texturesCreated = 0L
        internal set
    var renderTargetsCreated = 0L
        internal set
}
