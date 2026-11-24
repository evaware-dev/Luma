# Shader & Rendering API Usage

Raw GLSL works with the `api` and `core-gl` artifacts alone. Add `shader-translator` and assign `DefaultShaderTranslator` when using Luma `@`-directives or sharing shader sources between OpenGL and Blaze3D/Renderpearl Vulkan.

For `@`-directive preprocessor syntax, see [Shader preprocessor syntax](shader_example.md).

## Fluent `Shader` API

```kotlin
val shader = Shader(
    "assets/luma/shaders/core/texture_rect.frag",
    "assets/luma/shaders/core/texture_rect.vert"
).drawMode(PrimitiveType.TRIANGLES)

val uMatrix: Mat4Uniform
with(shader) {
    vertices.float(2, 0)
    vertices.float(4, 1)
    uMatrix = uniforms.mat4("uMatrix")
}

shader.load()

shader.vertices
    .vec2(x, y)
    .vec4(r, g, b, a)

shader.attach()
shader.uniforms.mat4(uMatrix, matrix)
shader.draw()
```

1. Construct a `Shader` with fragment/vertex resource paths and optional `drawMode(...)`.
2. Declare the vertex layout (`vertices.float(count, layoutPos)`) and uniforms (`uniforms.mat4("name")`).
3. Call `load()` to translate sources, collect `ShaderReflection`, and create the backend program.
4. Push per-vertex attributes through `vertices`, call `attach()`, set uniforms, and call `draw()`.

## Preparing Vertices Off the Render Thread

`PreparedVertices` owns a detached native vertex buffer. A worker thread may populate it and call `seal()` to publish the packet to the render thread (caller provides the inter-thread handoff synchronization).

```kotlin
val prepared = shader.prepareVertices(initialVertexCapacity = 4096)

workerExecutor.submit {
    prepared
        .vec2(x0, y0).vec4(r, g, b, a)
        .vec2(x1, y1).vec4(r, g, b, a)
        .seal()
}

shader.attach()
shader.draw(prepared)
```

Drawing does not clear `PreparedVertices`, so it can be submitted across frames. Call `clear()` before rebuilding and `close()` to release its native buffer.

## Hardware Instancing

For instanced draws where static base geometry is generated in the vertex shader (`gl_VertexID` / `gl_VertexIndex`):

```kotlin
val instancedShader = Shader("path/to/quad.frag", "path/to/quad.vert")
    .instanced(baseVertexCount = 6)
```

## Explicit GPU Buffers & `ShaderReflection`

For low-level control over GPU vertex/index buffers and multi-binding layouts:

```kotlin
val backend = Luma.backend

val vertexLayout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
val instanceLayout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 1) }

val inputLayout = VertexInputLayout()
    .binding(vertexLayout, bindingIndex = 0)
    .binding(instanceLayout, bindingIndex = 1, stepRate = 1)

val translated = Luma.shaderTranslator.translate(rawVert, rawFrag, vertexLayout)
val program = backend.createProgram(
    translated.vertexSource,
    translated.fragmentSource,
    inputLayout,
    translated.reflection
)

val vertexBuffer = backend.createVertexBuffer(vertexBytes, BufferUsage.STATIC)
val instanceBuffer = backend.createVertexBuffer(instanceBytes, BufferUsage.STREAM)
val indexBuffer = backend.createIndexBuffer(indexBytes, IndexType.UINT16, BufferUsage.STATIC)

backend.updateVertexBuffer(vertexBuffer, 0L, vertexData)
backend.updateVertexBuffer(instanceBuffer, 0L, instanceData)
backend.updateIndexBuffer(indexBuffer, 0L, indexData)

backend.bindVertexBuffer(0, vertexBuffer)
backend.bindVertexBuffer(1, instanceBuffer)
backend.bindIndexBuffer(indexBuffer)

backend.drawIndexed(
    program = program,
    uniforms = uniforms,
    primitiveType = PrimitiveType.TRIANGLES,
    firstIndex = 0,
    indexCount = indexCount,
    instanceCount = instanceCount
)

indexBuffer.close()
instanceBuffer.close()
vertexBuffer.close()
program.close()
```

Passing `translated.reflection` (`ShaderReflection`) supplies typed attribute, uniform, and sampler metadata directly to the backend without runtime GLSL parsing.

## Textures, Formats, Samplers & Mipmaps

Textures support explicit `TextureFormat` (`RGBA8`, `R8`, `RG8`, `RGBA16F`, `R32F`, `RGBA32F`) and `SamplerState` presets (`LINEAR_CLAMP`, `NEAREST_CLAMP`, `LINEAR_REPEAT`, `NEAREST_REPEAT`) or custom `SamplerState(minFilter, magFilter, wrapU, wrapV, maxAnisotropy, maxLod)`:

