// Raymarched cute 3D duck. The whole duck (and its accessories) is built from
// a few smoothly blended shapes, so no 3D model files are needed.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform vec2 uRes;
uniform mat3 uRot;       // world -> duck rotation
uniform float uBob;      // vertical hop / bob offset
uniform vec3 uScale;     // squash and stretch
uniform float uMat;      // 0 glossy, 1 metallic, 2 crystal, 3 fluffy
uniform vec3 uBody;      // body colour
uniform vec3 uHead;      // head colour (differs for two-tone skins)
uniform vec3 uBeak;
uniform float uAcc;      // 0 none, 1 flower, 2 beanie, 3 party hat
uniform vec3 uAccA;
uniform vec3 uAccB;
uniform float uEyeMode;  // 0 open, 1 happy ^ ^, 2 sleepy u u
uniform vec3 uEyeR;      // open-eye radii (blinks and surprise change it)
uniform float uDeco;     // 0 none, 1 heart, 2 Zz, 3 !
uniform float uDecoAmt;  // 0..1 fade / float
uniform float uDecoSide; // +1 right, -1 left

const vec3 HEAD_C = vec3(0.18, 0.28, 0.0);
const float HEAD_R = 0.36;
const vec3 EYE_C = vec3(0.445, 0.368, 0.194);
const vec3 EYE_F = vec3(0.779, 0.260, 0.571);
const vec3 EYE_U = vec3(-0.2097, 0.9657, -0.1538);
const vec3 EYE_S = vec3(0.5914, 0.0, -0.8068);
const vec3 CHEEK_C = vec3(0.396, 0.237, 0.284);

