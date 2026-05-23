#version 460
#extension GL_EXT_ray_tracing : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

vec2 texCoord;

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
const float INV_PI = 1.0 / PI;

//float TIME = mod(cameraInfo.time * 0.001, 1.0);
float TIME = 0.0;
float angle = (TIME * 360 - 90) * (PI * 0.005555555555556);
vec3 SUN_DIR = normalize(vec3(-cos(angle), -sin(angle), 0.0));
vec3 MOON_DIR = vec3(-SUN_DIR.x, -SUN_DIR.y, SUN_DIR.z);

layout(set=1, binding=0) uniform accelerationStructureEXT accelStruct;
layout(set=2, binding=0, rgba16) uniform image2D outImage;
layout(set=3, binding=0, rgba16) uniform image2D prevImage;
layout(set=4, binding=0) uniform sampler2D SkyGradient;
layout(location = 0) rayPayloadEXT Ray ray;


Ray saveRayState() {
    return Ray(ray.hit, ray.hitValue, ray.rayOrigin, ray.rayDir, ray.rayPos, ray.hitNormal, ray.hitMaterial);
}

void loadSaveState(Ray savedRay) {
    ray.hit = savedRay.hit;
    ray.hitValue = savedRay.hitValue;
    ray.rayOrigin = savedRay.rayOrigin;
    ray.rayDir = savedRay.rayDir;
    ray.rayPos = savedRay.rayPos;
    ray.hitNormal = savedRay.hitNormal;
    ray.hitMaterial = savedRay.hitMaterial;
}

