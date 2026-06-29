# Luma

Lightweight rendering abstraction library for OpenGL and Vulkan (via Mojang Blaze3D) written in Kotlin.

## Project structure

- **[api](api)**: Common interfaces, rendering contracts, and resource management.
- **[core-gl](core-gl)**: OpenGL backend implementation.
- **[vulkan-blaze3d-wrapper](vulkan-blaze3d-wrapper)**: Vulkan backend implementation integrated into Mojang Blaze3D.
- **[shader-translator](shader-translator)**: Luma shader preprocessor supporting both OpenGL and Vulkan/SPIR-V targets.
- **[minecraft-render-library](minecraft-render-library)**: Minecraft integration library, automatically choosing OpenGL or Vulkan backend.
- **[minecraft-implementation](minecraft-implementation)**: Integration mod example for Minecraft.
- **[standalone-gl-app](standalone-gl-app)**: Standalone test and demo OpenGL application.

## Usage

For detailed shader preprocessor syntax, see [Shader preprocessor syntax](docs/shader_example.md).

### Shaders configuration and rendering

```kotlin
val shader = Shader(
    "assets/luma/shaders/core/rect_quad.frag",
    "assets/luma/shaders/core/rect_quad.vert"
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

### Dependency

Add the JitPack repository and the core dependency to your project:

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.evaware-dev.Luma:api:VERSION'
    implementation 'com.github.evaware-dev.Luma:core-gl:VERSION'
    implementation 'com.github.evaware-dev.Luma:vulkan-blaze3d-wrapper:VERSION'
}
```

## License

MIT License
