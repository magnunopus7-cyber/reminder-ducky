package app.reminderducky

import androidx.annotation.StringRes

/** Colours are 0xRRGGBB in sRGB; the shader converts them for lighting. */
private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)

enum class DuckMaterial(@StringRes val label: Int, val code: Float) {
    GLOSSY(R.string.mat_glossy, 0f), METALLIC(R.string.mat_metallic, 1f), CRYSTAL(R.string.mat_crystal, 2f), FLUFFY(R.string.mat_fluffy, 3f),
}

enum class DuckColor(@StringRes val label: Int, val body: Int, val head: Int = body) {
    SUNNY(R.string.col_sunny, 0xFFD01A),
    BLOSSOM(R.string.col_blossom, 0xFFA6BA),
    SKY(R.string.col_sky, 0x6B9EF2),
    MINT(R.string.col_mint, 0x8FE0BE),
    LAVENDER(R.string.col_lavender, 0xC2A6FF),
    SNOW(R.string.col_snow, 0xF2F5FF),
    GOLD(R.string.col_gold, 0xFFB838),
    SILVER(R.string.col_silver, 0xEBEBF2),
    SAILOR(R.string.col_sailor, 0x6B9EF2, 0xF7F7FF);

    val bodyRgb get() = rgb(body)
    val headRgb get() = rgb(head)
}

enum class DuckAccessory(@StringRes val label: Int, val code: Float, val a: Int, val b: Int) {
    NONE(R.string.acc_none, 0f, 0xFFFFFF, 0xFFFFFF),
    FLOWER(R.string.acc_flower, 1f, 0xFFEBF2, 0xFFD24D),
    BEANIE(R.string.acc_beanie, 2f, 0x4D80E6, 0x99C2FF),
    PARTY(R.string.acc_party, 3f, 0xFF73A6, 0x73CCFF);

    val aRgb get() = rgb(a)
    val bRgb get() = rgb(b)
}

enum class DuckSize(@StringRes val label: Int, val dp: Int) {
    SMALL(R.string.size_small, 80), MEDIUM(R.string.size_medium, 104), LARGE(R.string.size_large, 136), HUGE(R.string.size_huge, 208),
    GIGANTIC(R.string.size_gigantic, 288)
}

data class DuckLook(
    val material: DuckMaterial = DuckMaterial.GLOSSY,
    val color: DuckColor = DuckColor.SUNNY,
    val accessory: DuckAccessory = DuckAccessory.NONE,
) {
    val beakRgb: FloatArray get() = rgb(if (material == DuckMaterial.METALLIC) 0xFF731F else 0xFF801F)
}

/** Ready-made looks, like collectible figures. Each one is just a combination of the options. */
enum class DuckSkin(@StringRes val label: Int, @StringRes val mood: Int, val look: DuckLook) {
    CLASSIC(R.string.skin_classic, R.string.skin_classic_m, DuckLook(DuckMaterial.GLOSSY, DuckColor.SUNNY)),
    PASTEL(R.string.skin_pastel, R.string.skin_pastel_m, DuckLook(DuckMaterial.GLOSSY, DuckColor.BLOSSOM, DuckAccessory.FLOWER)),
    CRYSTAL(R.string.skin_crystal, R.string.skin_crystal_m, DuckLook(DuckMaterial.CRYSTAL, DuckColor.SNOW)),
    GOLD(R.string.skin_gold, R.string.skin_gold_m, DuckLook(DuckMaterial.METALLIC, DuckColor.GOLD)),
    SILVER(R.string.skin_silver, R.string.skin_silver_m, DuckLook(DuckMaterial.METALLIC, DuckColor.SILVER)),
    FLUFFY(R.string.skin_fluffy, R.string.skin_fluffy_m, DuckLook(DuckMaterial.FLUFFY, DuckColor.SUNNY)),
    COSTUME(R.string.skin_costume, R.string.skin_costume_m, DuckLook(DuckMaterial.GLOSSY, DuckColor.SAILOR, DuckAccessory.BEANIE)),
    PARTY(R.string.skin_party, R.string.skin_party_m, DuckLook(DuckMaterial.GLOSSY, DuckColor.SUNNY, DuckAccessory.PARTY)),
}
