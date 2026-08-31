package com.nova.galaxysandbox.action

/** Everything the player can shoot at a planet from orbit (Solar Smash side). */
enum class Weapon(
    val label: String,
    val description: String,
    val cooldown: Float,
    val glyph: String,
    val tint: Int,
    val targeting: Targeting
) {
    LASER("Laser", "Continuous beam that burns a glowing scar across the crust.", 0.05f, "≡", 0xFFFF3B5C.toInt(), Targeting.HOLD),
    RAILGUN("Railgun", "Hyper-velocity slug. Punches clean through small worlds.", 0.55f, "→", 0xFF9CE8FF.toInt(), Targeting.TAP),
    MISSILE("Missile Salvo", "Six guided warheads that bloom into firestorms.", 1.1f, "^", 0xFFFFB347.toInt(), Targeting.TAP),
    NUKE("Nuclear Strike", "Mushroom cloud, shockwave and long-lived fallout.", 1.8f, "☢", 0xFFFFE066.toInt(), Targeting.TAP),
    ASTEROID("Asteroid", "Drop a rock. Physics does the rest.", 1.4f, "●", 0xFFB99A72.toInt(), Targeting.TAP),
    METEOR_STORM("Meteor Storm", "A rain of burning debris across the hemisphere.", 2.6f, "✷", 0xFFFF8A3C.toInt(), Targeting.TAP),
    ANNIHILATOR("Annihilator", "Charge, then split the planet apart. Nothing survives.", 4.5f, "◈", 0xFFB06BFF.toInt(), Targeting.TAP),
    BLACK_HOLE("Black Hole", "Spawn a singularity that eats everything nearby.", 6.0f, "◉", 0xFF7B5CFF.toInt(), Targeting.TAP),
    SUN_CRUSHER("Sun Crusher", "Detonate a star and take its whole system with it.", 8.0f, "☀", 0xFFFFD24A.toInt(), Targeting.STAR),
    GRAVITY_SLAM("Gravity Slam", "Crush the crust inward; cities collapse instantly.", 2.2f, "⇩", 0xFF6FE7C8.toInt(), Targeting.TAP),
    ICE_BEAM("Cryo Beam", "Freeze the atmosphere. Oceans lock into ice sheets.", 1.0f, "❄", 0xFF9FE8FF.toInt(), Targeting.HOLD),
    PLAGUE("Bio Plague", "Leaves the terrain untouched. Kills every living thing.", 3.0f, "✚", 0xFF8ED14A.toInt(), Targeting.TAP),
    EMP("EMP Burst", "Fries shields, satellites and every bit of technology.", 2.4f, "≈", 0xFF64B5F6.toInt(), Targeting.TAP),
    UFO("Alien Invasion", "Send a hostile mothership to harvest the population.", 5.0f, "⏣", 0xFF32FF9A.toInt(), Targeting.TAP),
    TERRAFORM("Terraformer", "Repair the crust, restore atmosphere, reseed life.", 3.2f, "✿", 0xFF4FE07A.toInt(), Targeting.TAP);

    enum class Targeting { TAP, HOLD, STAR }
}

