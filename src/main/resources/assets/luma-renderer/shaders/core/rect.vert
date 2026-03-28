#version 330 core

layout(location = 0) in vec2 a0;
layout(location = 1) in vec4 a1;

uniform mat4 uMatrix;

out vec4 vColor;

void main() {
    gl_Position = uMatrix * vec4(a0, 0.0, 1.0);
    vColor = a1;
}
