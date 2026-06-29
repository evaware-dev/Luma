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
