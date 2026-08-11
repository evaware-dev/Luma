package sweetie.evaware.luma.tools

class FrameStats internal constructor() {
    var frameIndex = 0L
        private set
    var cpuNanos = 0L
        private set
    var drawCalls = 0L
        private set
    var vertices = 0L
        private set
    var instances = 0L
        private set
    var primitives = 0L
        private set
    var programChanges = 0L
        private set
    var textureBindCalls = 0L
        private set
    var textureBindingChanges = 0L
        private set
    var renderTargetPasses = 0L
        private set
    var textureUploads = 0L
        private set
    var uploadedBytes = 0L
        private set
    var programsCreated = 0L
        private set
    var texturesCreated = 0L
        private set
    var renderTargetsCreated = 0L
        private set

    internal fun reset(index: Long) {
        frameIndex = index
        cpuNanos = 0L
        drawCalls = 0L
        vertices = 0L
        instances = 0L
        primitives = 0L
        programChanges = 0L
        textureBindCalls = 0L
        textureBindingChanges = 0L
        renderTargetPasses = 0L
        textureUploads = 0L
        uploadedBytes = 0L
        programsCreated = 0L
        texturesCreated = 0L
        renderTargetsCreated = 0L
    }

    internal fun copyFrom(source: FrameStats) {
        frameIndex = source.frameIndex
        cpuNanos = source.cpuNanos
        drawCalls = source.drawCalls
        vertices = source.vertices
        instances = source.instances
        primitives = source.primitives
        programChanges = source.programChanges
        textureBindCalls = source.textureBindCalls
        textureBindingChanges = source.textureBindingChanges
        renderTargetPasses = source.renderTargetPasses
        textureUploads = source.textureUploads
        uploadedBytes = source.uploadedBytes
        programsCreated = source.programsCreated
        texturesCreated = source.texturesCreated
        renderTargetsCreated = source.renderTargetsCreated
    }

    internal fun finish(nanos: Long) {
        cpuNanos = nanos
    }

    internal fun draw(vertices: Long, instances: Long, primitives: Long, pipelineChanged: Boolean) {
        drawCalls++
        this.vertices += vertices
        this.instances += instances
        this.primitives += primitives
        if (pipelineChanged) programChanges++
    }

    internal fun textureBind(changed: Boolean) {
        textureBindCalls++
        if (changed) textureBindingChanges++
    }

    internal fun renderTargetPass() {
        renderTargetPasses++
    }

    internal fun textureUpload(bytes: Long) {
        textureUploads++
        uploadedBytes += bytes
    }

    internal fun programCreated() {
        programsCreated++
    }

    internal fun textureCreated() {
        texturesCreated++
    }

    internal fun renderTargetCreated() {
        renderTargetsCreated++
    }
}