//////////////////////////////////////////////////////////////////
////                          RANDOM                          ////
//////////////////////////////////////////////////////////////////
float hash11(float p)
{
    p = fract(p * .1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float rand(vec2 p) {
    vec3 p3  = fract(vec3(p.xyx) * .1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z) * 2.0 - 1.0;
}

float rand2(vec2 p) {
    float value = rand(p);
    return value / cos(value);
}

vec3 getRandVec3(vec2 value) {
    return vec3(rand2(value * -0.634), rand2(value * 0.763), rand2(value * 0.524));
}

vec3 getRandomDir(vec2 value) {
    return normalize(getRandVec3(value));
}

vec3 getReflectionDir(vec3 incomingDir, vec3 normal, float rng) {
    vec3 randDir = getRandomDir(gl_LaunchIDEXT.xy * (rng * 0.652346) + cameraInfo.time*0.01 + 50);
    randDir = normalize(normal + randDir);
    vec3 reflectionDir = reflect(incomingDir, normal);

    return mix(randDir, reflectionDir, 0);
}

//////////////////////////////////////////////////////////////////
////                          CAMERA                          ////
//////////////////////////////////////////////////////////////////

vec3 projectAndDivide(mat4 projectionMat, vec3 position) {
    vec4 homogeneousPos = projectionMat * vec4(position, 1.0);
    return homogeneousPos.xyz / homogeneousPos.w;
}

vec3 screenToViewSpace(float depth) {
    return  projectAndDivide(cameraInfo.invProjMat, vec3(texCoord, depth) * 2.0 - 1.0);
}

vec3 screenToLocalSpace(float depth) {
    vec3 viewPos = screenToViewSpace(depth);
    return (cameraInfo.invModelViewMat * vec4(viewPos, 1.0)).xyz;
}

vec3 getRayDir() {
    return normalize(screenToLocalSpace(1.0));
}

//////////////////////////////////////////////////////////////////
////                        ENVIRONMENT                       ////
//////////////////////////////////////////////////////////////////

vec3 getSkyColor(in vec3 dir) {
    float upDot = clamp(dot(UP_DIR, dir), 0.0, 1.0);
    float sun = pow(clamp(dot(dir, SUN_DIR), 0.0, 1.0), 100);
    float moon = smoothstep(0.995, 1.0,clamp(dot(dir, MOON_DIR), 0.0, 1.0));
    vec3 horizonColor = texture(SkyGradient, vec2(0.25, TIME)).rgb;
    vec3 upColor = texture(SkyGradient, vec2(0.75, TIME)).rgb;

    return mix(horizonColor, upColor, upDot) + sun + moon;
}

vec3 getShadow() {
    vec3 light = vec3(0.0);
    bool moon = TIME > 0.25 && TIME < 0.75;
    vec3 shadowDir = moon ? MOON_DIR : SUN_DIR;

    Ray savedRay = saveRayState();
    //Shadow rays
    ray.hit = true;
    ray.rayOrigin = ray.rayPos + ray.hitNormal * 0.001;
    ray.rayDir = mix(shadowDir, getRandVec3((texCoord+1) + 0.2345 + cameraInfo.time), 0.00);
    if (dot(ray.hitNormal, shadowDir) >= -0.001) {
        traceRayEXT(accelStruct, gl_RayFlagsTerminateOnFirstHitEXT, 0xFFu, 0, 0, 0, ray.rayOrigin, 0.001, ray.rayDir, 10000, 0);
    }

    if (!ray.hit) {
        light += mix(vec3(moon ? 0.1 : 1.0), getSkyColor(ray.rayDir), 0.1) * dot(ray.hitNormal, ray.rayDir) * 10;
    }

    loadSaveState(savedRay);

    return light;
}

float linearToSRGB(float color) {
    if (color <= 0.0031308) {
        return color * 12.92;
    } else {
        return 1.055 * pow(color, 1/2.4) - 0.055;
    }
}

vec3 linearToSRGB(vec3 color) {
    return pow(color, vec3(1/2.2));
}

void main() {
    ivec2 pixelCoord = ivec2(gl_LaunchIDEXT.xy);
    vec3 throughPut = vec3(1.0);
    vec3 light = vec3(0.0);
    const vec2 pixelCenter = vec2(gl_LaunchIDEXT.xy) + vec2(0.5f, 0.5f);
    const vec2 invUv = pixelCenter/vec2(gl_LaunchSizeEXT.xy);
    texCoord = vec2(invUv.x, invUv.y);

    ray.rayDir = getRayDir();
    ray.rayOrigin = cameraInfo.cameraPos.xyz;
    ray.hit = false;

    //Primary rays
    traceRayEXT(accelStruct, gl_RayFlagsNoneEXT, 0xFFu, 0, 0, 0, ray.rayOrigin, 0.001, ray.rayDir, 10000, 0);
    bool prevHit = ray.hit;
    vec3 prevPos = ray.rayPos;
    vec3 prevNormal = ray.hitNormal;
    throughPut *= ray.hitValue;

    if (ray.hit && ray.hitMaterial.emissiveColor.a <= 0) {
        light += getShadow() * throughPut;

        ray.hit = false;
        ray.rayOrigin = ray.rayPos + ray.hitNormal * 0.001;
        ray.rayDir = getReflectionDir(ray.rayDir, ray.hitNormal, (2356));

        //Secondary rays
        int numOfBounces = 0;
        for (int i = 0; i < 2; i++) {
            numOfBounces++;
            traceRayEXT(accelStruct, gl_RayFlagsNoneEXT, 0xFFu, 0, 0, 0, ray.rayOrigin, 0.001, ray.rayDir, 10000, 0);

            if (!ray.hit) {
                light += throughPut * (vec3(1.0));
                break;
            } else {
                light += ray.hitMaterial.emissiveColor.rgb * ray.hitMaterial.emissiveColor.a * throughPut;
                throughPut *= ray.hitMaterial.color.rgb;

                //Russian roulette
                float prob = max(throughPut.x, max(throughPut.y, throughPut.z));
                if (rand(gl_LaunchIDEXT.xy * (i + 1) * 0.23523) > prob) {
                    break;
                }
                throughPut /= prob;

                light += getShadow() * throughPut;

                light *= 0.5;

                ray.hit = false;
                ray.rayOrigin = ray.rayPos + ray.hitNormal * 0.001;
                ray.rayDir = getReflectionDir(ray.rayDir, ray.hitNormal, (i+1));
            }
        }

//        light = mix(imageLoad(prevImage, pixelCoord).rgb, light, 1.0 / (cameraInfo.time + 1));
        light = mix(imageLoad(prevImage, pixelCoord).rgb, light, cameraInfo.time == 0 ? 1.0 : (1.0 / (cameraInfo.time + 1)));

    } else {
        if (ray.hitMaterial.emissiveColor.a > 0) {
            light = ray.hitMaterial.emissiveColor.rgb;
        } else {
            light = ray.hitValue;
        }
    }

    imageStore(outImage, pixelCoord, vec4(light, 1.f));
}