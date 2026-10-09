#version 330 core

#import<matrix>
#import<rect>

@in 0 vec2 a0 Origin
@in 1 vec2 a1 Size

out vec2 vUv;

void main() {
    vec2 corner = RECT_VERTICES[gl_VertexID % 4];
    gl_Position = uMatrix * vec4(a0 + corner * a1, 0.0, 1.0);
    vUv = vec2(corner.x, 1.0 - corner.y);
}
