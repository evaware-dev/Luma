#version 330 core

#import<matrix>
#import<rect>

@in 0 vec2 a0 Origin
@in 1 vec2 a1 Size
@in 2 vec2 a2 UvOrigin
@in 3 vec2 a3 UvSize
@in 4 vec4 a4 Color
@in 5 vec4 a5 Scissor

out vec2 vUv;
out vec4 vColor;
out vec4 vScissor;

void main() {
    vec2 corner = RECT_TRIANGLES[gl_VertexID];
    gl_Position = uMatrix * vec4(a0 + corner * a1, 0.0, 1.0);
    vUv = a2 + corner * a3;
    vColor = a4;
    vScissor = a5;
}
