#version 330

uniform sampler2D Sampler0;

in vec2 vUv;
in vec4 vColor;
in vec2 vState;
in vec4 vScissor;

out vec4 fragColor;

bool scissorVisible(vec4 scissor, vec2 fragCoord) {
    if (scissor.z <= scissor.x || scissor.w <= scissor.y) {
        return true;
    }

    return fragCoord.x >= scissor.x &&
        fragCoord.y >= scissor.y &&
        fragCoord.x <= scissor.z &&
        fragCoord.y <= scissor.w;
}

float median3(vec3 value) {
    return max(min(value.r, value.g), min(max(value.r, value.g), value.b));
}

float msdfAlpha(vec2 uv, float range) {
    vec3 sampleValue = texture(Sampler0, uv).rgb;
    float signedDistance = median3(sampleValue) - 0.5;
    float pxRange = max(range, 1.0);
    float alpha = signedDistance * pxRange + 0.5;
    return clamp(alpha, 0.0, 1.0);
}

void main() {
    if (!scissorVisible(vScissor, gl_FragCoord.xy)) {
        discard;
    }

    int mode = int(vState.x + 0.5);
    vec4 sampleColor = texture(Sampler0, vUv);

    if (mode == 1) {
        float alpha = msdfAlpha(vUv, vState.y);
        fragColor = vec4(vColor.rgb, vColor.a * alpha);
        return;
    }

    fragColor = sampleColor * vColor;
}
