#version 330 core

in vec2 vPos;
out vec4 fragColor;

void main() {
    if (dot(vPos, vPos) > 0.25) discard;
    fragColor = vec4(1.0, 1.0, 1.0, 1.0);
}
