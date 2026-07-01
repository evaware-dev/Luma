#version 330 core

@in 0 vec2 a0 Position
@in 1 vec4 a1 Color

@uniforms
uniform mat4 uMatrix;
@end

out vec4 vColor;

void main() {
    gl_Position = uMatrix * vec4(a0, 0.0, 1.0);
    vColor = a1;
}
