#version 330 core

layout(location = 0) in vec2 a0;
layout(location = 1) in vec2 a1;
layout(location = 2) in vec4 a2;
layout(location = 3) in vec2 a3;
layout(location = 4) in vec4 a4;

uniform mat4 uMatrix;

out vec2 vUv;
out vec4 vColor;
out vec2 vState;
out vec4 vScissor;

void main() {
    gl_Position = uMatrix * vec4(a0, 0.0, 1.0);
    vUv = a1;
    vColor = a2;
    vState = a3;
    vScissor = a4;
}
