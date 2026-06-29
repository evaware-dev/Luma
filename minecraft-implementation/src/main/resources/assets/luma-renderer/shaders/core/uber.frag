#version 330 core

#import<scissor>

in vec2 vUv;
in vec4 vColor;
in vec4 vScissor;
out vec4 fragColor;

@sampler sampler2D uTexture 0

void main() {
    if (!scissorVisible(vScissor, gl_FragCoord.xy)) {
        discard;
    }

    fragColor = texture(uTexture, vUv) * vColor;
}
