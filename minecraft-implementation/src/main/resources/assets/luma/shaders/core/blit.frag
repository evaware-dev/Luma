#version 330 core

@sampler sampler2D uTexture 0

in vec2 vUv;
out vec4 fragColor;

void main() {
    fragColor = texture(uTexture, vUv);
}
