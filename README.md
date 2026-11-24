<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/banner-dark.png">
    <source media="(prefers-color-scheme: light)" srcset="assets/banner-light.png">
    <img alt="Luma" src="assets/banner-dark.png" height="72">
  </picture>

  <p>Dual-backend OpenGL 3.3+ and Blaze3D Vulkan rendering library for Minecraft and standalone JVM apps.</p>

  <p>
    <img alt="Java 25+" src="https://img.shields.io/badge/Java-25%2B-1f2328?style=flat&labelColor=0d1117">
    <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/License-MIT-1f2328?style=flat&labelColor=0d1117"></a>
  </p>
</div>

## Features

- **Dual backends**: unified API over OpenGL 3.3+ and Mojang Blaze3D Vulkan.
- **Draw call merging**: automatic batching across shared pipelines, textures, and scissor state.
- **Explicit GPU buffers**: direct vertex/index buffers, multi-stream `VertexInputLayout`, and hardware instancing.
- **Shader preprocessor**: `@in`, `@uniforms`, and `#import` directives targeting GLSL 330 and SPIR-V `std140` UBOs.
- **Render targets & atlases**: dynamic offscreen framebuffers, borrowed target lifecycles, and shelf-packed 2D texture atlases.
- **GL state isolation**: `GlStatePolicy.PRESERVE` (default) or `OWNED`, with raw GL via `Backend.externalGl { ... }`.

## Modules

| Module | Purpose |
| --- | --- |
| [api](api) | Core rendering interfaces and resource lifecycle |
| [core-gl](core-gl) | OpenGL 3.3+ backend |
| [vulkan-blaze3d-wrapper](vulkan-blaze3d-wrapper) | Mojang Blaze3D Vulkan backend |
| [shader-translator](shader-translator) | GLSL / SPIR-V shader preprocessor |
| [minecraft-render-library](minecraft-render-library) | Minecraft integration with automatic backend selection |
| [minecraft-implementation](minecraft-implementation) | Example Fabric integration mod |
| [developer-tools](developer-tools) | Optional allocation-free frame and lifetime metrics |
| [standalone-app](standalone-app) | Standalone OpenGL and Blaze3D Vulkan window apps |
| [benchmarks](benchmarks) | Isolated OpenGL vs. Blaze3D Vulkan workloads |

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

dependencies {
    // Minecraft Fabric (26.2 or 26.3, includes transitive core):
    implementation "sweetie.evaware.luma:minecraft-render-library-26.2:$luma_version"
    // implementation "sweetie.evaware.luma:minecraft-render-library-26.3:$luma_version"

    // Standalone / modular:
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

dependencies {
    implementation "com.github.evaware-dev.Luma:minecraft-render-library-26.2:$luma_version"
    // implementation "com.github.evaware-dev.Luma:minecraft-render-library-26.3:$luma_version"

    // Standalone / modular:
    implementation "com.github.evaware-dev.Luma:api:$luma_version"
    implementation "com.github.evaware-dev.Luma:core-gl:$luma_version"
    implementation "com.github.evaware-dev.Luma:vulkan-blaze3d-wrapper:$luma_version"
    implementation "com.github.evaware-dev.Luma:shader-translator:$luma_version"
    implementation "com.github.evaware-dev.Luma:developer-tools:$luma_version"
}
```

Releases: [github.com/evaware-dev/Luma/releases](https://github.com/evaware-dev/Luma/releases)

## Documentation

- [Minecraft integration](minecraft-implementation/README.md) — render targets, lifecycle hooks, and mod setup
- [Shader usage](docs/shader_usage.md) — configuring and drawing with `Shader` in Kotlin
- [Shader preprocessor syntax](docs/shader_example.md) — `@`-directive syntax reference
- [Developer tools](docs/developer_tools.md) — allocation-free frame and lifetime statistics
- [Benchmarks](docs/benchmarks.md) — OpenGL and Blaze3D Vulkan benchmark workloads

[MIT License](LICENSE)
