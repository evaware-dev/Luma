#version 330 core

#import<scissor>

in vec2 vLocal;
in vec2 vSize;
in vec4 vRadius;
in vec4 vColor;
in vec4 vScissor;
out vec4 fragColor;

float roundedDistance(vec2 local, vec2 size, vec4 radius) {
    float corner = radius.x;
    if (local.x >= size.x * 0.5 && local.y < size.y * 0.5) {
        corner = radius.y;
    } else if (local.x >= size.x * 0.5 && local.y >= size.y * 0.5) {
        corner = radius.z;
    } else if (local.x < size.x * 0.5 && local.y >= size.y * 0.5) {
        corner = radius.w;
    }

    vec2 centered = abs(local - size * 0.5);
    vec2 halfSize = size * 0.5 - vec2(corner);
    vec2 q = centered - halfSize;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - corner;
}

void main() {
    if (!scissorVisible(vScissor, gl_FragCoord.xy)) {
        discard;
    }

    float dist = roundedDistance(vLocal, vSize, vRadius);
    float aa = max(fwidth(dist), 0.75);
    float alpha = 1.0 - smoothstep(-aa, aa, dist);
    fragColor = vec4(vColor.rgb, vColor.a * alpha);
}
