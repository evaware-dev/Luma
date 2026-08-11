# Luma

Lightweight rendering abstraction library for OpenGL and Vulkan (via Mojang Blaze3D) written in Kotlin.

## Project structure

- **[api](api)**: Common interfaces, rendering contracts, and resource management.
- **[core-gl](core-gl)**: OpenGL backend implementation.
- **[vulkan-blaze3d-wrapper](vulkan-blaze3d-wrapper)**: Vulkan backend implementation integrated into Mojang Blaze3D.
- **[shader-translator](shader-translator)**: Luma shader preprocessor supporting both OpenGL and Vulkan/SPIR-V targets.
- **[minecraft-render-library](minecraft-render-library)**: Minecraft integration library, automatically choosing OpenGL or Vulkan backend.
- **[minecraft-implementation](minecraft-implementation)**: Integration mod example for Minecraft.
- **[developer-tools](developer-tools)**: Optional frame and lifetime statistics backend decorator.
- **[standalone-app](standalone-app)**: Standalone OpenGL and Blaze3D Vulkan applications and window lifecycle.
- **[benchmarks](benchmarks)**: Isolated, comparable OpenGL and Blaze3D Vulkan benchmarks.

## Usage

- [Shader usage](docs/shader_usage.md) — configuring and rendering with a `Shader` from Kotlin.
- [Shader preprocessor syntax](docs/shader_example.md) — the `@`-directive shader source syntax.
- [Developer tools](docs/developer_tools.md) — optional allocation-free frame and lifetime statistics.
- [Backend benchmarks](docs/benchmarks.md) — comparable OpenGL and Blaze3D Vulkan workloads.

The OpenGL backend defaults to `GlStatePolicy.PRESERVE`, which snapshots and restores the surrounding renderer's state. Applications that own the whole GL context can select `GlStatePolicy.OWNED` to remove that integration overhead. Raw GL remains available through `Backend.externalGl { ... }`; Luma invalidates its caches around the call instead of restricting what the application may do.

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
    implementation 'com.github.evaware-dev.Luma:developer-tools:VERSION'
}
```

## License

MIT License
