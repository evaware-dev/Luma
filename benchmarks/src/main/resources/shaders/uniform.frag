#version 330 core

@uniforms
uniform vec4 uColor;
@end

out vec4 fragColor;

void main() {
    fragColor = uColor;
}
