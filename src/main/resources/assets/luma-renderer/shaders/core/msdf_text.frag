#version 330 core

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform float uRange;

float msdfMedian(vec3 value) {
    return max(min(value.r, value.g), min(max(value.r, value.g), value.b));
}

void main() {
    vec3 sampleColor = texture(uTexture, vUv).rgb;
    float signedDistance = msdfMedian(sampleColor) - 0.5;
    vec2 pixelDerivatives = fwidth(vUv) * vec2(textureSize(uTexture, 0));
    float pixels = uRange * inversesqrt(max(dot(pixelDerivatives, pixelDerivatives), 1e-6));
    float alpha = smoothstep(-0.5, 0.5, signedDistance * pixels);
    fragColor = vec4(vColor.rgb, vColor.a * alpha);
}
