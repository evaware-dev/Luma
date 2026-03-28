float msdfMedian(vec3 value) {
    return max(min(value.r, value.g), min(max(value.r, value.g), value.b));
}

float msdfAlpha(sampler2D textureSampler, vec2 uv, float range) {
    vec3 sampleColor = texture(textureSampler, uv).rgb;
    float signedDistance = msdfMedian(sampleColor) - 0.5;
    vec2 pixelDerivatives = fwidth(uv) * vec2(textureSize(textureSampler, 0));
    float pixels = range * inversesqrt(max(dot(pixelDerivatives, pixelDerivatives), 1e-6));
    return smoothstep(-0.5, 0.5, signedDistance * pixels);
}
