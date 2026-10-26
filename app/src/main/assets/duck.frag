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
uniform float uAcc;      // 0 none, 1 flower, 2 beanie, 3 party hat, 4 nightcap, 5 sprout,
                         // 6 heart shades, 7 headphones, 8 cape, 9 cap, 10 frog hood,
                         // 11 bear hood, 12 sunhat, 13 bandana, 14 snorkel, 15 backpack,
                         // 16 sailor, 17 duckling, 18 heart hug, 19 bubble bath, 20 bookworm, 21 cloud
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

// A tube from a to b whose radius goes from r1 to r2.
float sdTaper(vec3 p, vec3 a, vec3 b, float r1, float r2) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - mix(r1, r2, h);
}

float sdRoundBox(vec3 p, vec3 b, float r) {
    vec3 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}

float sdRoundRect(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// A hood around the head with an opening for the face (h is relative to the head centre).
float sdHood(vec3 h) {
    float shell = length(h - vec3(-0.03, 0.01, 0.0)) - 0.415;
    float face = sdEllipsoid(h - vec3(0.34, -0.03, 0.0), vec3(0.30, 0.29, 0.31));
    return max(max(shell, -face), -0.27 - h.y);
}

// A mini rubber duck for the duckling friend (q in its own little space).
vec2 miniDuck(vec3 q) {
    float d = sdEllipsoid(q - vec3(0.0, -0.22, 0.0), vec3(0.55, 0.36, 0.46));
    d = smin(d, length(q - HEAD_C) - HEAD_R, 0.10);
    vec2 r = vec2(d, 3.0);
    float beak = sdEllipsoid(q - vec3(0.52, 0.20, 0.0), vec3(0.16, 0.07, 0.17));
    if (beak < r.x) r = vec2(beak, 4.0);
    float eye = sdEllipsoid(vec3(q.x, q.y, abs(q.z)) - EYE_C, vec3(0.07, 0.1, 0.06));
    if (eye < r.x) r = vec2(eye, 2.0);
    return r;
}

// Puffs spread around a ring: n of them at radius rad and height y.
float puffRing(vec3 p, float n, float rad, float y, float size) {
    float sec = 6.2831853 / n;
    float a = atan(p.z, p.x);
    float k = floor(a / sec + 0.5);
    float aa = a - k * sec;
    vec3 lp = vec3(length(p.xz) * cos(aa), p.y, length(p.xz) * sin(aa));
    float wob = 0.75 + 0.5 * fract(sin(k * 12.9898) * 43758.5453);
    return length(lp - vec3(rad, y, 0.0)) - size * wob;
}

float sdHeart2(vec2 p) {
    p.x = abs(p.x);
    if (p.y + p.x > 1.0) return length(p - vec2(0.25, 0.75)) - 0.35355;
    vec2 a = p - vec2(0.0, 1.0);
    vec2 b = p - 0.5 * max(p.x + p.y, 0.0);
    return sqrt(min(dot(a, a), dot(b, b))) * sign(p.x - p.y);
}

float sdStar5(vec2 p, float r, float rf) {
    const vec2 k1 = vec2(0.809016994375, -0.587785252292);
    const vec2 k2 = vec2(-0.809016994375, -0.587785252292);
    p.x = abs(p.x);
    p -= 2.0 * max(dot(k1, p), 0.0) * k1;
    p -= 2.0 * max(dot(k2, p), 0.0) * k2;
    p.x = abs(p.x);
    p.y -= r;
    vec2 ba = rf * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
    float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
    return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
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
    // The superhero cape covers the tail, so leave it out.
    if (abs(uAcc - 8.0) > 0.5) d = smin(d, sdEllipsoid(t, vec3(0.18, 0.08, 0.15)), 0.12);

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
        } else if (uAcc < 3.5) {
            vec3 q = h - vec3(-0.02, 0.68, 0.0);
            if (length(q - vec3(0.0, -0.2, 0.0)) < 0.32) {
                float cone = sdCone(q, vec2(0.38, 0.925), 0.40);
                if (cone < res.x) res = vec2(cone, 3.0);
                float pom = length(q - vec3(0.0, 0.02, 0.0)) - 0.045;
                if (pom < res.x) res = vec2(pom, 4.0);
            }
        } else if (uAcc < 4.5) {
            // Nightcap: a soft starry cap whose tip flops down the back.
            if (length(h - vec3(-0.2, 0.25, 0.0)) < 0.62) {
                float cap = max(length(h) - 0.385, 0.06 - h.y);
                cap = smin(cap, sdTaper(h, vec3(-0.02, 0.30, 0.0), vec3(-0.30, 0.40, 0.04), 0.20, 0.10), 0.06);
                cap = smin(cap, sdTaper(h, vec3(-0.30, 0.40, 0.04), vec3(-0.50, 0.22, 0.10), 0.10, 0.045), 0.04);
                float band = sdTorus(h - vec3(0.0, 0.07, 0.0), 0.35, 0.05);
                cap = min(cap, band);
                if (cap < res.x) res = vec2(cap, 3.0);
                float pom = length(h - vec3(-0.52, 0.17, 0.11)) - 0.065;
                if (pom < res.x) res = vec2(pom, 4.0);
            }
        } else if (uAcc < 5.5) {
            // Sprout: a little stem with two leaves.
            vec3 q = h - vec3(-0.02, 0.34, 0.0);
            if (length(q - vec3(0.0, 0.16, 0.0)) < 0.3) {
                float stem = sdTaper(q, vec3(0.0), vec3(0.0, 0.17, 0.0), 0.034, 0.024);
                if (stem < res.x) res = vec2(stem, 4.0);
                vec3 l = q - vec3(0.0, 0.18, 0.0);
                l.z = abs(l.z);
                l -= vec3(0.0, 0.045, 0.105);
                l = vec3(l.x, 0.8776 * l.y - 0.4794 * l.z, 0.4794 * l.y + 0.8776 * l.z);
                float leaf = sdEllipsoid(l, vec3(0.075, 0.026, 0.12));
                if (leaf < res.x) res = vec2(leaf, 3.0);
            }
        } else if (uAcc < 6.5) {
            // Heart-shaped sunglasses in front of the eyes.
            vec3 g = vec3(p.x, p.y, abs(p.z));
            if (length(g - vec3(0.35, 0.38, 0.2)) < 0.32) {
                vec3 e2 = g - (EYE_C + EYE_F * 0.075 - EYE_U * 0.01);
                vec2 uv = vec2(dot(e2, EYE_S), dot(e2, EYE_U));
                float w = dot(e2, EYE_F);
                float hd = sdHeart2(uv / 0.19 + vec2(0.0, 0.55)) * 0.19;
                float lens = max(hd, abs(w) - 0.012);
                if (lens < res.x) res = vec2(lens, 4.0);
                float rim = max(abs(hd) - 0.022, abs(w) - 0.028);
                vec3 outer = EYE_C + EYE_F * 0.075 - EYE_S * 0.17;
                rim = min(rim, sdTaper(g, outer, vec3(-0.02, 0.40, 0.38), 0.02, 0.016));
                rim = min(rim, sdTaper(g, vec3(0.56, 0.42, 0.10), vec3(0.56, 0.42, 0.0), 0.02, 0.02));
                if (rim < res.x) res = vec2(rim, 3.0);
            }
        } else if (uAcc < 7.5) {
            // Headphones: a band over the head and a cup on each side.
            vec3 q = h - vec3(-0.03, 0.0, 0.0);
            vec3 qa = vec3(q.x, q.y, abs(q.z));
            if (length(q) < 0.56) {
                float band = max(length(vec2(length(q.yz) - 0.41, q.x)) - 0.032, -q.y);
                if (band < res.x) res = vec2(band, 4.0);
                vec3 c = qa - vec3(0.0, 0.02, 0.40);
                float cup = sdEllipsoid(c, vec3(0.12, 0.13, 0.07));
                cup = min(cup, sdTaper(qa, vec3(0.0, 0.0, 0.41), vec3(0.0, 0.12, 0.41), 0.03, 0.03));
                if (cup < res.x) res = vec2(cup, 3.0);
                float pad = sdEllipsoid(c + vec3(0.0, 0.0, 0.05), vec3(0.10, 0.11, 0.04));
                if (pad < res.x) res = vec2(pad, 4.0);
            }
        } else if (uAcc < 8.5) {
            // Superhero: a scarf with a knot and a cape down the back.
            if (length(p - vec3(-0.05, -0.12, 0.0)) < 0.95) {
                // The cape drapes from the neck over a slightly bigger body shape.
                float hull = sdEllipsoid(p - vec3(-0.02, -0.25, 0.0), vec3(0.70, 0.40, 0.57));
                hull = smin(hull, sdTaper(p, vec3(0.08, -0.10, 0.0), vec3(0.10, 0.16, 0.0), 0.30, 0.25), 0.35);
                // Solid between the body and the hull, so there are no gaps to see through.
                float cape = max(hull, 0.01 - sdEllipsoid(p - vec3(0.0, -0.22, 0.0), vec3(0.55, 0.36, 0.46)));
                float back = p.x + 0.04 - 0.2 * (1.0 - smoothstep(-0.4, 0.1, p.y));
                cape = max(cape, max(back, max(p.y - 0.18, -0.46 - p.y)));
                if (cape < res.x) res = vec2(cape, 3.0);
                vec3 n = p - vec3(0.13, 0.06, 0.0);
                n = vec3(0.9553 * n.x + 0.2955 * n.y, -0.2955 * n.x + 0.9553 * n.y, n.z);
                float scarf = sdEllipsoid(vec3(length(n.xz) - 0.33, n.y, 0.0), vec3(0.08, 0.065, 1.0));
                vec3 k = p - vec3(0.47, 0.02, 0.0);
                float knot = length(k) - 0.055;
                vec3 kz = vec3(k.x, k.y, abs(k.z) - 0.07);
                knot = min(knot, sdEllipsoid(kz, vec3(0.04, 0.05, 0.065)));
                scarf = min(scarf, knot);
                if (scarf < res.x) res = vec2(scarf, 4.0);
            }
        } else if (uAcc < 9.5) {
            // Cap: a round dome with a short visor and a button on top.
            if (length(h - vec3(0.0, 0.25, 0.0)) < 0.5) {
                float cap = max(length(h * vec3(1.0, 0.92, 1.0)) - 0.39, 0.15 - h.y);
                vec3 b = h - vec3(0.0, 0.16, 0.0);
                float brim = length(vec2(length(b.xz) - 0.35, b.y * 1.3)) - 0.06;
                cap = smin(cap, brim, 0.03);
                if (cap < res.x) res = vec2(cap, 3.0);
                float button = length(h - vec3(0.0, 0.425, 0.0)) - 0.04;
                if (button < res.x) res = vec2(button, 4.0);
            }
        } else if (uAcc < 10.5) {
            // Frog hood: a green hood with big frog eyes on top.
            if (length(h - vec3(0.0, 0.08, 0.0)) < 0.6) {
                float hood = sdHood(h);
                vec3 e = vec3(h.x, h.y, abs(h.z)) - vec3(0.03, 0.38, 0.17);
                hood = smin(hood, length(e) - 0.13, 0.05);
                if (hood < res.x) res = vec2(hood, 3.0);
                float white = length(e - vec3(0.07, 0.02, 0.0)) - 0.09;
                if (white < res.x) res = vec2(white, 4.0);
                float pupil = length(e - vec3(0.15, 0.03, 0.0)) - 0.045;
                if (pupil < res.x) res = vec2(pupil, 2.0);
            }
        } else if (uAcc < 11.5) {
            // Bear hood: a soft cream hood with round ears.
            if (length(h - vec3(0.0, 0.08, 0.0)) < 0.6) {
                float hood = sdHood(h);
                vec3 e = vec3(h.x, h.y, abs(h.z)) - vec3(-0.06, 0.36, 0.25);
                float ear = sdEllipsoid(e, vec3(0.06, 0.11, 0.11));
                hood = smin(hood, ear, 0.04);
                if (hood < res.x) res = vec2(hood, 3.0);
                float inner = sdEllipsoid(e - vec3(0.045, 0.0, 0.0), vec3(0.03, 0.07, 0.07));
                if (inner < res.x) res = vec2(inner, 4.0);
            }
        } else if (uAcc < 12.5) {
            // Sunhat: a straw crown, a wide tilted brim and a ribbon.
            vec3 q = h - vec3(-0.02, 0.20, 0.0);
            q = vec3(0.9801 * q.x + 0.1987 * q.y, -0.1987 * q.x + 0.9801 * q.y, q.z);
            if (length(q) < 0.72) {
                float crown = max(sdEllipsoid(q - vec3(0.0, 0.02, 0.0), vec3(0.33, 0.25, 0.33)), -0.04 - q.y);
                float brim = sdEllipsoid(q + vec3(0.0, 0.03, 0.0), vec3(0.55, 0.03, 0.54));
                float hat = smin(crown, brim, 0.04);
                if (hat < res.x) res = vec2(hat, 3.0);
                float ribbon = max(abs(sdEllipsoid(q - vec3(0.0, 0.02, 0.0), vec3(0.335, 0.255, 0.335))) - 0.012,
                                   abs(q.y - 0.05) - 0.035);
                if (ribbon < res.x) res = vec2(ribbon, 4.0);
            }
        } else if (uAcc < 13.5) {
            // Bandana: a dotted scarf over the top of the head, knotted at the back.
            if (length(h - vec3(-0.1, 0.12, 0.0)) < 0.6) {
                float cover = max(length(h) - 0.385, dot(h, vec3(0.85, -0.53, 0.0)) - 0.03);
                cover = max(cover, -0.02 - h.y);
                vec3 k = h - vec3(-0.33, 0.02, 0.20);
                float knot = length(k) - 0.06;
                knot = min(knot, sdTaper(k, vec3(0.0), vec3(-0.12, -0.06, 0.14), 0.05, 0.02));
                knot = min(knot, sdTaper(k, vec3(0.0), vec3(-0.15, 0.03, 0.06), 0.05, 0.02));
                if (min(cover, knot) < res.x) res = vec2(min(cover, knot), 3.0);
            }
        } else if (uAcc < 14.5) {
            // Snorkel: a pink mask frame around the eyes, a strap and a breathing tube.
            if (length(h - vec3(0.1, 0.1, 0.0)) < 0.62) {
                float rr = length(h.xz);
                vec2 sv = vec2(atan(h.z, h.x) * 0.38, h.y - 0.09);
                float box = sdRoundRect(sv, vec2(0.26, 0.11), 0.08);
                float frame = max(abs(box) - 0.022, abs(rr - 0.37) - 0.03);
                float strap = max(max(abs(sv.y) - 0.03, abs(rr - 0.37) - 0.015), h.x + 0.05);
                float tube = sdTaper(h, vec3(0.12, -0.20, 0.33), vec3(0.02, 0.48, 0.30), 0.035, 0.035);
                float mask = min(min(frame, strap), tube);
                if (mask < res.x) res = vec2(mask, 3.0);
                float mouth = sdEllipsoid(h - vec3(0.13, -0.22, 0.31), vec3(0.06, 0.05, 0.06));
                if (mouth < res.x) res = vec2(mouth, 4.0);
            }
        } else if (uAcc < 15.5) {
            // Backpack: a rounded pack on the back with a pocket and shoulder straps.
            if (length(p - vec3(-0.35, -0.08, 0.0)) < 0.6) {
                float pack = sdRoundBox(p - vec3(-0.56, -0.10, 0.0), vec3(0.13, 0.22, 0.25), 0.09);
                vec3 s = vec3(p.x, p.y, abs(p.z));
                float strap = sdTaper(s, vec3(-0.45, 0.05, 0.18), vec3(-0.05, 0.08, 0.28), 0.03, 0.03);
                strap = min(strap, sdTaper(s, vec3(-0.05, 0.08, 0.28), vec3(0.30, -0.05, 0.31), 0.03, 0.03));
                pack = min(pack, strap);
                if (pack < res.x) res = vec2(pack, 3.0);
                float pocket = sdRoundBox(p - vec3(-0.68, -0.18, 0.0), vec3(0.04, 0.11, 0.17), 0.04);
                if (pocket < res.x) res = vec2(pocket, 4.0);
            }
        } else if (uAcc < 16.5) {
            // Sailor: a tilted beret with a button, and a neckerchief.
            if (length(p - vec3(0.12, 0.25, 0.0)) < 0.75) {
                vec3 q = h - vec3(-0.04, 0.27, 0.0);
                q = vec3(0.9553 * q.x + 0.2955 * q.y, -0.2955 * q.x + 0.9553 * q.y, q.z);
                float beret = sdEllipsoid(q, vec3(0.40, 0.13, 0.40));
                beret = smin(beret, sdTorus(q + vec3(0.0, 0.07, 0.0), 0.31, 0.05), 0.04);
                vec3 n = p - vec3(0.13, 0.06, 0.0);
                n = vec3(0.9553 * n.x + 0.2955 * n.y, -0.2955 * n.x + 0.9553 * n.y, n.z);
                float scarf = sdEllipsoid(vec3(length(n.xz) - 0.33, n.y, 0.0), vec3(0.08, 0.065, 1.0));
                vec3 k = vec3(p.x, p.y, abs(p.z)) - vec3(0.46, -0.04, 0.06);
                scarf = min(scarf, sdTaper(k, vec3(0.0), vec3(0.02, -0.14, 0.05), 0.05, 0.03));
                float blue = min(beret, scarf);
                if (blue < res.x) res = vec2(blue, 3.0);
                float button = length(q - vec3(0.06, 0.13, 0.0)) - 0.045;
                if (button < res.x) res = vec2(button, 4.0);
            }
        } else if (uAcc < 17.5) {
            // Duckling friend: a tiny duck riding on top of the head.
            vec3 q = (h - vec3(-0.02, 0.47, 0.0)) / 0.32;
            if (length(q) < 1.2) {
                vec2 m = miniDuck(q);
                m.x *= 0.32;
                if (m.x < res.x) res = m;
            }
        } else if (uAcc < 18.5) {
            // A puffy heart hugged in front of the tummy.
            vec3 q = p - vec3(0.58, -0.15, 0.0);
            if (length(q) < 0.36) {
                float hd = sdHeart2(vec2(q.z, q.y) / 0.27 + vec2(0.0, 0.5)) * 0.27;
                vec2 w = vec2(hd + 0.03, abs(q.x) - 0.045);
                float heart = length(max(w, 0.0)) + min(max(w.x, w.y), 0.0) - 0.04;
                if (heart < res.x) res = vec2(heart, 3.0);
            }
        } else if (uAcc < 19.5) {
            // Bubble bath: a pool of water with foam all around.
            if (length(p - vec3(0.0, -0.5, 0.0)) < 0.95) {
                float water = sdEllipsoid(p - vec3(0.0, -0.55, 0.0), vec3(0.80, 0.07, 0.72));
                if (water < res.x) res = vec2(water, 3.0);
                float foam = puffRing(p, 9.0, 0.60, -0.46, 0.12);
                foam = smin(foam, puffRing(p + vec3(0.0, 0.0, 0.0), 7.0, 0.70, -0.52, 0.10), 0.04);
                if (foam < res.x) res = vec2(foam, 4.0);
            }
        } else if (uAcc < 20.5) {
            // Bookworm: round glasses and an open book held in front.
            vec3 g = vec3(p.x, p.y, abs(p.z));
            vec3 e2 = g - (EYE_C + EYE_F * 0.07);
            vec2 uv = vec2(dot(e2, EYE_S), dot(e2, EYE_U));
            float w = dot(e2, EYE_F);
            float ring = max(abs(length(uv) - 0.11) - 0.014, abs(w) - 0.014);
            vec3 outer = EYE_C + EYE_F * 0.07 - EYE_S * 0.12;
            ring = min(ring, sdTaper(g, outer, vec3(-0.02, 0.40, 0.38), 0.014, 0.012));
            ring = min(ring, sdTaper(g, vec3(0.555, 0.41, 0.075), vec3(0.555, 0.41, 0.0), 0.014, 0.014));
            if (ring < res.x) res = vec2(ring, 4.0);
            vec3 b = vec3(p.x, p.y, abs(p.z)) - vec3(0.60, -0.16, 0.0);
            b = vec3(0.866 * b.x + 0.5 * b.z, b.y, -0.5 * b.x + 0.866 * b.z);
            float book = sdRoundBox(b - vec3(0.0, 0.0, 0.11), vec3(0.018, 0.13, 0.11), 0.012);
            if (book < res.x) res = vec2(book, 3.0);
        } else {
            // Cloud nap: a soft cloud to doze on.
            if (length(p - vec3(0.0, -0.5, 0.0)) < 0.95) {
                float cloud = puffRing(p, 8.0, 0.52, -0.48, 0.17);
                cloud = smin(cloud, sdEllipsoid(p - vec3(0.0, -0.56, 0.0), vec3(0.70, 0.12, 0.62)), 0.08);
                cloud = smin(cloud, puffRing(p, 6.0, 0.72, -0.56, 0.12), 0.06);
                if (cloud < res.x) res = vec2(cloud, 3.0);
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
        if (uAcc > 2.5 && uAcc < 3.5 && id < 3.5) {
            // Party hat stripes.
            base = mix(uAccA, uAccB, step(0.5, fract(op.y * 9.0)));
        }
        if (uAcc > 3.5 && uAcc < 4.5 && id < 3.5) {
            // White stars on the nightcap.
            vec3 hh = op - HEAD_C;
            vec2 g = vec2(atan(hh.z, hh.x) * 1.6, hh.y * 7.0 + hh.x * 3.0);
            vec2 c = fract(g) - 0.5;
            float star = sdStar5(c, 0.30, 0.45);
            base = mix(uAccA, uAccB, 1.0 - smoothstep(-0.03, 0.03, star));
        }
        if (type > 1.5) type = 0.0;
        if (uAcc > 1.5 && type > 0.5) type = 0.0;
        if (abs(uAcc - 8.0) < 0.5) type = 3.0; // the cape and scarf are soft fabric
        if ((abs(uAcc - 11.0) < 0.5 || uAcc > 20.5) && id < 3.5) type = 3.0; // fluffy bear hood, cloud
        if (abs(uAcc - 12.0) < 0.5 && id < 3.5) {
            // Woven straw.
            float r = length((op - HEAD_C).xz);
            base *= 0.86 + 0.14 * step(0.5, fract(r * 34.0 + op.y * 20.0));
        }
        if (abs(uAcc - 13.0) < 0.5) {
            // White polka dots.
            vec3 c = fract(op * 11.0) - 0.5;
            base = mix(base, uAccB, 1.0 - smoothstep(0.18, 0.24, length(c)));
        }
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
