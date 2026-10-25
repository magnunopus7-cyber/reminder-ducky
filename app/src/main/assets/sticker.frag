#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

// Picture ducks: a flat image on a transparent background that bobs,
// tilts and squishes like the 3D duck, with the same mood symbols.
uniform vec2 uRes;
uniform sampler2D uTex;
uniform float uBob;      // vertical hop, in screen units
uniform float uRoll;     // tilt around the bottom centre
uniform vec2 uScale;     // squash and stretch
uniform float uFlip;     // -1 mirrors the picture
uniform float uDeco;     // 0 none, 1 heart, 2 Zz, 3 !
uniform float uDecoAmt;
uniform float uDecoSide;

const float SIZE = 0.80;
const float FLOOR = -0.42;

float sdHeart(vec2 p) {
    p.x = abs(p.x);
    if (p.y + p.x > 1.0) return length(p - vec2(0.25, 0.75)) - 0.35355;
    vec2 a = p - vec2(0.0, 1.0);
    vec2 b = p - 0.5 * max(p.x + p.y, 0.0);
    return sqrt(min(dot(a, a), dot(b, b))) * sign(p.x - p.y);
}

float sdSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0));
}

float sdZ(vec2 p, float s) {
    float d = sdSeg(p, vec2(-s, s), vec2(s, s));
    d = min(d, sdSeg(p, vec2(s, s), vec2(-s, -s)));
    return min(d, sdSeg(p, vec2(-s, -s), vec2(s, -s)));
}

// Little floating symbols above the duck's head.
vec4 deco(vec2 uv, float px) {
    if (uDeco < 0.5 || uDecoAmt <= 0.0) return vec4(0.0);
    vec2 c = vec2(0.34 * uDecoSide, 0.30 + 0.06 * uDecoAmt);
    vec2 q = uv - c;
    float d;
    vec3 col;
    if (uDeco < 1.5) {
        d = sdHeart(q / 0.11 + vec2(0.0, 0.5)) * 0.11;
        col = vec3(1.0, 0.42, 0.58);
    } else if (uDeco < 2.5) {
        d = min(sdZ(q, 0.035), sdZ(q - vec2(0.09 * uDecoSide, 0.09), 0.025)) - 0.008;
        col = vec3(0.45, 0.62, 0.95);
    } else {
        d = min(sdSeg(q, vec2(0.0, 0.11), vec2(0.0, 0.0)) - 0.022, length(q + vec2(0.0, 0.05)) - 0.024);
        col = vec3(1.0, 0.35, 0.35);
    }
    float a = (1.0 - smoothstep(-px, px, d)) * clamp(uDecoAmt * 1.5, 0.0, 1.0);
    return vec4(col * a, a);
}

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * uRes) / uRes.y;
    float px = 1.0 / uRes.y;

    // Soft shadow on the ground; it shrinks as the duck hops up.
    vec2 sp = (uv - vec2(0.0, FLOOR + 0.01)) * vec2(1.0, 5.0);
    float shadow = 0.16 * (1.0 - smoothstep(0.05, 0.30 - uBob, length(sp)));
    vec4 outc = vec4(0.0, 0.0, 0.0, shadow);

    vec2 p = uv - vec2(0.0, FLOOR + uBob);
    float c = cos(uRoll), s = sin(uRoll);
    p = vec2(c * p.x + s * p.y, -s * p.x + c * p.y);
    p /= uScale;
    vec2 t = vec2(0.5 + uFlip * p.x / SIZE, 1.0 - p.y / SIZE);
    if (t.x >= 0.0 && t.x <= 1.0 && t.y >= 0.0 && t.y <= 1.0) {
        // Some pictures are cut off at the bottom: fade that edge softly.
        vec4 img = texture2D(uTex, t) * smoothstep(1.0, 0.93, t.y);   // premultiplied
        outc = img + outc * (1.0 - img.a);
    }

    vec4 dc = deco(uv, px * 1.5);
    gl_FragColor = dc + outc * (1.0 - dc.a);
}
