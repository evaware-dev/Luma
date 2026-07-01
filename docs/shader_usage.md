# Shader usage

How to configure and render with a Luma `Shader` from Kotlin.

For the shader source `@`-directive syntax (preprocessor), see
[Shader preprocessor syntax](shader_example.md).

## Configuration and rendering

```kotlin
val shader = Shader(
    "assets/luma/shaders/core/texture_rect.frag",
    "assets/luma/shaders/core/texture_rect.vert"
).drawMode(GL11.GL_TRIANGLES)

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
