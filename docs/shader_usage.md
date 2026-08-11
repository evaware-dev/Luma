# Shader usage

Raw GLSL works with the `api` and `core-gl` artifacts alone. Add `shader-translator` and assign `DefaultShaderTranslator` when using Luma shader directives or sharing shader sources with the Blaze3D Vulkan backend.

How to configure and render with a Luma `Shader` from Kotlin.

For the shader source `@`-directive syntax (preprocessor), see
[Shader preprocessor syntax](shader_example.md).

## Configuration and rendering

```kotlin
val shader = Shader(
    "assets/luma/shaders/core/texture_rect.frag",
    "assets/luma/shaders/core/texture_rect.vert"
).drawMode(PrimitiveType.TRIANGLES)

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

## Steps

1. Construct a `Shader` with fragment/vertex resource paths and pick a draw mode via `drawMode(...)`.
2. Declare the vertex layout (`vertices.float(count, layoutPos)`) and uniforms (`uniforms.mat4("name")`).
3. Call `load()` to translate the sources for the active backend and create the program.
4. Push per-vertex data through the fluent `vertices` API.
5. `attach()`, upload uniforms, then `draw()`.

## Preparing vertices off the render thread

`PreparedVertices` owns a detached CPU-side vertex buffer. One worker may fill it, then `seal()` publishes the completed packet to the render thread. The caller must provide the usual queue, future, or other synchronization that transfers the packet between threads.

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

Drawing does not consume or clear the packet, so it can be submitted repeatedly. Call `clear()` before rebuilding it and `close()` when its native buffer is no longer needed. Shader state, uniforms, textures, and actual backend submission remain render-thread responsibilities.
