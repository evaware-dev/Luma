#version 330 core

#import<matrix>
#import<rect>

@in 0 vec2 a0 Origin
@in 1 vec2 a1 Size
@in 2 vec4 a2 Radius
@in 3 vec4 a3 Color
@in 4 vec4 a4 Scissor

out vec2 vLocal;
out vec2 vSize;
out vec4 vRadius;
out vec4 vColor;
out vec4 vScissor;

void main() {
    vec2 corner = RECT_TRIANGLES[gl_VertexID];
    vec2 local = corner * a1;
    gl_Position = uMatrix * vec4(a0 + local, 0.0, 1.0);
    vLocal = local;
    vSize = a1;
    vRadius = a2;
    vColor = a3;
    vScissor = a4;
}
