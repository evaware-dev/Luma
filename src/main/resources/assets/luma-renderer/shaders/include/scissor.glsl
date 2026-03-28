bool scissorVisible(vec4 scissor, vec2 fragCoord) {
    if (scissor.z <= scissor.x || scissor.w <= scissor.y) return true;

    vec2 insideMin = step(scissor.xy, fragCoord);
    vec2 insideMax = step(fragCoord, scissor.zw);
    return insideMin.x * insideMin.y * insideMax.x * insideMax.y > 0.5;
}
