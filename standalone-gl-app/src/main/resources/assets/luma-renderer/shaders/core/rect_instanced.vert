#version 330 core

const vec2 RECT_TRIANGLES[6] = vec2[6](
    vec2(0.0, 0.0),
    vec2(0.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 0.0),
    vec2(0.0, 0.0)
);

@in 0 vec2 a0 Origin
@in 1 vec2 a1 Size
@in 2 vec4 a2 Color

@uniforms
uniform mat4 uMatrix;
@end

out vec4 vColor;

void main() {
    vec2 corner = RECT_TRIANGLES[gl_VertexID];
    gl_Position = uMatrix * vec4(a0 + corner * a1, 0.0, 1.0);
    vColor = a2;
}
