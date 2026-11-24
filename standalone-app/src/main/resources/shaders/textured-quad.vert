#version 330 core

@in 0 vec2 aPosition Position
@in 1 vec2 aUv UV0

out vec2 vUv;

void main() {
    vUv = aUv;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
