#version 330

layout(std140) uniform Projection {
    mat4 uMatrix;
};

in vec2 Position;
in vec2 UV0;
in vec4 Color;
in vec2 State;
in vec4 Scissor;

out vec2 vUv;
out vec4 vColor;
out vec2 vState;
out vec4 vScissor;

void main() {
    gl_Position = uMatrix * vec4(Position, 0.0, 1.0);
    vUv = UV0;
    vColor = Color;
    vState = State;
    vScissor = Scissor;
}
