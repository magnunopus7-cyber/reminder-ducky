package app.reminderducky

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** Colours are 0xRRGGBB in sRGB; the shader converts them for lighting. */
private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)

enum class DuckMaterial(@StringRes val label: Int, val code: Float, @DrawableRes val thumb: Int) {
    GLOSSY(R.string.mat_glossy, 0f, R.drawable.thumb_mat_glossy),
    METALLIC(R.string.mat_metallic, 1f, R.drawable.thumb_mat_metallic),
    CRYSTAL(R.string.mat_crystal, 2f, R.drawable.thumb_mat_crystal),
    FLUFFY(R.string.mat_fluffy, 3f, R.drawable.thumb_mat_fluffy),
}

enum class DuckColor(@StringRes val label: Int, @DrawableRes val thumb: Int, val body: Int, val head: Int = body) {
    SUNNY(R.string.col_sunny, R.drawable.thumb_col_sunny, 0xFFD01A),
    BLOSSOM(R.string.col_blossom, R.drawable.thumb_col_blossom, 0xFFA6BA),
    SKY(R.string.col_sky, R.drawable.thumb_col_sky, 0x6B9EF2),
    MINT(R.string.col_mint, R.drawable.thumb_col_mint, 0x8FE0BE),
    LAVENDER(R.string.col_lavender, R.drawable.thumb_col_lavender, 0xC2A6FF),
    SNOW(R.string.col_snow, R.drawable.thumb_col_snow, 0xF2F5FF),
    GOLD(R.string.col_gold, R.drawable.thumb_col_gold, 0xFFB838),
    SILVER(R.string.col_silver, R.drawable.thumb_col_silver, 0xEBEBF2),
    SAILOR(R.string.col_sailor, R.drawable.thumb_col_sailor, 0x6B9EF2, 0xF7F7FF);

    val bodyRgb get() = rgb(body)
    val headRgb get() = rgb(head)
}

