#version 330 core

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

uniform sampler2D uTexture;

void main() {
    fragColor = texture(uTexture, vUv) * vColor;
}
