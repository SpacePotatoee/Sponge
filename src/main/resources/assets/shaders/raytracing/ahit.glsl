#version 460
#extension GL_EXT_ray_tracing : enable
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_EXT_scalar_block_layout : enable
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require
#extension GL_EXT_buffer_reference2 : require
#extension GL_EXT_ray_tracing_position_fetch : require

//8 floats. 3 verts
const int STRIDE = (8 * 3);

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

layout(buffer_reference, scalar) buffer Vertices {
    float f[];
};

layout(location = 0) rayPayloadInEXT Ray ray;
hitAttributeEXT vec2 baryCoords;

void main() {
    vec3 bary = vec3(baryCoords , 1.0 - baryCoords.x - baryCoords.y);
    ray.hit = true;

    vec3 pos1 = gl_HitTriangleVertexPositionsEXT[0];
    vec3 pos2 = gl_HitTriangleVertexPositionsEXT[1];
    vec3 pos3 = gl_HitTriangleVertexPositionsEXT[2];

     vec3 normal = normalize(cross(pos2 - pos1, pos3 - pos1));
    ray.hitNormal = gl_HitKindEXT == gl_HitKindFrontFacingTriangleEXT ? normal : -normal;
    ray.hitValue = vec3(0.0, 0.0, 0.0);
    ray.rayPos = ray.rayOrigin + ray.rayDir * gl_HitTEXT;
}