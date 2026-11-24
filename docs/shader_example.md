# Shader Preprocessor (`@` Syntax)

`DefaultShaderTranslator` translates Luma GLSL sources into either OpenGL 330 core or Blaze3D/Renderpearl Vulkan GLSL 450 and emits structured `ShaderReflection` metadata (`attributes`, `uniforms`, `samplers`).

## Directives

- `@in <location> <type> <localName> <backendName>`: Declares a vertex attribute.
- `@uniforms` / `@end`: Encloses uniform declarations lowered to an std140 UBO on Vulkan or standard uniforms on OpenGL.
- `@sampler <type> <name> <binding>`: Declares a texture sampler bound to the given texture unit / binding index.

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

## Backend Translation & Reflection

- **Vulkan / SPIR-V (`ShaderTarget.BLAZE3D`)**: Vertex attributes and varyings receive explicit `layout(location = ...)` qualifiers, `gl_VertexID` is rewritten to `gl_VertexIndex`, and `@uniforms` blocks are lowered to `layout(std140, binding = ...) uniform LumaUniforms { ... }`.
- **OpenGL (`ShaderTarget.OPENGL`)**: Directives lower to standard GLSL 330 core `in`, `uniform`, and `sampler2D` declarations.
- **Typed `ShaderReflection`**: `TranslatedShader.reflection` carries parsed `ShaderAttributeInfo`, `ShaderUniformInfo`, and `ShaderSamplerInfo` lists directly to `RenderBackend.createProgram(..., reflection)`, avoiding runtime source regex parsing in backends.
