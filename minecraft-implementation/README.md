# Minecraft Implementation Example

This module demonstrates how to properly integrate and use Luma in a Minecraft Fabric mod across both **OpenGL** and **Vulkan** (via Mojang Blaze3D) graphics backends.

## Core Concepts

### 1. Unified Render Targets (`MinecraftRenderTargets`)

On OpenGL, draw calls can target the implicit default/bound framebuffer. On **Vulkan**, however, Blaze3D requires an explicit `GpuTextureView` render pass target. Drawing without an active render target will fail with:

```text
IllegalStateException: No Blaze3D render target is active; call beginRenderTarget before drawing
```

To render directly to the Minecraft window/HUD:

```kotlin
// Borrow Minecraft's main framebuffer as a Luma RenderTargetHandle
val mainTarget = MinecraftRenderTargets.borrow(Minecraft.getInstance().gameRenderer.mainRenderTarget())
try {
    RenderUtil.renderToTarget(mainTarget) {
        // Draw HUD / overlays here
    }
} finally {
    mainTarget.close()
}
```

Or simply use `RenderUtil`:

```kotlin
RenderUtil.renderToMain {
    RenderUtil.ROUNDED_RECT
        .priority(RenderPipeline.GUI)
        .color(0xFFFFFFFF.toInt())
        .radius(6f)
        .draw(10f, 10f, 100f, 40f)
}
```

When using `RenderUtil.renderFrame { ... }`, the main target is automatically borrowed and bound for the duration of the frame, ensuring seamless compatibility across both OpenGL and Vulkan.

---

### 2. Offscreen Rendering

Create and render into custom render targets, then present or sample them:

```kotlin
val target = RenderUtil.createRenderTarget(width, height)

RenderUtil.renderFrame {
    // 1. Render to offscreen target
    RenderUtil.renderToTarget(target, clearColor = floatArrayOf(0f, 0f, 0f, 0f)) {
        RenderTest.renderGui()
    }
}

RenderUtil.renderFrame {
    // 2. Sample and present offscreen texture to the main screen
    shader.attach()
    Luma.bindTexture(target.colorTexture, 0)
    shader.draw(preparedQuad)
}
```

---

### 3. Geometry Caching (`PreparedVertices`)

For geometry that does not change every frame (such as fullscreen quads, static HUD elements, or custom meshes), avoid allocating and writing CPU vertex streams every frame. Use `shader.prepareVertices(...)` and `seal()`:

```kotlin
val prepared = shader.prepareVertices(4)
    .vec2(0f, 0f).vec2(width, height)
    .vec2(0f, 0f).vec2(width, height)
    .vec2(0f, 0f).vec2(width, height)
    .vec2(0f, 0f).vec2(width, height)
    .seal()

// During render loop:
shader.attach()
shader.draw(prepared)

// When resizing or destroying:
prepared.close()
```

---

### 4. Lifecycle & Resource Management

- **Init**: Call `LumaMinecraft.install()` and attach shared shaders/atlases during mod initialization (`ModInitializer.onInitialize`).
- **Shutdown / Device Reset**: Hook into `Minecraft.close()` and `GpuDevice` recreate events to invoke `RenderUtil.close()` to free all allocated GPU textures, pipelines, and buffers.
