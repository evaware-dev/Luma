#version 330 core

in vec2 vUv;
in vec4 vColor;
in vec4 vScissor;
out vec4 fragColor;

uniform sampler2D uTexture;

void main() {
    fragColor = texture(uTexture, vUv) * vColor;
}
