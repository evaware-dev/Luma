# Developer tools

The optional `developer-tools` artifact exposes non-allocating backend decorators and profiling diagnostics without adding overhead to the core runtime.

## Setup

Wrap any `RenderBackend` (e.g. `Backend` in `core-gl` or `vulkan-blaze3d-wrapper`) with `StatsBackend`:

```kotlin
val glBackend = Backend()
val stats = StatsBackend(glBackend)
Luma.backend = stats

Luma.render {
    scene.render()
}
```

## Zero-Allocation Metrics (`FrameStats`)

`currentFrame`, `lastFrame`, and `lifetime` are reused mutable snapshots. Reading them does not allocate per frame; retaining `lastFrame` retains the live snapshot rather than an immutable historical copy.

```kotlin
val frame = stats.lastFrame
val total = stats.lifetime

println("Frame #${frame.frameIndex} took ${frame.cpuNanos} ns")
println("Draw calls: ${frame.drawCalls}, instances: ${frame.instances}, primitives: ${frame.primitives}")
println("Program changes: ${frame.programChanges}")
println("Texture binds: ${frame.textureBindCalls} (${frame.textureBindingChanges} state changes)")
println("Render target passes: ${frame.renderTargetPasses}")
println("Texture uploads: ${frame.textureUploads} (${frame.uploadedBytes} bytes)")
```

### Tracked Metrics

| Property | Description |
|---|---|
| `frameIndex` | Incremental frame sequence number |
| `cpuNanos` | CPU-side submission duration for the frame |
| `drawCalls` | Total draw invocations (`draw`, `drawIndexed`) |
| `vertices` | Total number of vertices submitted |
| `instances` | Total number of instances rendered |
| `primitives` | Total primitive count drawn |
| `programChanges` | Pipeline and shader program switches |
| `textureBindCalls` | Total texture bind invocations |
| `textureBindingChanges` | Texture bind calls that resulted in a hardware/cache state switch |
| `renderTargetPasses` | Offscreen and framebuffer pass transitions |
| `textureUploads` | Number of texture buffer uploads |
| `uploadedBytes` | Total bytes uploaded to GPU textures |
| `programsCreated` | Number of shader programs compiled and created |
| `texturesCreated` | Number of GPU texture handles allocated |
| `renderTargetsCreated` | Number of framebuffer/render-target objects allocated |

## Backend Passthrough

Keep a reference to the concrete backend when using backend-specific features. For example, raw OpenGL can be isolated with `glBackend.externalGl { ... }` while statistics continue through the decorator.
