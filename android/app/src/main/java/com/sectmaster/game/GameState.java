package com.sectmaster.game;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Pure game simulation and versioned native persistence. */
public final class GameState {
    public static final String[] BUILDING_NAMES = {"Meditation Hall", "Herb Garden", "Spirit Mine", "Alchemy Hall", "Training Yard", "Guardian Array"};
    public static final int[] BUILDING_COSTS = {120, 100, 160, 220, 180, 300};
    public static final String[] DISCIPLE_NAMES = {"Arin", "Mira", "Tarin", "Sora", "Jin", "Kael", "Lian", "Nami", "Riven", "Yuna"};
    private static final String PREFS = "sect_master_native_save";
    private static final String SAVE = "state_v2";
    private static final int SAVE_VERSION = 2;

    public static final class Building {
        public int type, x, y, level = 1;
        Building(int type, int x, int y) { this.type = type; this.x = x; this.y = y; }
    }

    public static final class Disciple {
        public String name;
        public int level = 1;
        public float xp;
        Disciple(String name) { this.name = name; }
        int power() { return level * 8 + 6; }
    }

    public int day = 1;
    public float spiritStones = 450, herbs = 35, ore = 15, pills;
    public int reputation, researchLevel, battlesWon, missionsDone;
    public boolean paused;
    public long lastSavedAt;
    public final List<Building> buildings = new ArrayList<>();
    public final List<Disciple> disciples = new ArrayList<>();
    private final Random random = new Random();
    private float dayClock, autoSaveClock;

    public GameState() { reset(); }

    public void reset() {
        day = 1; dayClock = 0; autoSaveClock = 0;
        spiritStones = 450; herbs = 35; ore = 15; pills = 0;
        reputation = researchLevel = battlesWon = missionsDone = 0;
        paused = false;
        buildings.clear();
        buildings.add(new Building(-1, 3, 2)); // Main Hall
        disciples.clear();
        disciples.add(new Disciple("Arin"));
        disciples.add(new Disciple("Mira"));
        lastSavedAt = System.currentTimeMillis();
    }

    public int power() {
        int value = 0;
        for (Disciple d : disciples) value += d.power();
        return value + guardianLevel() * 12 + researchLevel * 3;
    }

    public int guardianLevel() {
        int result = 0;
        for (Building b : buildings) if (b.type == 5) result += b.level;
        return result;
    }

    public boolean occupied(int x, int y) {
        for (Building b : buildings) if (b.x == x && b.y == y) return true;
        return false;
    }

    public String build(int type, int x, int y) {
        if (type < 0 || type >= BUILDING_COSTS.length) return "Choose a building first.";
        if (x < 0 || x >= 8 || y < 0 || y >= 5) return "That location is outside your sect.";
        if (occupied(x, y)) return "That tile is already occupied.";
        if (buildings.size() >= 18) return "Upgrade your sect before adding more buildings.";
        int cost = BUILDING_COSTS[type];
        if (spiritStones < cost) return "Not enough Spirit Stones.";
        spiritStones -= cost;
        buildings.add(new Building(type, x, y));
        reputation += 2;
        return null;
    }

    public String upgrade(Building b) {
        if (b == null) return "Select a building.";
        if (b.level >= 5) return "This building is at maximum level.";
        int base = b.type < 0 ? 240 : BUILDING_COSTS[b.type];
        int cost = base * b.level;
        if (spiritStones < cost) return "Not enough Spirit Stones.";
        spiritStones -= cost;
        b.level++;
        reputation += 3;
        return null;
    }

    public String recruit() {
        int cost = 100 + disciples.size() * 35;
        if (disciples.size() >= 12) return "The disciple roster is full.";
        if (spiritStones < cost) return "Not enough Spirit Stones.";
        spiritStones -= cost;
        String base = DISCIPLE_NAMES[random.nextInt(DISCIPLE_NAMES.length)];
        String name = base;
        int suffix = 2;
        while (hasName(name)) name = base + " " + suffix++;
        disciples.add(new Disciple(name));
        reputation += 2;
        return null;
    }

    private boolean hasName(String name) {
        for (Disciple d : disciples) if (d.name.equals(name)) return true;
        return false;
    }

    public String train(Disciple d) {
        if (d == null) return "Select a disciple.";
        int cost = 20 + d.level * 12;
        if (spiritStones < cost) return "Not enough Spirit Stones.";
        spiritStones -= cost;
        d.xp += 35 + researchLevel * 2;
        levelUp(d);
        return null;
    }

    public String brew() {
        int amount = 10;
        if (herbs < amount) return "You need 10 herbs.";
        if (!hasBuilding(3)) return "Build an Alchemy Hall first.";
        herbs -= amount;
        pills += 1 + buildingLevels(3) * 0.25f;
        return null;
    }

    public String research() {
        int cost = 120 + researchLevel * 90;
        if (researchLevel >= 8) return "All available research is complete.";
        if (spiritStones < cost) return "Not enough Spirit Stones.";
        spiritStones -= cost;
        researchLevel++;
        reputation += 8;
        return null;
    }

    public String mission(int difficulty) {
        if (difficulty < 0 || difficulty > 2) return "Unknown mission.";
        int[] requirements = {25, 65, 125};
        int[] rewards = {65, 145, 300};
        int required = requirements[difficulty];
        int chance = Math.max(15, Math.min(95, 55 + (power() - required) / 2));
        if (random.nextInt(100) < chance) {
            int reward = rewards[difficulty];
            spiritStones += reward;
            herbs += 4 + difficulty * 5;
            ore += 2 + difficulty * 4;
            reputation += 5 + difficulty * 7;
            battlesWon++;
            missionsDone++;
            for (Disciple d : disciples) { d.xp += 8 + difficulty * 6; levelUp(d); }
            return "Victory! The expedition returned with valuable supplies.";
        }
        int loss = Math.min((int) spiritStones, 20 + difficulty * 25);
        spiritStones -= loss;
        missionsDone++;
        return "Defeat. The sect lost " + loss + " Spirit Stones, but everyone returned safely.";
    }

