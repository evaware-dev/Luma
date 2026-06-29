#version 330 core

#luma-in 0 vec2 a0 Position
#luma-in 1 vec4 a1 Color

#luma-uniforms
uniform mat4 uMatrix;
#luma-end

out vec4 vColor;

void main() {
    gl_Position = uMatrix * vec4(a0, 0.0, 1.0);
    vColor = a1;
}
