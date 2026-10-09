# Luma

High-performance dual-backend (OpenGL & Blaze3D Vulkan) rendering engine and abstraction library for Minecraft and standalone Kotlin/Java applications.

## Features

- **Dual-backend execution**: Seamlessly switches between OpenGL 3.3+ and Vulkan with a unified abstraction layer.
- **Draw Call Merging**: Dynamic batching of draw passes sharing pipelines, textures, and scissor state.
- **Explicit GPU buffers**: Direct vertex/index buffers, multi-stream vertex layouts (`VertexInputLayout`), and hardware instancing.
- **Shader preprocessor**: Custom `@in`, `@uniforms`, and `#import` directives compiled into GLSL 330 and SPIR-V std140 UBO layout.
- **Render targets**: Dynamic offscreen framebuffers, texture filters, and borrowed target lifecycle management.
- **Texture atlases**: Shelf-packed dynamically resized 2D texture atlases with transfer buffers.
- **Zero-allocation state management**: State isolation with `GlStatePolicy.PRESERVE/OWNED` and non-allocating metrics via `developer-tools`.

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

## Documentation

- [Minecraft integration guide](minecraft-implementation/README.md) — Blaze3D render targets, lifecycle hooks, and mod setup.
- [Shader usage](docs/shader_usage.md) — configuring and rendering with a `Shader` from Kotlin.
- [Shader preprocessor syntax](docs/shader_example.md) — the `@`-directive shader source syntax.
- [Developer tools](docs/developer_tools.md) — optional allocation-free frame and lifetime statistics.
- [Backend benchmarks](docs/benchmarks.md) — comparable OpenGL and Blaze3D Vulkan workloads.

The OpenGL backend defaults to `GlStatePolicy.PRESERVE`, which snapshots and restores the surrounding renderer's state. Applications that own the whole GL context can select `GlStatePolicy.OWNED` to remove that integration overhead. Raw GL remains available through `Backend.externalGl { ... }`; Luma invalidates its caches around the call instead of restricting what the application may do.

## Requirements

- **JDK 25+** (Mojang Blaze3D / Minecraft 26.2+)

## Installation

### GitHub Packages

```groovy
repositories {
    mavenCentral()
    maven {
        url 'https://maven.pkg.github.com/evaware-dev/Luma'
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}

ext {
    // See latest release: https://github.com/evaware-dev/Luma/releases
    luma_version = 'VERSION'
}

dependencies {
    // For Minecraft Fabric mods (transitive core included):
    implementation "sweetie.evaware.luma:minecraft-render-library-26.2:$luma_version"
    // or for Minecraft 26.3:
    // implementation "sweetie.evaware.luma:minecraft-render-library-26.3:$luma_version"

    // Standalone or modular setups:
    implementation "sweetie.evaware.luma:api:$luma_version"
    implementation "sweetie.evaware.luma:core-gl:$luma_version"
    implementation "sweetie.evaware.luma:vulkan-blaze3d-wrapper:$luma_version"
    implementation "sweetie.evaware.luma:shader-translator:$luma_version"
    implementation "sweetie.evaware.luma:developer-tools:$luma_version"
}
```

### JitPack

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

ext {
    // See latest release: https://github.com/evaware-dev/Luma/releases
    luma_version = 'TAG'
}

dependencies {
    // For Minecraft Fabric mods:
    implementation "com.github.evaware-dev.Luma:minecraft-render-library-26.2:$luma_version"
    // or for Minecraft 26.3:
    // implementation "com.github.evaware-dev.Luma:minecraft-render-library-26.3:$luma_version"

    // Standalone or modular setups:
    implementation "com.github.evaware-dev.Luma:api:$luma_version"
    implementation "com.github.evaware-dev.Luma:core-gl:$luma_version"
    implementation "com.github.evaware-dev.Luma:vulkan-blaze3d-wrapper:$luma_version"
    implementation "com.github.evaware-dev.Luma:shader-translator:$luma_version"
    implementation "com.github.evaware-dev.Luma:developer-tools:$luma_version"
}
```