enum class DuckAccessory(@StringRes val label: Int, @DrawableRes val thumb: Int, val code: Float, val a: Int, val b: Int) {
    NONE(R.string.acc_none, R.drawable.thumb_acc_none, 0f, 0xFFFFFF, 0xFFFFFF),
    FLOWER(R.string.acc_flower, R.drawable.thumb_acc_flower, 1f, 0xFFEBF2, 0xFFD24D),
    BEANIE(R.string.acc_beanie, R.drawable.thumb_acc_beanie, 2f, 0x4D80E6, 0x99C2FF),
    PARTY(R.string.acc_party, R.drawable.thumb_acc_party, 3f, 0xFF73A6, 0x73CCFF),
    NIGHTCAP(R.string.acc_nightcap, R.drawable.thumb_acc_nightcap, 4f, 0x6FA8F0, 0xFFFFFF),
    SPROUT(R.string.acc_sprout, R.drawable.thumb_acc_sprout, 5f, 0x6CC24A, 0x4E9F3A),
    SHADES(R.string.acc_shades, R.drawable.thumb_acc_shades, 6f, 0xFF8FB0, 0x2B2530),
    HEADPHONES(R.string.acc_headphones, R.drawable.thumb_acc_headphones, 7f, 0x4A90E2, 0xF2F6FF),
    CAPE(R.string.acc_cape, R.drawable.thumb_acc_cape, 8f, 0xF0483E, 0xE23B32),
    CAP(R.string.acc_cap, R.drawable.thumb_acc_cap, 9f, 0x4A90E2, 0xFFD45A),
    FROG(R.string.st_frog, R.drawable.thumb_acc_frog, 10f, 0x6CC24A, 0xFFFFFF),
    BEAR(R.string.st_bear, R.drawable.thumb_acc_bear, 11f, 0xF5E9D0, 0xF2C14E),
    SUNHAT(R.string.st_sunhat, R.drawable.thumb_acc_sunhat, 12f, 0xE8C88A, 0xFFD24D),
    BANDANA(R.string.st_bandana, R.drawable.thumb_acc_bandana, 13f, 0xF06A3A, 0xFFFFFF),
    SNORKEL(R.string.st_snorkel, R.drawable.thumb_acc_snorkel, 14f, 0xFF7FB3, 0x8FB8F0),
    BACKPACK(R.string.st_backpack, R.drawable.thumb_acc_backpack, 15f, 0x4A90E2, 0x8FC1FF);

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
enum class DuckSkin(@StringRes val label: Int, @DrawableRes val thumb: Int, val look: DuckLook) {
    CLASSIC(R.string.skin_classic, R.drawable.thumb_skin_classic, DuckLook(DuckMaterial.GLOSSY, DuckColor.SUNNY)),
    PASTEL(R.string.skin_pastel, R.drawable.thumb_skin_pastel, DuckLook(DuckMaterial.GLOSSY, DuckColor.BLOSSOM, DuckAccessory.FLOWER)),
    CRYSTAL(R.string.skin_crystal, R.drawable.thumb_skin_crystal, DuckLook(DuckMaterial.CRYSTAL, DuckColor.SNOW)),
    GOLD(R.string.skin_gold, R.drawable.thumb_skin_gold, DuckLook(DuckMaterial.METALLIC, DuckColor.GOLD)),
    SILVER(R.string.skin_silver, R.drawable.thumb_skin_silver, DuckLook(DuckMaterial.METALLIC, DuckColor.SILVER)),
    FLUFFY(R.string.skin_fluffy, R.drawable.thumb_skin_fluffy, DuckLook(DuckMaterial.FLUFFY, DuckColor.SUNNY)),
    COSTUME(R.string.skin_costume, R.drawable.thumb_skin_costume, DuckLook(DuckMaterial.GLOSSY, DuckColor.SAILOR, DuckAccessory.BEANIE)),
    PARTY(R.string.skin_party, R.drawable.thumb_skin_party, DuckLook(DuckMaterial.GLOSSY, DuckColor.SUNNY, DuckAccessory.PARTY)),
    NIGHTCAP(R.string.acc_nightcap, R.drawable.thumb_acc_nightcap, DuckLook(accessory = DuckAccessory.NIGHTCAP)),
    SPROUT(R.string.acc_sprout, R.drawable.thumb_acc_sprout, DuckLook(accessory = DuckAccessory.SPROUT)),
    SHADES(R.string.acc_shades, R.drawable.thumb_acc_shades, DuckLook(accessory = DuckAccessory.SHADES)),
    HEADPHONES(R.string.acc_headphones, R.drawable.thumb_acc_headphones, DuckLook(accessory = DuckAccessory.HEADPHONES)),
    HERO(R.string.st_hero, R.drawable.thumb_acc_cape, DuckLook(accessory = DuckAccessory.CAPE)),
    CAP(R.string.acc_cap, R.drawable.thumb_acc_cap, DuckLook(accessory = DuckAccessory.CAP)),
    FROG(R.string.st_frog, R.drawable.thumb_acc_frog, DuckLook(accessory = DuckAccessory.FROG)),
    BEAR(R.string.st_bear, R.drawable.thumb_acc_bear, DuckLook(accessory = DuckAccessory.BEAR)),
    SUNHAT(R.string.st_sunhat, R.drawable.thumb_acc_sunhat, DuckLook(accessory = DuckAccessory.SUNHAT)),
    BANDANA(R.string.st_bandana, R.drawable.thumb_acc_bandana, DuckLook(accessory = DuckAccessory.BANDANA)),
    SNORKEL(R.string.st_snorkel, R.drawable.thumb_acc_snorkel, DuckLook(accessory = DuckAccessory.SNORKEL)),
    BACKPACK(R.string.st_backpack, R.drawable.thumb_acc_backpack, DuckLook(accessory = DuckAccessory.BACKPACK)),
}

/** Picture ducks: a flat image that floats, hops and dozes like the 3D duck. */
enum class DuckSticker(@StringRes val label: Int, @DrawableRes val image: Int) {
    SAILOR(R.string.st_sailor, R.drawable.sticker_sailor),
    FROG(R.string.st_frog, R.drawable.sticker_frog),
    NIGHTCAP(R.string.st_nightcap, R.drawable.sticker_nightcap),
    PARTY_HAT(R.string.st_party_hat, R.drawable.sticker_party_hat),
    DUCKLING(R.string.st_duckling, R.drawable.sticker_duckling),
    SUNGLASSES(R.string.st_sunglasses, R.drawable.sticker_sunglasses),
    BACKPACK(R.string.st_backpack, R.drawable.sticker_backpack),
    HEART(R.string.st_heart, R.drawable.sticker_heart),
    MUSIC(R.string.st_music, R.drawable.sticker_music),
    BEAR(R.string.st_bear, R.drawable.sticker_bear),
    BATH(R.string.st_bath, R.drawable.sticker_bath),
    HERO(R.string.st_hero, R.drawable.sticker_hero),
    SUNHAT(R.string.st_sunhat, R.drawable.sticker_sunhat),
    BOOKWORM(R.string.st_bookworm, R.drawable.sticker_bookworm),
    CLOUD(R.string.st_cloud, R.drawable.sticker_cloud),
    SPROUT(R.string.st_sprout, R.drawable.sticker_sprout),
    WINK(R.string.st_wink, R.drawable.sticker_wink),
    SNORKEL(R.string.st_snorkel, R.drawable.sticker_snorkel),
    BANDANA(R.string.st_bandana, R.drawable.sticker_bandana),
}
