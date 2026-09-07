#version 330 core

@sampler sampler2D Sampler0 0

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, vec2(0.5));
}
