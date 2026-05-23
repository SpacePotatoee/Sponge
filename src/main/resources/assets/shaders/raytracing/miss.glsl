#version 460
#extension GL_EXT_ray_tracing : enable
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

struct Material {
    vec4 color;
    vec4 emissiveColor;
};

struct Ray {
    bool hit;
    vec3 hitValue;
    vec3 rayOrigin;
    vec3 rayDir;
    vec3 rayPos;
    vec3 hitNormal;
    Material hitMaterial;
};

layout(set = 0, binding = 0) uniform CameraInfo {
    mat4 projMat;
    mat4 modelViewMat;
    mat4 invProjMat;
    mat4 invModelViewMat;
    vec3 cameraPos;

    uint64_t vertAddress;
    uint64_t meshAddress;

    float time;
} cameraInfo;

const vec3 UP_DIR = vec3(0.0, 1.0, 0.0);
const float PI = 3.141592;

//float TIME = mod(cameraInfo.time * 0.001, 1.0);
float TIME = 0.0;
float angle = (TIME * 360 - 90) * (PI * 0.005555555555556);
vec3 SUN_DIR = normalize(vec3(-cos(angle), -sin(angle), 0.1));
vec3 MOON_DIR = vec3(-SUN_DIR.x, -SUN_DIR.y, SUN_DIR.z);

layout(location = 0) rayPayloadInEXT Ray ray;
layout(set=4, binding=0) uniform sampler2D SkyGradient;

vec3 getSkyColor(in vec3 dir) {
    float upDot = clamp(dot(UP_DIR, dir), 0.0, 1.0);
    float sun = pow(clamp(dot(dir, SUN_DIR), 0.0, 1.0), 100);
    float moon = smoothstep(0.995, 1.0,clamp(dot(dir, MOON_DIR), 0.0, 1.0));
    vec3 horizonColor = texture(SkyGradient, vec2(0.25, TIME)).rgb;
    vec3 upColor = texture(SkyGradient, vec2(0.75, TIME)).rgb;

    return mix(horizonColor, upColor, upDot) + sun + moon;
}

void main() {
    ray.hit = false;
//    ray.hitValue = getSkyColor(ray.rayDir);
    ray.hitValue = vec3(1.0);
    ray.hitMaterial = Material(vec4(0.0), vec4(0.0));
//    ray.hitValue = vec3(0.0);
}