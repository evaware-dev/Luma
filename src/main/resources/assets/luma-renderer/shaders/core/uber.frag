#version 330 core

#import<font_renderer>
#import<scissor>

in vec2 vUv;
in vec4 vColor;
in vec2 vState;
in vec4 vScissor;
out vec4 fragColor;

uniform sampler2D uTexture;

void main() {
    if (!scissorVisible(vScissor, gl_FragCoord.xy)) {
        discard;
    }

    int mode = int(vState.x + 0.5);
    vec4 sampleColor = texture(uTexture, vUv);

    if (mode == 1) {
        float alpha = msdfAlpha(uTexture, vUv, vState.y);
        fragColor = vec4(vColor.rgb, vColor.a * alpha);
        return;
    }

    fragColor = sampleColor * vColor;
}
