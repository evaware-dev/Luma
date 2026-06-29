# Shader Example with `@` Syntax

Luma uses a custom preprocessor to translate shaders into Vulkan (Blaze3D) or OpenGL targets. Shaders must use `@` directives instead of `#luma-` prefixed ones.

## Directives

- `@in <location> <type> <localName> <backendName>`: Declares a vertex attribute.
- `@uniforms` / `@end`: Encloses shared uniform variable declarations.
- `@sampler <type> <name> <binding>`: Declares a texture sampler.

## Vertex Shader Example

```glsl
#version 330 core

@in 0 vec2 a0 Position
@in 1 vec2 a1 UV0
@in 2 vec4 a2 Color
@in 3 vec4 a3 Scissor

@uniforms
uniform mat4 uMatrix;
@end

out vec2 vUv;
out vec4 vColor;
out vec4 vScissor;

void main() {
    gl_Position = uMatrix * vec4(a0, 0.0, 1.0);
    vUv = a1;
    vColor = a2;
    vScissor = a3;
}
```

## Fragment Shader Example

```glsl
#version 330 core

#import<scissor>

in vec2 vUv;
in vec4 vColor;
in vec4 vScissor;
out vec4 fragColor;

@sampler sampler2D uTexture 0

void main() {
    if (!scissorVisible(vScissor, gl_FragCoord.xy)) {
        discard;
    }

    fragColor = texture(uTexture, vUv) * vColor;
}
```