    private void levelUp(Disciple d) {
        while (d.level < 30) {
            float needed = 50 + d.level * 20;
            if (d.xp < needed) break;
            d.xp -= needed;
            d.level++;
        }
    }

    private boolean hasBuilding(int type) { return buildingLevels(type) > 0; }
    private int buildingLevels(int type) {
        int n = 0;
        for (Building b : buildings) if (b.type == type) n += b.level;
        return n;
    }

    /** Advances at most five seconds per frame, preventing lifecycle time jumps. */
    public boolean update(float seconds) {
        if (paused || seconds <= 0) return false;
        seconds = Math.min(seconds, 5f);
        float mine = buildingLevels(2), garden = buildingLevels(1), meditation = buildingLevels(0);
        spiritStones += seconds * mine * 0.36f;
        herbs += seconds * garden * 0.10f;
        ore += seconds * mine * 0.025f;
        for (Disciple d : disciples) { d.xp += seconds * meditation * 0.045f; levelUp(d); }
        spiritStones = Math.min(spiritStones, 999999f);
        herbs = Math.min(herbs, 99999f); ore = Math.min(ore, 99999f); pills = Math.min(pills, 9999f);
        dayClock += seconds;
        while (dayClock >= 120f) { dayClock -= 120f; day++; }
        autoSaveClock += seconds;
        return autoSaveClock >= 20f;
    }

    public String offlineSummary(long now) {
        long elapsed = Math.max(0, Math.min(now - lastSavedAt, 8L * 60 * 60 * 1000));
        if (elapsed < 60_000) return null;
        float seconds = elapsed / 1000f;
        float stones = seconds * buildingLevels(2) * 0.18f;
        float gainedHerbs = seconds * buildingLevels(1) * 0.05f;
        spiritStones = Math.min(999999f, spiritStones + stones);
        herbs = Math.min(99999f, herbs + gainedHerbs);
        return String.format(Locale.US, "While away: +%.0f stones, +%.0f herbs", stones, gainedHerbs);
    }

    public void save(Context context) {
        try {
            lastSavedAt = System.currentTimeMillis();
            autoSaveClock = 0;
            JSONObject o = new JSONObject();
            o.put("v", SAVE_VERSION).put("day", day).put("clock", dayClock)
                .put("stones", spiritStones).put("herbs", herbs).put("ore", ore).put("pills", pills)
                .put("rep", reputation).put("research", researchLevel).put("wins", battlesWon)
                .put("missions", missionsDone).put("saved", lastSavedAt);
            JSONArray bs = new JSONArray();
            for (Building b : buildings) bs.put(new JSONObject().put("t", b.type).put("x", b.x).put("y", b.y).put("l", b.level));
            JSONArray ds = new JSONArray();
            for (Disciple d : disciples) ds.put(new JSONObject().put("n", d.name).put("l", d.level).put("xp", d.xp));
            o.put("buildings", bs).put("disciples", ds);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SAVE, o.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static GameState load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(SAVE, null);
        GameState s = new GameState();
        if (raw == null) return s;
        try {
            JSONObject o = new JSONObject(raw);
            if (o.optInt("v") != SAVE_VERSION) return s;
            s.day = Math.max(1, o.optInt("day", 1)); s.dayClock = (float)o.optDouble("clock", 0);
            s.spiritStones = positive(o.optDouble("stones", 450), 450); s.herbs = positive(o.optDouble("herbs", 35), 35);
            s.ore = positive(o.optDouble("ore", 15), 15); s.pills = positive(o.optDouble("pills", 0), 0);
            s.reputation = Math.max(0, o.optInt("rep")); s.researchLevel = Math.max(0, o.optInt("research"));
            s.battlesWon = Math.max(0, o.optInt("wins")); s.missionsDone = Math.max(0, o.optInt("missions"));
            s.lastSavedAt = o.optLong("saved", System.currentTimeMillis());
            s.buildings.clear();
            JSONArray bs = o.getJSONArray("buildings");
            for (int i=0; i<bs.length() && i<18; i++) { JSONObject b=bs.getJSONObject(i); Building x=new Building(b.getInt("t"),b.getInt("x"),b.getInt("y")); x.level=Math.max(1,Math.min(5,b.optInt("l",1))); if(x.x>=0&&x.x<8&&x.y>=0&&x.y<5&&!s.occupied(x.x,x.y)) s.buildings.add(x); }
            s.disciples.clear();
            JSONArray ds = o.getJSONArray("disciples");
            for (int i=0; i<ds.length() && i<12; i++) { JSONObject d=ds.getJSONObject(i); Disciple x=new Disciple(d.optString("n","Disciple")); x.level=Math.max(1,Math.min(30,d.optInt("l",1))); x.xp=(float)Math.max(0,d.optDouble("xp",0)); s.disciples.add(x); }
            if (s.buildings.isEmpty() || s.disciples.isEmpty()) s.reset();
        } catch (Exception e) { s.reset(); }
        return s;
    }

    private static float positive(double value, float fallback) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0 ? (float)value : fallback;
    }
    public static void clearSave(Context context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply(); }
}
