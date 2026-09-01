package com.nova.galaxysandbox.world

/** Surface biomes used by the planet-scale (WorldBox style) sandbox. */
enum class Biome(
    val label: String,
    val color: Int,
    val walkable: Boolean,
    val fertility: Float,
    val liquid: Boolean = false
) {
    DEEP_OCEAN("Deep Ocean", 0xFF0B2B58.toInt(), false, 0f, liquid = true),
    OCEAN("Ocean", 0xFF11458C.toInt(), false, 0f, liquid = true),
    SHALLOW("Shallows", 0xFF1E6BB8.toInt(), false, 0f, liquid = true),
    BEACH("Beach", 0xFFE0CE96.toInt(), true, 0.10f),
    GRASS("Grassland", 0xFF4E9A44.toInt(), true, 0.90f),
    PLAINS("Plains", 0xFF7BAE4E.toInt(), true, 0.80f),
    FOREST("Forest", 0xFF2C6B33.toInt(), true, 0.70f),
    JUNGLE("Jungle", 0xFF1E7A3C.toInt(), true, 0.65f),
    SAVANNA("Savanna", 0xFFA9A055.toInt(), true, 0.50f),
    DESERT("Desert", 0xFFD8C070.toInt(), true, 0.15f),
    BADLANDS("Badlands", 0xFFA9744A.toInt(), true, 0.12f),
    TUNDRA("Tundra", 0xFF9FB0A2.toInt(), true, 0.25f),
    SNOW("Snow", 0xFFE8F1F6.toInt(), true, 0.08f),
    ICE("Ice", 0xFFBEE1F2.toInt(), false, 0f, liquid = true),
    MOUNTAIN("Mountain", 0xFF7A7269.toInt(), true, 0.05f),
    PEAK("Peak", 0xFFCFCBC2.toInt(), true, 0f),
    LAVA("Lava", 0xFFFF5A1F.toInt(), false, 0f, liquid = true),
    ASH("Ash", 0xFF4A4643.toInt(), true, 0.05f),
    CRATER("Crater", 0xFF5B534C.toInt(), true, 0.02f),
    TOXIC("Toxic Sludge", 0xFF7BA02A.toInt(), true, 0.05f),
    VOID("Void", 0xFF07060B.toInt(), false, 0f);

    companion object {
        val ALL: Array<Biome> = values()
        fun of(index: Int): Biome = ALL[index.coerceIn(0, ALL.size - 1)]
    }
}

/** Playable species and wildlife that can populate a world. */
enum class Species(
    val label: String,
    val color: Int,
    val accent: Int,
    val strength: Float,
    val speed: Float,
    val intelligence: Float,
    val civilised: Boolean,
    val prefers: Array<Biome>
) {
    HUMAN("Humans", 0xFFFFD9A0.toInt(), 0xFF2F6FD0.toInt(), 1.0f, 1.0f, 1.0f, true,
        arrayOf(Biome.GRASS, Biome.PLAINS, Biome.FOREST, Biome.BEACH)),
    ELF("Sylvan", 0xFFC8F5D0.toInt(), 0xFF2E9E67.toInt(), 0.9f, 1.2f, 1.15f, true,
        arrayOf(Biome.FOREST, Biome.JUNGLE, Biome.GRASS)),
    ORC("Orcs", 0xFF9BCB6B.toInt(), 0xFF7A3A1F.toInt(), 1.5f, 0.95f, 0.7f, true,
        arrayOf(Biome.SAVANNA, Biome.BADLANDS, Biome.PLAINS, Biome.DESERT)),
    DWARF("Dwarves", 0xFFE0B48A.toInt(), 0xFF8A5A2B.toInt(), 1.25f, 0.8f, 1.05f, true,
        arrayOf(Biome.MOUNTAIN, Biome.TUNDRA, Biome.PEAK, Biome.BADLANDS)),
    FROSTKIN("Frostkin", 0xFFDDF3FF.toInt(), 0xFF4A8FC0.toInt(), 1.1f, 0.9f, 0.95f, true,
        arrayOf(Biome.SNOW, Biome.TUNDRA, Biome.ICE)),
    SYNTH("Synthetics", 0xFFB8C8D8.toInt(), 0xFF00E5FF.toInt(), 1.4f, 1.1f, 1.6f, true,
        arrayOf(Biome.DESERT, Biome.ASH, Biome.CRATER, Biome.PLAINS)),
    WOLF("Wolves", 0xFF8E8E96.toInt(), 0xFF3B3B44.toInt(), 0.8f, 1.4f, 0.2f, false,
        arrayOf(Biome.FOREST, Biome.TUNDRA, Biome.GRASS)),
    BEAR("Bears", 0xFF6B4A32.toInt(), 0xFF3A281B.toInt(), 1.8f, 0.8f, 0.2f, false,
        arrayOf(Biome.FOREST, Biome.TUNDRA, Biome.MOUNTAIN)),
    DRAGON("Dragons", 0xFFD2453B.toInt(), 0xFFFFB03A.toInt(), 6.0f, 1.6f, 0.6f, false,
        arrayOf(Biome.MOUNTAIN, Biome.PEAK, Biome.BADLANDS, Biome.ASH)),
    XENO("Xenomorphs", 0xFF6E5AA8.toInt(), 0xFF32FF9A.toInt(), 2.4f, 1.35f, 0.5f, false,
        arrayOf(Biome.TOXIC, Biome.JUNGLE, Biome.ASH, Biome.CRATER));

    companion object {
        val CIVS = values().filter { it.civilised }
        val BEASTS = values().filter { !it.civilised }
    }
}