/** Brushes and disasters used on a planet's surface (WorldBox side). */
enum class Tool(
    val label: String,
    val category: Category,
    val glyph: String,
    val tint: Int,
    val defaultRadius: Float,
    val description: String
) {
    INSPECT("Inspect", Category.SELECT, "◎", 0xFFB0BEC5.toInt(), 1f, "Tap anything to read its story."),

    RAISE("Raise Land", Category.TERRAIN, "▲", 0xFF9C7B4E.toInt(), 4f, "Push the crust up into hills and mountains."),
    LOWER("Dig Ocean", Category.TERRAIN, "▼", 0xFF2E6FB0.toInt(), 4f, "Sink the land and let the sea flood in."),
    FOREST("Forest", Category.TERRAIN, "❦", 0xFF2C6B33.toInt(), 3f, "Plant dense woodland."),
    GRASSLAND("Grassland", Category.TERRAIN, "❞", 0xFF4E9A44.toInt(), 3f, "Soft, fertile plains."),
    DESERT("Desert", Category.TERRAIN, "░", 0xFFD8C070.toInt(), 3f, "Dry everything out."),
    SNOW("Snow", Category.TERRAIN, "❅", 0xFFE8F1F6.toInt(), 3f, "Bring the deep cold."),
    MOUNTAIN("Mountain", Category.TERRAIN, "⛰", 0xFF7A7269.toInt(), 2f, "Raise sheer rock and peaks."),
    LAVA_BRUSH("Lava", Category.TERRAIN, "≋", 0xFFFF5A1F.toInt(), 2f, "Open a molten wound in the ground."),

    HUMANS("Humans", Category.LIFE, "🯅", 0xFFFFD9A0.toInt(), 2f, "Adaptable builders."),
    ELVES("Sylvan", Category.LIFE, "🯅", 0xFFC8F5D0.toInt(), 2f, "Forest dwellers, fast and clever."),
    ORCS("Orcs", Category.LIFE, "🯅", 0xFF9BCB6B.toInt(), 2f, "Strong, warlike, restless."),
    DWARVES("Dwarves", Category.LIFE, "🯅", 0xFFE0B48A.toInt(), 2f, "Mountain engineers."),
    FROSTKIN("Frostkin", Category.LIFE, "🯅", 0xFFDDF3FF.toInt(), 2f, "Thrive in ice and tundra."),
    SYNTHS("Synthetics", Category.LIFE, "🯅", 0xFFB8C8D8.toInt(), 2f, "Machine intelligence. Fearless."),
    WOLVES("Wolves", Category.LIFE, "🐾", 0xFF8E8E96.toInt(), 2f, "Pack predators."),
    BEARS("Bears", Category.LIFE, "🐾", 0xFF6B4A32.toInt(), 2f, "Solitary and dangerous."),
    DRAGONS("Dragons", Category.LIFE, "🜂", 0xFFD2453B.toInt(), 1f, "Burn the world from above."),
    XENOS("Xenomorphs", Category.LIFE, "☣", 0xFF32FF9A.toInt(), 2f, "They spread. They do not stop."),

    METEOR("Meteor", Category.DISASTER, "☄", 0xFFFF8A3C.toInt(), 5f, "Impact crater, shockwave, wildfires."),
    VOLCANO("Volcano", Category.DISASTER, "🜂", 0xFFFF5A1F.toInt(), 4f, "Erupts lava that keeps spreading."),
    NUKE_TOOL("Nuke", Category.DISASTER, "☢", 0xFFFFE066.toInt(), 8f, "Flatten a region and poison it for ages."),
    LIGHTNING("Lightning", Category.DISASTER, "⚡", 0xFFFFF176.toInt(), 1f, "Ignites whatever it hits."),
    TSUNAMI("Tsunami", Category.DISASTER, "≈", 0xFF2E9BD8.toInt(), 10f, "A wall of water swallows the coast."),
    TORNADO("Tornado", Category.DISASTER, "🌀", 0xFFB0BEC5.toInt(), 3f, "Wanders, tears up ground and creatures."),
    PLAGUE_TOOL("Plague", Category.DISASTER, "✚", 0xFF8ED14A.toInt(), 12f, "Kills the living, leaves the buildings."),
    ICE_AGE("Ice Age", Category.DISASTER, "❄", 0xFF9FE8FF.toInt(), 14f, "Global cooling. Glaciers advance."),
    ACID_RAIN("Acid Rain", Category.DISASTER, "☔", 0xFF9CCC65.toInt(), 10f, "Strips vegetation into toxic sludge."),
    EARTHQUAKE("Earthquake", Category.DISASTER, "〰", 0xFF8D6E63.toInt(), 12f, "Shatters cities, opens fissures."),

    HEAL("Restore", Category.DIVINE, "✚", 0xFF4FE07A.toInt(), 6f, "Undo the damage: regrow, cool, cleanse."),
    ARMAGEDDON("Armageddon", Category.DIVINE, "✷", 0xFFFF3B5C.toInt(), 20f, "Everything, everywhere, all at once."),
    SMITE("Smite", Category.DIVINE, "✦", 0xFFFFF176.toInt(), 2f, "A pillar of light removes what you touch."),
    ERASE("Erase Life", Category.DIVINE, "✕", 0xFFEF5350.toInt(), 6f, "Quietly delete every creature in range.");

    enum class Category(val label: String) {
        SELECT("Cursor"), TERRAIN("Terrain"), LIFE("Life"), DISASTER("Disasters"), DIVINE("Divine")
    }
}
