#version 330 core

@in 0 vec2 a0 Position
@in 1 vec2 a1 UV0
@in 2 vec2 a2 a2
@in 3 vec4 a3 Scissor
@in 4 vec4 a4 Color
@in 5 vec4 a5 a5

@uniforms
uniform mat4 uMatrix;
@end

out vec2 vLocal;
out vec2 vSize;
out vec4 vRadius;
out vec4 vColor;
out vec4 vScissor;

void main() {
    gl_Position = uMatrix * vec4(a0, 0.0, 1.0);
    vLocal = a1;
    vSize = a2;
    vRadius = a3;
    vColor = a4;
    vScissor = a5;
}
