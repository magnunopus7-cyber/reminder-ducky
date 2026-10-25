// Raymarched metallic rubber duck. The whole duck is built from a few
// smoothly blended ellipsoids, so no 3D model files are needed.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform vec2 uRes;
uniform mat3 uRot;     // world -> duck rotation
uniform float uBob;    // vertical bob offset
uniform vec3 uScale;   // squash and stretch
uniform vec3 uBody;    // metal colour (F0) of the body
uniform vec3 uBeak;    // metal colour (F0) of the beak

float sdEllipsoid(vec3 p, vec3 r) {
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / k1;
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

// x = distance, y = material (0 body, 1 beak, 2 eye)
vec2 map(vec3 pw) {
    vec3 p = uRot * (pw - vec3(0.0, uBob, 0.0));
    p /= uScale;

    float d = sdEllipsoid(p - vec3(0.0, -0.18, 0.0), vec3(0.62, 0.40, 0.48));

    vec3 t = p - vec3(-0.50, 0.02, 0.0);
    t = vec3(0.8253 * t.x - 0.5646 * t.y, 0.5646 * t.x + 0.8253 * t.y, t.z);
    d = smin(d, sdEllipsoid(t, vec3(0.24, 0.10, 0.20)), 0.14);

    float head = length(p - vec3(0.30, 0.38, 0.0)) - 0.32;
    d = smin(d, head, 0.12);

    vec3 w = p - vec3(-0.10, -0.08, 0.0);
    w.z = abs(w.z) - 0.40;
    w = vec3(0.9689 * w.x - 0.2474 * w.y, 0.2474 * w.x + 0.9689 * w.y, w.z);
    d = smin(d, sdEllipsoid(w, vec3(0.30, 0.15, 0.09)), 0.05);

    vec2 res = vec2(d, 0.0);

    float beak = sdEllipsoid(p - vec3(0.62, 0.32, 0.0), vec3(0.20, 0.07, 0.16));
    if (beak < res.x) res = vec2(beak, 1.0);

    vec3 e = p - vec3(0.49, 0.50, 0.0);
    e.z = abs(e.z) - 0.19;
    float eye = length(e) - 0.055;
    if (eye < res.x) res = vec2(eye, 2.0);

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

// A little photo studio for the metal to reflect: dark walls, strip
// lights and a bright horizon. High contrast is what makes metal read as metal.
vec3 env(vec3 d) {
    float y = d.y;
    float az = atan(d.x, d.z);
    vec3 col = mix(vec3(0.03, 0.03, 0.04), vec3(0.30, 0.32, 0.38), smoothstep(-0.1, 1.0, y));
    col = mix(vec3(0.07, 0.06, 0.05), col, smoothstep(-0.06, 0.0, y));
    col += vec3(1.8) * exp(-abs(y - 0.02) * 22.0);
    float above = smoothstep(-0.05, 0.15, y);
    col += vec3(4.0) * (1.0 - smoothstep(0.10, 0.22, abs(az + 0.9))) * above;
    col += vec3(2.6, 2.5, 2.3) * (1.0 - smoothstep(0.05, 0.12, abs(az - 1.2))) * above;
    col += vec3(3.0) * smoothstep(0.80, 0.97, y);
    col += vec3(0.5) * smoothstep(0.2, 1.0, d.z);
    return col;
}

vec3 shade(vec3 p, vec3 n, vec3 rd, float mat) {
    vec3 r = reflect(rd, n);
    float ndv = clamp(dot(n, -rd), 0.0, 1.0);
    float ao = 0.5 + 0.5 * clamp(map(p + n * 0.06).x / 0.06, 0.0, 1.0);

    vec3 f0 = mat < 0.5 ? uBody : (mat < 1.5 ? uBeak : vec3(0.04));
    vec3 fre = f0 + (1.0 - f0) * pow(1.0 - ndv, 5.0);
    vec3 col = env(r) * fre * 1.3;
    col += mat > 1.5 ? vec3(0.01) : f0 * 0.06;

    vec3 L = normalize(vec3(-0.6, 0.8, 0.6));
    float spec = pow(clamp(dot(r, L), 0.0, 1.0), 60.0);
    col += spec * (mat > 1.5 ? vec3(1.5) : f0 * 2.0 + 0.5);
    col *= ao;

    col = col / (1.0 + col);
    return pow(col, vec3(0.4545));
}

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * uRes) / uRes.y;
    vec3 ro = vec3(0.0, 0.25, 3.2);
    vec3 ww = normalize(vec3(0.0, 0.02, 0.0) - ro);
    vec3 uu = normalize(cross(ww, vec3(0.0, 1.0, 0.0)));
    vec3 vv = cross(uu, ww);
    const float fl = 1.75;
    vec3 rd = normalize(uv.x * uu + uv.y * vv + fl * ww);
    float px = 1.0 / (uRes.y * fl);

    // Soft contact shadow under the duck.
    vec4 outc = vec4(0.0);
    if (rd.y < 0.0) {
        float tp = (-0.62 - ro.y) / rd.y;
        vec3 pp = ro + rd * tp;
        float r = length(pp.xz * vec2(1.0, 1.4));
        outc = vec4(0.0, 0.0, 0.0, 0.22 * (1.0 - smoothstep(0.15, 0.75, r)));
    }

    // Only march rays that enter the duck's bounding sphere.
    float b = dot(ro, rd);
    float h = b * b - (dot(ro, ro) - 1.0);
    if (h > 0.0) {
        h = sqrt(h);
        float t = -b - h;
        float tmax = -b + h;
        float minRatio = 1e5;
        float tMin = t;
        float mat = 0.0;
        bool hit = false;
        for (int i = 0; i < 80; i++) {
            vec2 m = map(ro + rd * t);
            float ratio = m.x / t;
            if (ratio < minRatio) { minRatio = ratio; tMin = t; mat = m.y; }
            if (m.x < 0.0008 * t) { hit = true; tMin = t; mat = m.y; break; }
            t += m.x;
            if (t > tmax) break;
        }
        // Rays that just miss still get partial coverage: smooth edges.
        float alpha = hit ? 1.0 : 1.0 - smoothstep(0.0, 1.5 * px, minRatio);
        if (alpha > 0.0) {
            vec3 pos = ro + rd * tMin;
            vec3 col = shade(pos, calcNormal(pos), rd, mat);
            outc = vec4(col * alpha, alpha) + outc * (1.0 - alpha);
        }
    }
    gl_FragColor = outc;
}
