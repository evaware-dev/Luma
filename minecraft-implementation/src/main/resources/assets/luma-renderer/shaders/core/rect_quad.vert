#version 330 core

layout(location = 0) in vec2 a0;
layout(location = 1) in vec2 a1;
layout(location = 2) in vec2 a2;
layout(location = 3) in vec4 a3;
layout(location = 4) in vec4 a4;
layout(location = 5) in vec4 a5;

uniform mat4 uMatrix;

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