float sdEllipsoid(vec3 p, vec3 r) {
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / k1;
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float sdTorus(vec3 p, float R, float r) {
    return length(vec2(length(p.xz) - R, p.y)) - r;
}

// Cone with its tip at the origin, opening downwards. c = (sin, cos) of half angle.
float sdCone(vec3 p, vec2 c, float h) {
    vec2 q = h * vec2(c.x / c.y, -1.0);
    vec2 w = vec2(length(p.xz), p.y);
    vec2 a = w - q * clamp(dot(w, q) / dot(q, q), 0.0, 1.0);
    vec2 b = w - q * vec2(clamp(w.x / q.x, 0.0, 1.0), 1.0);
    float k = sign(q.y);
    float d = min(dot(a, a), dot(b, b));
    float s = max(k * (w.x * q.y - w.y * q.x), k * (w.y - q.y));
    return sqrt(d) * sign(s);
}

// A curved closed-eye stroke. up = +1 draws ^, up = -1 draws u.
float sdEyeArc(vec3 e, float up) {
    vec2 q = vec2(dot(e, EYE_S), dot(e, EYE_U) * up);
    float w = dot(e, EYE_F) - 0.025;
    vec2 c = vec2(0.0, -0.025);
    const float R = 0.056;
    float d = q.y > c.y ? abs(length(q - c) - R) : length(vec2(abs(q.x) - R, q.y - c.y));
    return length(vec2(d, w)) - 0.018;
}

float sdFlower(vec3 q) {
    const vec3 n = vec3(0.102, 0.820, 0.564);
    const vec3 t1 = vec3(0.995, -0.084, -0.058);
    const vec3 t2 = vec3(-0.0002, 0.567, -0.8245);
    vec2 uv = vec2(dot(q, t1), dot(q, t2));
    float w = dot(q, n);
    const float sector = 6.2831853 / 5.0;
    float a = mod(atan(uv.y, uv.x) + sector * 0.5, sector) - sector * 0.5;
    float r = length(uv);
    vec3 lp = vec3(r * cos(a) - 0.075, r * sin(a), w);
    return sdEllipsoid(lp, vec3(0.075, 0.055, 0.025));
}

// x = distance, y = material (0 body, 1 beak, 2 eye, 3 accessory A, 4 accessory B)
vec2 map(vec3 pw) {
    vec3 p = uRot * (pw - vec3(0.0, uBob, 0.0));
    p /= uScale;

    float d = sdEllipsoid(p - vec3(0.0, -0.22, 0.0), vec3(0.55, 0.36, 0.46));

    vec3 t = p - vec3(-0.46, -0.06, 0.0);
    t = vec3(0.8253 * t.x - 0.5646 * t.y, 0.5646 * t.x + 0.8253 * t.y, t.z);
    d = smin(d, sdEllipsoid(t, vec3(0.18, 0.08, 0.15)), 0.12);

    d = smin(d, length(p - HEAD_C) - HEAD_R, 0.10);

    vec3 w = p - vec3(-0.06, -0.17, 0.0);
    w.z = abs(w.z) - 0.42;
    w = vec3(0.9397 * w.x - 0.3420 * w.y, 0.3420 * w.x + 0.9397 * w.y, w.z);
    d = smin(d, sdEllipsoid(w, vec3(0.22, 0.13, 0.08)), 0.04);

    vec2 res = vec2(d, 0.0);

    vec3 b = p - vec3(0.52, 0.20, 0.0);
    float beak = sdEllipsoid(b, vec3(0.16, 0.065, 0.17));
    beak = smin(beak, sdEllipsoid(b - vec3(-0.01, -0.045, 0.0), vec3(0.12, 0.045, 0.13)), 0.03);
    if (beak < res.x) res = vec2(beak, 1.0);

    vec3 e = vec3(p.x, p.y, abs(p.z)) - EYE_C;
    float eye;
    if (uEyeMode < 0.5) eye = sdEllipsoid(e, uEyeR);
    else eye = sdEyeArc(e, uEyeMode < 1.5 ? 1.0 : -1.0);
    if (eye < res.x) res = vec2(eye, 2.0);

    if (uAcc > 0.5) {
        vec3 h = p - HEAD_C;
        if (uAcc < 1.5) {
            vec3 q = p - vec3(0.217, 0.575, 0.203);
            if (length(q) < 0.2) {
                float petals = sdFlower(q);
                if (petals < res.x) res = vec2(petals, 3.0);
                float centre = length(q - vec3(0.102, 0.820, 0.564) * 0.02) - 0.04;
                if (centre < res.x) res = vec2(centre, 4.0);
            }
        } else if (uAcc < 2.5) {
            if (length(h - vec3(0.0, 0.25, 0.0)) < 0.45) {
                float cap = max(length(h) - 0.385, 0.10 - h.y);
                if (cap < res.x) res = vec2(cap, 3.0);
                float brim = sdTorus(h - vec3(0.0, 0.11, 0.0), 0.345, 0.045);
                float pom = length(h - vec3(-0.04, 0.42, 0.0)) - 0.075;
                float acc = min(brim, pom);
                if (acc < res.x) res = vec2(acc, 4.0);
            }
        } else {
            vec3 q = h - vec3(-0.02, 0.68, 0.0);
            if (length(q - vec3(0.0, -0.2, 0.0)) < 0.32) {
                float cone = sdCone(q, vec2(0.38, 0.925), 0.40);
                if (cone < res.x) res = vec2(cone, 3.0);
                float pom = length(q - vec3(0.0, 0.02, 0.0)) - 0.045;
                if (pom < res.x) res = vec2(pom, 4.0);
            }
        }
    }

    res.x *= min(uScale.x, min(uScale.y, uScale.z));
    return res;
}

vec3 calcNormal(vec3 p) {
    const vec2 k = vec2(1.0, -1.0);
    const float e = 0.0015;
    return normalize(k.xyy * map(p + k.xyy * e).x +
                     k.yyx * map(p + k.yyx * e).x +
                     k.yxy * map(p + k.yxy * e).x +
                     k.xxx * map(p + k.xxx * e).x);
}

// A bright, sunny studio: sky above, a white horizon and soft boxes, darker floor.
vec3 env(vec3 d) {
    float y = d.y;
    float az = atan(d.x, d.z);
    vec3 col = mix(vec3(0.20, 0.24, 0.32), vec3(0.62, 0.78, 1.00), smoothstep(-0.1, 0.9, y));
    col = mix(vec3(0.10, 0.09, 0.08), col, smoothstep(-0.06, 0.0, y));
    col += vec3(1.6) * exp(-abs(y - 0.02) * 22.0);
    float above = smoothstep(-0.05, 0.15, y);
    col += vec3(3.2) * (1.0 - smoothstep(0.10, 0.22, abs(az + 0.9))) * above;
    col += vec3(2.2) * (1.0 - smoothstep(0.05, 0.12, abs(az - 1.2))) * above;
    col += vec3(2.6) * smoothstep(0.80, 0.97, y);
    col += vec3(0.4) * smoothstep(0.2, 1.0, d.z);
    return col;
}

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

// Returns colour in .rgb and opacity in .a (only crystal is see-through).
vec4 shade(vec3 pos, vec3 n, vec3 rd, float id) {
    vec3 op = uRot * (pos - vec3(0.0, uBob, 0.0)) / uScale;
    float ao = 0.55 + 0.45 * clamp(map(pos + n * 0.06).x / 0.06, 0.0, 1.0);
    vec3 L = normalize(vec3(-0.5, 0.8, 0.6));
    float type = uMat;

    vec3 base;
    if (id < 0.5) {
        base = mix(uBody, uHead, smoothstep(0.0, 0.12, op.y));
    } else if (id < 1.5) {
        base = uBeak;
        if (type > 1.5) type = 0.0; // crystal and fluffy ducks keep a glossy beak
    } else if (id < 2.5) {
        base = vec3(0.03);
        type = 0.0;
    } else {
        base = id < 3.5 ? uAccA : uAccB;
        if (uAcc > 2.5 && id < 3.5) {
            // Party hat stripes.
            base = mix(uAccA, uAccB, step(0.5, fract(op.y * 9.0)));
        }
        if (type > 1.5) type = 0.0;
        if (uAcc > 1.5 && type > 0.5) type = 0.0;
    }

    // Rosy cheeks on everything but metal and crystal.
    if (id < 0.5 && uMat != 1.0 && uMat != 2.0) {
        vec3 cp = vec3(op.x, op.y, abs(op.z));
        float blush = 1.0 - smoothstep(0.03, 0.085, length(cp - CHEEK_C));
        base = mix(base, vec3(1.0, 0.45, 0.50), blush * 0.6);
    }
    base = pow(base, vec3(2.2)); // colours arrive in sRGB; light in linear

    if (type > 2.5) {
        // Fluffy: jitter the normal for a soft fuzzy surface and add sheen.
        vec3 j = vec3(hash(op * 41.0), hash(op * 43.0 + 3.1), hash(op * 47.0 + 7.3)) - 0.5;
        n = normalize(n + j * 0.35);
    }

    vec3 r = reflect(rd, n);
    float ndv = clamp(dot(n, -rd), 0.0, 1.0);
    float ndl = dot(n, L);
    float spec = pow(clamp(dot(r, L), 0.0, 1.0), 60.0);
    vec3 col;
    float alpha = 1.0;

    if (type < 0.5) {
        // Glossy vinyl, like a real bath duck.
        float diff = clamp(ndl * 0.6 + 0.4, 0.0, 1.0);
        float sky = 0.5 + 0.5 * n.y;
        vec3 dif = base * (0.40 * sky * vec3(0.85, 0.92, 1.05) + 1.0 * diff);
        float fre = 0.04 + 0.96 * pow(1.0 - ndv, 5.0);
        col = mix(dif, env(r), fre * 0.55) + spec * 0.9;
        if (id > 1.5 && id < 2.5) {
            // Two sparkles in each eye.
            col += vec3(3.0) * smoothstep(0.90, 0.93, dot(n, normalize(vec3(0.25, 0.75, 0.65))));
            col += vec3(2.0) * smoothstep(0.965, 0.98, dot(n, normalize(vec3(0.9, -0.1, 0.45))));
        }
    } else if (type < 1.5) {
        vec3 fre = base + (1.0 - base) * pow(1.0 - ndv, 5.0);
        col = env(r) * fre * 1.25 + base * 0.05 + spec * (base * 2.0 + 0.5);
    } else if (type < 2.5) {
        // Crystal: clear glass with a rainbow film.
        float fre = 0.06 + 0.94 * pow(1.0 - ndv, 4.0);
        vec3 irid = 0.5 + 0.5 * cos(6.2831853 * (1.7 * ndv + op.y * 0.6 + vec3(0.0, 0.33, 0.67)));
        vec3 through = env(refract(rd, n, 0.72)) * mix(vec3(1.0), base, 0.5);
        col = through * 0.45 + irid * 0.30 + env(r) * fre + spec * 1.5;
        alpha = clamp(0.40 + 0.60 * fre + 0.25 * (1.0 - ndv), 0.0, 1.0);
    } else {
        float diff = clamp(ndl * 0.5 + 0.5, 0.0, 1.0);
        col = base * (0.30 + 0.85 * diff);
        col += base * 0.55 * pow(1.0 - ndv, 2.0); // velvet sheen at the edges
    }
    col *= ao;
    col = 1.0 - exp(-col * 1.5);
    return vec4(pow(col, vec3(0.4545)), alpha);
}

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
    vec3 ro = vec3(0.0, 0.30, 3.4);
    vec3 ww = normalize(vec3(0.0, 0.10, 0.0) - ro);
    vec3 uu = normalize(cross(ww, vec3(0.0, 1.0, 0.0)));
    vec3 vv = cross(uu, ww);
    const float fl = 1.65;
    vec3 rd = normalize(uv.x * uu + uv.y * vv + fl * ww);
    float px = 1.0 / (uRes.y * fl);

    // Soft contact shadow under the duck.
    vec4 outc = vec4(0.0);
    if (rd.y < 0.0) {
        float tp = (-0.62 - ro.y) / rd.y;
        vec3 pp = ro + rd * tp;
        float r = length(pp.xz * vec2(1.0, 1.4));
        outc = vec4(0.0, 0.0, 0.0, 0.20 * (1.0 - smoothstep(0.15, 0.75, r)));
    }

    // Only march rays that enter the duck's bounding sphere.
    vec3 oc = ro - vec3(0.0, 0.08, 0.0);
    float b = dot(oc, rd);
    float h = b * b - (dot(oc, oc) - 1.0);
    if (h > 0.0) {
        h = sqrt(h);
        float t = -b - h;
        float tmax = -b + h;
        float minRatio = 1e5;
        float tMin = t;
        float mat = 0.0;
        bool hit = false;
        for (int i = 0; i < 90; i++) {
            vec2 m = map(ro + rd * t);
            float ratio = m.x / t;
            if (ratio < minRatio) { minRatio = ratio; tMin = t; mat = m.y; }
            if (m.x < 0.0008 * t) { hit = true; tMin = t; mat = m.y; break; }
            t += m.x;
            if (t > tmax) break;
        }
        // Rays that just miss still get partial coverage: smooth edges.
        float edge = uMat > 2.5 ? 3.5 : 1.5;
        float cover = hit ? 1.0 : 1.0 - smoothstep(0.0, edge * px, minRatio);
        if (cover > 0.0) {
            vec3 pos = ro + rd * tMin;
            vec4 s = shade(pos, calcNormal(pos), rd, mat);
            float a = cover * s.a;
            outc = vec4(s.rgb * a, a) + outc * (1.0 - a);
        }
    }

    vec4 dc = deco(uv, px * 1.5);
    gl_FragColor = dc + outc * (1.0 - dc.a);
}
