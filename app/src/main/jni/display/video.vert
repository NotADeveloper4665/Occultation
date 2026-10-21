#version 450
layout(location=0) out vec2 uv;
layout(push_constant) uniform Crop { vec4 rect; } crop;
void main() {
    vec2 p = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    uv = mix(crop.rect.xy, crop.rect.zw, p);
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
