# Developer Tools

The optional `developer-tools` artifact provides a non-allocating `StatsBackend` decorator and opt-in non-blocking GPU timestamp profiling with zero overhead when unused.

## Setup

Wrap any `RenderBackend` (`core-gl` or `vulkan-blaze3d-wrapper`) with `StatsBackend`:

```kotlin
val glBackend = Backend()
val stats = StatsBackend(glBackend, gpuTimers = false)
Luma.backend = stats

Luma.render {
    Luma.debugGroup("Scene") {
        scene.render()
    }
}
```

## Debug Groups

`Luma.debugGroup(label) { ... }` (or `pushDebugGroup` / `popDebugGroup`) emits native GPU profiler markers:

- **OpenGL**: `GL43.glPushDebugGroup` / `GL_KHR_debug` (cached capability check; no-op if unsupported).
- **Vulkan (Blaze3D / Renderpearl)**: `RenderPass.pushDebugGroup` / `popDebugGroup`, preserved cleanly across render-pass boundaries.

## Zero-Allocation CPU Metrics (`FrameStats`)

`currentFrame`, `lastFrame`, and `lifetime` are reused mutable snapshots. Reading them performs no per-frame allocation.

```kotlin
val frame = stats.lastFrame
val total = stats.lifetime

println("Frame #${frame.frameIndex}: cpu=${frame.cpuNanos} ns, gpu=${frame.gpuFrameNanos} ns")
println("Draws: ${frame.drawCalls}, instances: ${frame.instances}, primitives: ${frame.primitives}")
println("Program changes: ${frame.programChanges}")
println("Texture binds: ${frame.textureBindCalls} (${frame.textureBindingChanges} switches)")
println("Render target passes: ${frame.renderTargetPasses}")
println("Texture uploads: ${frame.textureUploads} (${frame.uploadedBytes} bytes)")
```

### Tracked Properties

| Property | Description |
|---|---|
| `frameIndex` | Incremental frame sequence number |
| `cpuNanos` | CPU-side submission duration for the frame |
| `gpuFrameNanos` | Most recently completed asynchronous GPU frame duration (`0` when `gpuTimers = false`) |
| `drawCalls` | Total draw invocations (`draw`, `drawIndexed`) |
| `vertices` | Total vertices submitted |
| `instances` | Total instances rendered |
| `primitives` | Total primitives drawn |
| `programChanges` | Shader program switches across draws |
| `textureBindCalls` | Total `bindTexture` invocations |
| `textureBindingChanges` | `bindTexture` calls that changed the bound handle on a unit |
| `renderTargetPasses` | `beginRenderTarget` pass transitions |
| `textureUploads` | Number of texture uploads (`createTexture` with data and `updateTexture`) |
| `uploadedBytes` | Total bytes uploaded to textures (accounted by `TextureFormat.bytesPerPixel`) |
| `programsCreated` | Programs created |
| `texturesCreated` | Textures created |
| `renderTargetsCreated` | Render targets created |

## Opt-In Non-Blocking GPU Timers

Pass `gpuTimers = true` to enable hardware GPU timestamps:

- **OpenGL**: `GL33.glQueryCounter(GL_TIMESTAMP)` (or `GL_ARB_timer_query`), polled via `GL_QUERY_RESULT_AVAILABLE`.
- **Vulkan (Blaze3D 26.2 & Renderpearl 26.3)**: `GpuDevice.createTimestampQueryPool` + `CommandEncoder.writeTimestamp` / `RenderPass.writeTimestamp`, scaled by `DeviceInfo.timestampPeriod()` and polled via non-blocking `OptionalLong`.
- **4-frame ring buffer**: Each in-flight frame uses its own query pool so the CPU never stalls waiting for the current frame and never overwrites an in-flight pool. If the GPU falls more than 4 frames behind, new frames skip timing until a slot completes.
- **Automatic zone bracketing**: Frames and nested `debugGroup(label)` scopes are automatically bracketed and published into reused `GpuZoneTiming` instances with zero steady-state allocations.

```kotlin
val stats = StatsBackend(backend, gpuTimers = true)
Luma.backend = stats

// After rendering frames (or call stats.pollGpuTimers() explicitly):
println("Completed GPU frame #${stats.lastGpuFrameIndex}: ${stats.lastGpuFrameNanos} ns")
for (i in 0 until stats.gpuZoneCount) {
    val zone = stats.gpuZones[i]
    println("${"  ".repeat(zone.depth)}${zone.name}: ${zone.nanos} ns")
}
```

### Teardown

- `stats.close()` frees all GPU timestamp pools first and then closes `delegate`.
- `stats.closeGpuTimers()` frees only the GPU timestamp pools without closing `delegate` (useful when detaching `StatsBackend` from a long-lived backend).

## Backend Passthrough

Keep a reference to the concrete backend for backend-specific escape hatches (for example `glBackend.externalGl { ... }`) while metrics continue flowing through `StatsBackend`.
