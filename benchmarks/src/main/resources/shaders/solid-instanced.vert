#version 330 core

@in 0 vec2 aOffset Offset

const vec2 TRIANGLE[3] = vec2[3](
    vec2(0.0, 0.0),
    vec2(0.001, 0.0),
    vec2(0.0, 0.001)
);

void main() {
    gl_Position = vec4(TRIANGLE[gl_VertexID] + aOffset, 0.0, 1.0);
}
