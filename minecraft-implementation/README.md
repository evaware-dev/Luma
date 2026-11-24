# Minecraft Implementation Example

Demonstrates integrating Luma in a Minecraft Fabric mod across both **OpenGL** (`core-gl` with `GlStatePolicy.PRESERVE`) and **Vulkan** (Mojang Blaze3D 26.2 / Renderpearl 26.3).

## Core Concepts

### 1. Unified Render Targets (`MinecraftRenderTargets`)

On OpenGL, draws may target the default bound framebuffer. On **Vulkan**, Blaze3D/Renderpearl requires an explicit `GpuTextureView` render pass target; drawing without an active target throws:

```text
IllegalStateException: No Blaze3D render target is active; call beginRenderTarget before drawing
```

Borrow Minecraft's main framebuffer as a Luma `RenderTargetHandle`:

```kotlin
val mainTarget = MinecraftRenderTargets.borrow(Minecraft.getInstance().gameRenderer.mainRenderTarget())
try {
    RenderUtil.renderToTarget(mainTarget) {
        // Draw HUD / overlays here
    }
} finally {
    mainTarget.close()
}
```

Or use `RenderUtil.renderToMain` / `RenderUtil.renderFrame { ... }`, which borrows and binds the main target automatically across both OpenGL and Vulkan:

```kotlin
RenderUtil.renderToMain {
    RenderUtil.ROUNDED_RECT
        .priority(RenderPipeline.GUI)
        .color(0xFFFFFFFF.toInt())
        .radius(6f)
        .draw(10f, 10f, 100f, 40f)
}
```

### 2. Offscreen Targets, Formats, Samplers & Blitting

Create offscreen render targets with explicit `RenderTargetFormat`, optional depth/stencil attachments, and `SamplerState`:

```kotlin
val target = Luma.backend.createRenderTarget(
    width = width,
    height = height,
    useDepth = true,
    format = RenderTargetFormat.RGBA8,
    sampler = SamplerState.LINEAR_CLAMP
)

RenderUtil.renderFrame {
    RenderUtil.renderToTarget(target, clearColor = floatArrayOf(0f, 0f, 0f, 0f)) {
        // Arbitrary Early-Z clipping works on both OpenGL and Vulkan
        ClipMask.clip(
            mode = ClipMode.INSIDE,
            mask = { drawClipShape() },
            content = { RenderTest.renderGui() }
        )
    }
    // Blit directly to active target or sample target.colorTexture
    Luma.blitRenderTarget(target, destination = null, filter = TextureFilter.LINEAR)
}
```

### 3. Static Geometry & Command Reuse (`PreparedVertices` and `DrawList`)

- **`PreparedVertices`**: Cache immutable vertex buffers for individual shaders (`shader.prepareVertices(...).seal()`).
- **`DrawList`**: Record multi-draw command sequences (state, textures, uniforms, clip masks, render targets) once via `drawList.record { ... }` and replay them at `0 B/op` via `drawList.replay()`. Close the `DrawList` on teardown to release its native vertex buffer.

### 4. Lifecycle & Resource Management

- **Init**: Call `LumaMinecraft.install()` and initialize shared shaders/atlases during mod init (`ModInitializer.onInitialize`).
- **Shutdown / Device Reset**: Hook `Minecraft.close()` and `GpuDevice` recreation to call `RenderUtil.close()`, releasing all GPU buffers, textures, pipelines, and query pools.
