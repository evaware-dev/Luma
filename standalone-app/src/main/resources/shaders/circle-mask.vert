#version 330 core

@in 0 vec2 aPosition Position

out vec2 vPos;

void main() {
    vPos = aPosition;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
