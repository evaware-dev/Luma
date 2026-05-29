# Luma

Lightweight OpenGL wrapper written in Kotlin.

## Project Structure

- **[core](core)**: Core OpenGL/GLFW library.
- **[minecraft-implementation](minecraft-implementation)**: Integration mod example for Minecraft.
- **[standalone-app](standalone-app)**: Standalone test and demo application.

## Requirements

- Java 21+
- Kotlin

## Usage

Add the JitPack repository and the core dependency to your project:

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.evaware-dev.Luma:core:VERSION'
}
```

## License

MIT License
