package app.reminderducky

/** Colours are 0xRRGGBB in sRGB; the shader converts them for lighting. */
private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)

enum class DuckMaterial(val label: String, val code: Float) {
    GLOSSY("Glossy", 0f), METALLIC("Metallic", 1f), CRYSTAL("Crystal", 2f), FLUFFY("Fluffy", 3f),
}

enum class DuckColor(val label: String, val body: Int, val head: Int = body) {
    SUNNY("Sunny", 0xFFB80D),
    BLOSSOM("Blossom", 0xFFA6BA),
    SKY("Sky", 0x6B9EF2),
    MINT("Mint", 0x8FE0BE),
    LAVENDER("Lavender", 0xC2A6FF),
    SNOW("Snow", 0xF2F5FF),
    GOLD("Gold", 0xFFB838),
    SILVER("Silver", 0xEBEBF2),
    SAILOR("Sailor", 0x6B9EF2, 0xF7F7FF);

    val bodyRgb get() = rgb(body)
    val headRgb get() = rgb(head)
}

enum class DuckAccessory(val label: String, val code: Float, val a: Int, val b: Int) {
    NONE("None", 0f, 0xFFFFFF, 0xFFFFFF),
    FLOWER("Flower", 1f, 0xFFEBF2, 0xFFD24D),
    BEANIE("Beanie", 2f, 0x4D80E6, 0x99C2FF),
    PARTY("Party hat", 3f, 0xFF73A6, 0x73CCFF);

    val aRgb get() = rgb(a)
    val bRgb get() = rgb(b)
}

enum class DuckSize(val label: String, val dp: Int) { SMALL("Small", 80), MEDIUM("Medium", 104), LARGE("Large", 136) }

data class DuckLook(
    val material: DuckMaterial = DuckMaterial.GLOSSY,
    val color: DuckColor = DuckColor.SUNNY,
    val accessory: DuckAccessory = DuckAccessory.NONE,
) {
    val beakRgb: FloatArray get() = rgb(if (material == DuckMaterial.METALLIC) 0xFF731F else 0xFF801F)
}

/** Ready-made looks, like collectible figures. Each one is just a combination of the options. */
enum class DuckSkin(val label: String, val mood: String, val look: DuckLook) {
    CLASSIC("Classic", "Default", DuckLook(DuckMaterial.GLOSSY, DuckColor.SUNNY)),
    PASTEL("Pastel", "Sweet", DuckLook(DuckMaterial.GLOSSY, DuckColor.BLOSSOM, DuckAccessory.FLOWER)),
    CRYSTAL("Crystal", "Shiny", DuckLook(DuckMaterial.CRYSTAL, DuckColor.SNOW)),
    GOLD("Metallic Gold", "Luxurious", DuckLook(DuckMaterial.METALLIC, DuckColor.GOLD)),
    SILVER("Metallic Silver", "Modern", DuckLook(DuckMaterial.METALLIC, DuckColor.SILVER)),
    FLUFFY("Fluffy", "Cozy", DuckLook(DuckMaterial.FLUFFY, DuckColor.SUNNY)),
    COSTUME("Costume", "Playful", DuckLook(DuckMaterial.GLOSSY, DuckColor.SAILOR, DuckAccessory.BEANIE)),
    PARTY("Party", "Celebrating", DuckLook(DuckMaterial.GLOSSY, DuckColor.SUNNY, DuckAccessory.PARTY)),
}
