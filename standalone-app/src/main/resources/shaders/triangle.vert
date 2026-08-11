#version 330 core

@in 0 vec2 aPosition Position

void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
