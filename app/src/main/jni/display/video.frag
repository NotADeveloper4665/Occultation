#version 450
layout(set=0,binding=0) uniform sampler2D video;
layout(location=0) in vec2 uv;
layout(location=0) out vec4 color;
// YCbCr conversion is provided by the immutable Vulkan sampler, not shader math.
void main() { color = vec4(texture(video, uv).rgb, 1.0); }
