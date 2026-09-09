#version 330 core

@in 0 vec2 aPosition Position
@in 1 vec2 aOffset Offset

void main() {
    gl_Position = vec4(aPosition + aOffset, 0.0, 1.0);
}