```kotlin
val texture = Luma.backend.createTexture(
    width = 256,
    height = 256,
    data = rawByteBuffer,
    format = TextureFormat.R8,
    mipmap = false,
    sampler = SamplerState.NEAREST_REPEAT
)

// Update sampler state dynamically without recreating the texture
Luma.setTextureSampler(texture, SamplerState.LINEAR_CLAMP)

// Generate mipmaps on OpenGL (unsupported on Blaze3D Vulkan)
Luma.generateMipmaps(texture)

Luma.bindTexture(texture, unit = 0)
```

## Color Mask, Native Stencil & Cross-Backend `ClipMask`

### Color Write Mask

```kotlin
Luma.colorMask(red = false, green = false, blue = false, alpha = false)
// depth-only or stencil-only geometry
Luma.colorMask(red = true, green = true, blue = true, alpha = true)
```

### Cross-Backend Early-Z `ClipMask` (OpenGL & Vulkan)

`ClipMask` clips arbitrary float geometry and fragment `discard` SDF masks using hardware Early-Z depth rejection on both OpenGL and Vulkan. The active render target must be created with `useDepth = true`:

```kotlin
val target = Luma.backend.createRenderTarget(width, height, useDepth = true)

Luma.render {
    Luma.backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 0f, 1f))
    ClipMask.clip(
        mode = ClipMode.INSIDE,
        clear = true,
        mask = { drawMaskShape() },
        content = { drawClippedContent() }
    )
    Luma.backend.endRenderTarget()
}
```

For multi-pass clipping against one mask, use `ClipMask.write { ... }`, `ClipMask.inside { ... }`, and `ClipMask.outside { ... }` (or imperative `beginWrite` / `beginTest` / `end`). Prior color-mask and depth state are automatically captured and restored.

### Native OpenGL Stencil

When `Luma.backend.supportsStencil` is `true` (`core-gl`), render targets can allocate a combined depth/stencil attachment (`useStencil = true`):

```kotlin
if (Luma.backend.supportsStencil) {
    val target = Luma.backend.createRenderTarget(width, height, useDepth = true, useStencil = true)
    Luma.render {
        Luma.backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 0f, 1f))
        Luma.clearStencil(0)
        Luma.enableStencilTest()
        Luma.colorMask(false, false, false, false)
        Luma.stencilMask(0xFF)
        Luma.stencilFunc(StencilCompare.ALWAYS, 1, 0xFF)
        Luma.stencilOp(StencilOp.KEEP, StencilOp.KEEP, StencilOp.REPLACE)
        drawMaskShape()

        Luma.colorMask(true, true, true, true)
        Luma.stencilMask(0x00)
        Luma.stencilFunc(StencilCompare.EQUAL, 1, 0xFF)
        Luma.stencilOp(StencilOp.KEEP, StencilOp.KEEP, StencilOp.KEEP)
        drawClippedContent()

        Luma.stencilMask(0xFF)
        Luma.disableStencilTest()
        Luma.backend.endRenderTarget()
    }
}
```

## Render Target Blitting

Copy or scale regions between render targets with `Luma.blitRenderTarget`:

```kotlin
Luma.blitRenderTarget(
    source = offscreenTarget,
    destination = destinationTarget, // null targets the default framebuffer on OpenGL or active target on Vulkan
    filter = TextureFilter.LINEAR,
    copyColor = true,
    copyDepth = false
)
```

On OpenGL this issues `glBlitFramebuffer`; on Vulkan 1:1 matching-format copies use native `copyTextureToTexture` and scaled or format-converting color copies fall back to an internal textured quad pass.

## Passive Reusable `DrawList`

`DrawList` records state, binding, render-target, clip, debug-group, and draw commands into compact SoA arrays and an off-heap vertex buffer, snapshotting uniform values per draw so subsequent mutations do not affect recorded draws.

- **Zero overhead when unused**: `DrawList` never wraps `Luma.backend` permanently; `record { ... }` swaps `Luma.backend` only for the duration of recording and restores it in `finally`.
- **Steady-state reuse**: Re-recording reuses existing arrays and native memory. Consecutive identical immediate draws are tail-merged in O(1) at record time.
- **0 B/op replay**: `replay()` submits recorded commands to any `RenderBackend` with zero heap allocations in steady state.

```kotlin
val drawList = DrawList()

drawList.record {
    Luma.debugGroup("StaticLayer") {
        Luma.enableBlend()
        shader.attach()
        shader.uniforms.vec4(uColor, 1f, 0f, 0f, 1f)
        emitQuad(shader)
        shader.draw()

        shader.uniforms.vec4(uColor, 0f, 1f, 0f, 1f)
        emitSecondQuad(shader)
        shader.draw()
    }
}

Luma.render {
    Luma.backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 0f, 1f))
    drawList.replay()
    Luma.backend.endRenderTarget()
}

// Release native vertex buffer when no longer needed
drawList.close()
```

Convenience wrappers `Luma.record(drawList) { ... }` and `Luma.replay(drawList)` are also available.
