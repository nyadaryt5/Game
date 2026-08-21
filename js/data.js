/* ═══════════════════════════════════════════════════════
   仙门掌门 · Sect Master — GAME DATA
   ═══════════════════════════════════════════════════════ */
(function () {
  "use strict";
  const D = (window.G = window.G || {});

  /* ── helpers ─────────────────────────────── */
  D.rid = (() => { let n = 1; return () => "id" + (n++).toString(36) + Date.now().toString(36).slice(-4); })();
  D.rand = (a, b) => a + Math.random() * (b - a);
  D.randi = (a, b) => Math.floor(D.rand(a, b + 1));
  D.pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
  D.clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  D.fmt = (n) => {
    n = Math.floor(n);
    if (n >= 1e8) return (n / 1e8).toFixed(1) + "亿";
    if (n >= 1e4) return (n / 1e4).toFixed(1) + "万";
    return "" + n;
  };
  D.fmtTime = (s) => {
    s = Math.ceil(s);
    if (s < 60) return s + "秒";
    if (s < 3600) return Math.floor(s / 60) + "分" + (s % 60 ? s % 60 + "秒" : "");
    return Math.floor(s / 3600) + "时" + Math.floor((s % 3600) / 60) + "分";
  };

  /* ── cultivation steps (realm ladder) ───── */
  // each step: { cn, en, tier, atk, hp, xp, major }  major = needs manual breakthrough
  (function buildSteps() {
    const tiers = [
      { key: "qi", name: "炼气", en: "Qi Refining", mult: 1.0 },
      { key: "base", name: "筑基", en: "Foundation", mult: 1.9 },
      { key: "core", name: "金丹", en: "Golden Core", mult: 3.4 },
      { key: "nascent", name: "元婴", en: "Nascent Soul", mult: 6.0 },
      { key: "spirit", name: "化神", en: "Spirit Severing", mult: 10.5 },
      { key: "maha", name: "大乘", en: "Mahayana", mult: 18.0 },
      { key: "ascend", name: "渡劫飞升", en: "Ascension", mult: 32.0 },
    ];
    const steps = [];
    const subNames = [
      ["一层", "二层", "三层", "四层", "五层", "六层", "七层", "八层", "九层"],
      ["初期", "中期", "后期"],
    ];
    const qiXp = [20, 30, 44, 62, 86, 116, 152, 196, 250];
    let stepInTier = -1;
    tiers.forEach((t, ti) => {
      const count = ti === 0 ? 9 : ti >= 5 ? 1 : 3;
      for (let k = 0; k < count; k++) {
        stepInTier++;
        const isLastOfTier = k === count - 1;
        const idx = steps.length;
        const atk = Math.round((5 + k * 2.4) * t.mult);
        const hp = Math.round((46 + k * 15) * t.mult * 0.92);
        const xp = ti === 0 ? qiXp[k] : Math.round(320 * Math.pow(1.45, ti - 1) * Math.pow(1.22, k));
        steps.push({
          cn: ti === 0 ? t.name + subNames[0][k] : t.name + subNames[1][k],
          en: t.en,
          tier: t.key,
          tierName: t.name,
          tierIdx: ti,
          step: idx,
          atk, hp, xp,
          major: isLastOfTier && ti < tiers.length - 1, // need manual breakthrough
          majorTo: isLastOfTier && ti < tiers.length - 1 ? steps.length + 1 : -1, // hmm see below
        });
      }
    });
    // fix majorTo: index of next step
    steps.forEach((s) => {
      if (s.major) s.majorTo = s.step + 1;
    });
    // breakthrough difficulty per major gate
    D.STEPS = steps;
    D.MAJOR_GATE = [
      { from: 8, to: 9, name: "筑基", en: "Foundation", chance: 0.72, pill: "pill_zhu", failLose: 0.25 },
      { from: 11, to: 12, name: "金丹", en: "Golden Core", chance: 0.6, pill: "pill_jin", failLose: 0.25 },
      { from: 14, to: 15, name: "元婴", en: "Nascent Soul", chance: 0.5, pill: "pill_ying", failLose: 0.25 },
      { from: 17, to: 18, name: "化神", en: "Spirit Severing", chance: 0.4, pill: "pill_shen", failLose: 0.25 },
      { from: 20, to: 21, name: "大乘", en: "Mahayana", chance: 0.3, pill: "pill_du", failLose: 0.25 },
      { from: 21, to: 22, name: "飞升", en: "Ascension", chance: 0.22, pill: "pill_du", failLose: 0.25 },
    ];
    D.gateFor = (step) => D.MAJOR_GATE.find((g) => g.from === step);
  })();

  /* ── spiritual roots ─────────────────────── */
  D.ROOTS = {
    metal: { name: "金灵根", en: "Metal", color: "#e8c36a", robe: "#c9a54a", robeD: "#8a6d2e", atk: 0.10, hp: 0, spd: 0, xp: 0, style: "aggressive", rare: 0 },
    wood: { name: "木灵根", en: "Wood", color: "#86d98f", robe: "#4e9c5f", robeD: "#356b41", atk: 0, hp: 0.06, spd: 0, xp: 0.06, style: "healer", rare: 0 },
    water: { name: "水灵根", en: "Water", color: "#6db3e8", robe: "#3f7fb8", robeD: "#2a5680", atk: 0.05, hp: 0.05, spd: 0, xp: 0.05, style: "balanced", rare: 0 },
    fire: { name: "火灵根", en: "Fire", color: "#ef8a64", robe: "#c2523a", robeD: "#82361f", atk: 0.17, hp: -0.05, spd: 0, xp: 0, style: "aggressive", rare: 0 },
    earth: { name: "土灵根", en: "Earth", color: "#d9b078", robe: "#8f6b42", robeD: "#5f4629", atk: -0.03, hp: 0.18, spd: 0, xp: 0, style: "tank", rare: 0 },
    thunder: { name: "雷灵根", en: "Thunder", color: "#c9a4ff", robe: "#7c5fc4", robeD: "#51408a", atk: 0.2, hp: 0, spd: 0.1, xp: 0, style: "aggressive", rare: 1 },
    ice: { name: "冰灵根", en: "Ice", color: "#9fe8f0", robe: "#5b9fb0", robeD: "#3d6d7a", atk: 0.08, hp: 0.1, spd: 0, xp: 0, style: "control", rare: 1 },
    wind: { name: "风灵根", en: "Wind", color: "#b8e8c0", robe: "#6ba878", robeD: "#477054", atk: 0.05, hp: 0, spd: 0.25, xp: 0.05, style: "swift", rare: 1 },
    heaven: { name: "天灵根", en: "Heavenly", color: "#ffffff", robe: "#ded6ea", robeD: "#9a90a8", atk: 0.24, hp: 0.14, spd: 0.1, xp: 0.3, style: "balanced", rare: 2 },
  };
  D.ROOT_KEYS = Object.keys(D.ROOTS);

  /* ── rooms ───────────────────────────────── */
  // cost array indexed by (next level - 1); time likewise. effect described in code.
  D.ROOMS = {
    hall: {
      key: "hall", name: "宗主大殿", en: "Main Hall", icon: "🏯", size: [3, 3], core: true, maxLvl: 5,
      desc: "宗门中枢，提供灵气与诸多上限。大殿若被攻破，宗门即告失守。",
      cost: { stone: [0, 600, 1000, 1600, 2500] }, time: [0, 30, 60, 120, 240], hp: [900, 1200, 1600, 2100, 2800],
    },
    med: {
      key: "med", name: "练功房", en: "Meditation", icon: "🧘", size: [2, 2], maxLvl: 10,
      desc: "弟子打坐吐纳之地。指派弟子进入，持续获得修为。",
      cost: { stone: [120, 180, 260, 380, 540, 760, 1060, 1480, 2050, 2840] },
      time: [6, 9, 13, 18, 24, 32, 42, 55, 72, 94], hp: [150, 200, 260, 340, 440, 560, 710, 900, 1130, 1420],
    },
    garden: {
      key: "garden", name: "灵药园", en: "Herb Garden", icon: "🌿", size: [2, 3], maxLvl: 10,
      desc: "培育灵草的药圃，持续产出灵草。指派擅长种植的弟子可增产。",
      cost: { stone: [150, 220, 320, 460, 650, 900, 1250, 1720, 2350, 3200] },
      time: [7, 10, 14, 19, 26, 35, 46, 60, 78, 100], hp: [160, 220, 290, 380, 490, 620, 790, 1000, 1260, 1580],
    },
    mine: {
      key: "mine", name: "灵石矿脉", en: "Spirit Mine", icon: "💠", size: [2, 2], maxLvl: 10,
      desc: "开采灵石，是宗门的主要财源。指派弟子采矿可增产。",
      cost: { stone: [150, 220, 320, 460, 650, 900, 1250, 1720, 2350, 3200] },
      time: [7, 10, 14, 19, 26, 35, 46, 60, 78, 100], hp: [160, 220, 290, 380, 490, 620, 790, 1000, 1260, 1580],
    },
    train: {
      key: "train", name: "演武场", en: "Training Ground", icon: "⚔️", size: [3, 2], maxLvl: 10,
      desc: "弟子习练剑术之处。指派弟子获得少量修为，每级提升全宗攻击。",
      cost: { stone: [200, 300, 440, 640, 900, 1250, 1720, 2350, 3200, 4300] },
      time: [9, 13, 18, 25, 34, 45, 60, 79, 103, 133], hp: [200, 280, 380, 500, 660, 850, 1090, 1390, 1760, 2220],
    },
    alch: {
      key: "alch", name: "炼丹房", en: "Alchemy Hall", icon: "⚗️", size: [2, 2], maxLvl: 10,
      desc: "以灵草炼制丹药。指派炼丹弟子，选择丹方后持续炼制。",
      cost: { stone: [250, 380, 560, 810, 1150, 1600, 2200, 3000, 4060, 5450] },
      time: [10, 15, 21, 29, 40, 54, 72, 95, 125, 163], hp: [180, 250, 330, 430, 560, 720, 920, 1170, 1470, 1850],
      unlockRep: 1,
    },
    dorm: {
      key: "dorm", name: "弟子居所", en: "Dormitory", icon: "🛏️", size: [2, 2], maxLvl: 10,
      desc: "弟子起居之所，每级提升可收弟子的数量上限。",
      cost: { stone: [140, 210, 300, 430, 600, 830, 1150, 1580, 2150, 2900] },
      time: [6, 9, 13, 18, 24, 32, 42, 55, 72, 94], hp: [150, 210, 280, 360, 470, 600, 760, 960, 1210, 1510],
      unlockRep: 1,
    },
    store: {
      key: "store", name: "聚宝阁", en: "Treasury", icon: "🏺", size: [2, 2], maxLvl: 10,
      desc: "存放宗门资源，每级提升各项资源的储存上限。",
      cost: { stone: [140, 210, 300, 430, 600, 830, 1150, 1580, 2150, 2900] },
      time: [6, 9, 13, 18, 24, 32, 42, 55, 72, 94], hp: [150, 210, 280, 360, 470, 600, 760, 960, 1210, 1510],
      unlockRep: 1,
    },
    lib: {
      key: "lib", name: "藏经阁", en: "Library", icon: "📜", size: [2, 2], maxLvl: 10,
      desc: "收藏功法典籍，可研究宗门技艺（一次一项）。每级加速研究。",
      cost: { stone: [220, 330, 480, 680, 950, 1320, 1810, 2460, 3330, 4470] },
      time: [9, 13, 18, 25, 34, 45, 60, 79, 103, 133], hp: [180, 250, 330, 430, 560, 720, 920, 1170, 1470, 1850],
      unlockRep: 2,
    },
    forge: {
      key: "forge", name: "炼器坊", en: "Forge Hall", icon: "🔨", size: [2, 2], maxLvl: 10,
      desc: "以矿石炼制法器。指派炼器弟子，选择器谱后持续炼制。",
      cost: { stone: [320, 480, 700, 1000, 1420, 1980, 2730, 3730, 5060, 6800] },
      time: [12, 17, 24, 33, 45, 61, 82, 110, 147, 196], hp: [200, 280, 380, 500, 660, 850, 1090, 1390, 1760, 2220],
      unlockRep: 2,
    },
    talis: {
      key: "talis", name: "制符室", en: "Talisman Workshop", icon: "🧿", size: [2, 2], maxLvl: 10,
      desc: "绘制符箓，战斗中自动消耗，克敌制胜。指派制符弟子生产。",
      cost: { stone: [320, 480, 700, 1000, 1420, 1980, 2730, 3730, 5060, 6800] },
      time: [12, 17, 24, 33, 45, 61, 82, 110, 147, 196], hp: [200, 280, 380, 500, 660, 850, 1090, 1390, 1760, 2220],
      unlockRep: 3,
    },
    array: {
      key: "array", name: "护山大阵", en: "Guardian Array", icon: "🛡️", size: [2, 2], maxLvl: 10,
      desc: "护山大阵。遭遇攻打时提供灵力护盾，并提升全宗防御。",
      cost: { stone: [400, 600, 880, 1250, 1750, 2400, 3260, 4380, 5830, 7700] },
      time: [14, 20, 28, 38, 52, 70, 93, 124, 164, 216], hp: [220, 310, 410, 540, 700, 900, 1160, 1470, 1870, 2360],
      unlockRep: 4,
    },
  };
  // effective effects per room (function of level)
  D.roomEffects = {
    hall: (l) => ({ energy: 4 + 4 * l, rooms: 4 + 3 * l, disciples: 3 + 2 * l }),
    med: (l) => ({ slots: 1 + l, xpRate: 0.9 + 0.42 * (l - 1) }),
    garden: (l) => ({ slots: 1 + Math.floor(l / 3), herbRate: 0.3 + 0.13 * (l - 1) }),
    mine: (l) => ({ slots: 1 + Math.floor(l / 3), stoneRate: 0.36 + 0.15 * (l - 1) }),
    train: (l) => ({ slots: 2 + Math.floor(l / 2), xpRate: 0.42 + 0.14 * (l - 1), squadAtk: 0.005 * l }),
    alch: (l) => ({ slots: 1 + Math.floor(l / 3), speedBonus: 0.06 * (l - 1) }),
    dorm: (l) => ({ disciples: 2 * l }),
    store: (l) => ({ capMult: 0.35 * l }),
    lib: (l) => ({ speedBonus: 0.08 * (l - 1) }),
    forge: (l) => ({ slots: 1 + Math.floor(l / 4), speedBonus: 0.06 * (l - 1), quality: 4 * l }),
    talis: (l) => ({ slots: 1 + Math.floor(l / 3), speedBonus: 0.06 * (l - 1) }),
    array: (l) => ({ shield: 90 * l, squadDef: 0.02 * l }),
  };

  /* ── alchemy recipes ─────────────────────── */
  D.RECIPES = [
    { key: "pill_qi", name: "聚气丹", en: "Qi Pill", icon: "💊", cost: { herb: 5 }, time: 8, unlockRep: 0,
      desc: "服用后获得当前境界 35% 的修为。", xpMult: 0.35 },
    { key: "pill_pei", name: "培元丹", en: "Foundation Pill", icon: "💊", cost: { herb: 14 }, time: 13, unlockRep: 2,
      desc: "服用后获得当前境界 80% 的修为。", xpMult: 0.8 },
    { key: "pill_zhu", name: "筑基丹", en: "Foundation Pill", icon: "💮", cost: { herb: 24, ore: 6 }, time: 20, unlockRep: 3,
      desc: "冲击筑基时服用，突破成功率 +22%。", breakPill: true },
    { key: "pill_jin", name: "结金丹", en: "Core Pill", icon: "💮", cost: { herb: 32, ore: 10 }, time: 26, unlockRep: 5,
      desc: "冲击金丹时服用，突破成功率 +22%。", breakPill: true },
    { key: "pill_ying", name: "化婴丹", en: "Nascent Pill", icon: "💮", cost: { herb: 42, ore: 14 }, time: 32, unlockRep: 7,
      desc: "冲击元婴时服用，突破成功率 +22%。", breakPill: true },
    { key: "pill_shen", name: "化神丹", en: "Spirit Pill", icon: "💮", cost: { herb: 55, ore: 20 }, time: 40, unlockRep: 9,
      desc: "冲击化神时服用，突破成功率 +22%。", breakPill: true },
    { key: "pill_du", name: "渡劫丹", en: "Tribulation Pill", icon: "💮", cost: { herb: 75, ore: 30 }, time: 50, unlockRep: 11,
      desc: "冲击大乘与飞升时服用，突破成功率 +22%。", breakPill: true },
  ];

  /* ── forge blueprints ────────────────────── */
  D.TIERS = [
    { name: "凡品", en: "Mortal", color: "#b9b4a6", mult: 1.0 },
    { name: "下品", en: "Low", color: "#7fd08a", mult: 1.35 },
    { name: "中品", en: "Mid", color: "#6db3e8", mult: 1.8 },
    { name: "上品", en: "High", color: "#c9a4ff", mult: 2.5 },
    { name: "极品", en: "Supreme", color: "#f0c060", mult: 3.6 },
  ];
  D.BLUEPRINTS = [
    { key: "sword", name: "飞剑", en: "Flying Sword", icon: "🗡️", slot: "weapon", stat: "atk",
      cost: { ore: 4 }, time: 14, unlockRep: 0, base: [6, 9, 13, 18, 25], desc: "攻伐法器，提升攻击。" },
    { key: "robe", name: "法袍", en: "Spirit Robe", icon: "🥋", slot: "armor", stat: "hp",
      cost: { ore: 4 }, time: 14, unlockRep: 1, base: [15, 22, 32, 45, 62], desc: "护身法器，提升气血。" },
  ];

  /* ── talisman recipes ────────────────────── */
  D.TALISMANS = [
    { key: "talisman_fire", name: "火球符", en: "Fire Talisman", icon: "🔥", cost: { herb: 3, stone: 12 }, time: 10, unlockRep: 0,
      desc: "战斗中自动消耗（每场至多 3 张），每张 +12 攻击。" },
    { key: "talisman_vajra", name: "金刚符", en: "Vajra Talisman", icon: "🛡️", cost: { ore: 3, stone: 12 }, time: 10, unlockRep: 2,
      desc: "战斗中自动消耗（每场至多 2 张），每张提供 30 点护盾。" },
  ];

  /* ── library techs ───────────────────────── */
  D.TECHS = [
    { key: "array_spirit", name: "聚灵阵", en: "Spirit Gathering Array", icon: "🌀", cost: { stone: 150 }, time: 30, unlockRep: 1,
      desc: "灵气 +6，可供应更多殿阁。" },
    { key: "sword_qingfeng", name: "剑诀·清风", en: "Qingfeng Sword Art", icon: "🗡️", cost: { stone: 220 }, time: 45, unlockRep: 2,
      desc: "全宗攻击 +10%，暴击率 +5%。" },
    { key: "iron_wall", name: "铁壁功", en: "Iron Wall", icon: "🧱", cost: { stone: 220 }, time: 45, unlockRep: 2,
      desc: "全宗气血 +15%。" },
    { key: "swift_step", name: "疾风步", en: "Swift Steps", icon: "💨", cost: { stone: 260 }, time: 50, unlockRep: 3,
      desc: "全宗出手速度 +8%。" },
    { key: "soul_link", name: "同心诀", en: "Soul Link", icon: "💞", cost: { stone: 320 }, time: 55, unlockRep: 4,
      desc: "木灵根弟子疗伤时可同时治愈两名同门。" },
    { key: "dao_pill", name: "丹道真解", en: "Dao of Alchemy", icon: "⚗️", cost: { stone: 300 }, time: 55, unlockRep: 4,
      desc: "炼丹速度 +30%。" },
    { key: "sword_formation", name: "北斗剑阵", en: "Big Dipper Array", icon: "⭐", cost: { stone: 500 }, time: 80, unlockRep: 6,
      desc: "全宗攻击 +15%。" },
    { key: "golden_body", name: "金刚不坏", en: "Vajra Body", icon: "✨", cost: { stone: 500 }, time: 80, unlockRep: 6,
      desc: "全宗气血 +20%。" },
    { key: "forge_master", name: "器道真解", en: "Dao of Forging", icon: "🔨", cost: { stone: 450 }, time: 70, unlockRep: 5,
      desc: "炼器速度 +30%，高品质法器概率提升。" },
  ];

  /* ── reputation levels ───────────────────── */
  D.REP_LEVELS = [0, 40, 90, 160, 260, 400, 580, 800, 1080, 1420, 1840, 2350, 3000];
  D.repLevel = (rep) => {
    let l = 1;
    for (let i = 0; i < D.REP_LEVELS.length; i++) if (rep >= D.REP_LEVELS[i]) l = i + 1;
    return l;
  };

  /* ── regions / missions / raids ──────────── */
  D.REGIONS = [
    {
      id: "qingyun", name: "青云山脉", en: "Azure Cloud Range", rep: 0, color: "#5fae7a", icon: "⛰️",
      missions: [
        { id: "q_herb", type: "gather", name: "采集灵草", en: "Gather Herbs", icon: "🌿", power: 30, duration: 20, cooldown: 15,
          rewards: { stone: 40, herb: 26, rep: 6 }, xp: 14, desc: "后山灵草茂盛，派弟子采集。" },
        { id: "q_hunt", type: "hunt", name: "猎杀妖兽", en: "Beast Hunt", icon: "🐗", power: 55, duration: 30, cooldown: 20,
          rewards: { stone: 70, herb: 12, rep: 9 }, xp: 22, desc: "山中出现妖兽，扰民伤畜，速去清剿。" },
        { id: "q_escort", type: "escort", name: "护送商队", en: "Escort Caravan", icon: "🐎", power: 42, duration: 25, cooldown: 20,
          rewards: { stone: 95, rep: 8 }, xp: 16, desc: "山下商会求援，护送过境可得厚礼。" },
        { id: "q_realm", type: "realm", name: "秘境·青云洞", en: "Azure Cloud Cave", icon: "🌀", power: 95, duration: 60, cooldown: 150,
          rewards: { stone: 160, herb: 42, ore: 16, rep: 22 }, xp: 46, desc: "传闻洞中有上古修士遗泽，凶险非常。" },
      ],
      raids: [
        { id: "r_heifeng", name: "黑风寨", en: "Blackwind Bandits", icon: "🏴‍☠️", power: 95, shield: 140, core: 300, cooldown: 120,
          loot: { stone: 170, herb: 20, rep: 18 }, xp: 30, desc: "盘踞山麓的魔道散修，劫掠商旅，为祸一方。" },
      ],
    },
    {
      id: "luoxia", name: "落霞谷", en: "Sunset Valley", rep: 40, color: "#d98a5f", icon: "🌄",
      missions: [
        { id: "l_herb", type: "gather", name: "采集赤霞草", en: "Gather Sunset Grass", icon: "🌿", power: 115, duration: 25, cooldown: 20,
          rewards: { stone: 110, herb: 55, rep: 12 }, xp: 30, desc: "谷中赤霞草是炼丹良材。" },
        { id: "l_hunt", type: "hunt", name: "猎杀火蜥", en: "Fire Lizard Hunt", icon: "🦎", power: 150, duration: 32, cooldown: 25,
          rewards: { stone: 160, herb: 20, ore: 10, rep: 16 }, xp: 40, desc: "火蜥成群，焚毁农田，民怨沸腾。" },
        { id: "l_escort", type: "escort", name: "护送贡品", en: "Tribute Escort", icon: "🐎", power: 130, duration: 28, cooldown: 25,
          rewards: { stone: 210, rep: 14 }, xp: 34, desc: "护送贡品车队前往州府。" },
        { id: "l_realm", type: "realm", name: "秘境·落霞洞天", en: "Sunset Grotto", icon: "🌀", power: 250, duration: 70, cooldown: 170,
          rewards: { stone: 380, herb: 80, ore: 40, rep: 38 }, xp: 80, desc: "洞天遗迹，机缘与凶险并存。" },
      ],
      raids: [
        { id: "r_chixia", name: "赤霞宗", en: "Chixia Sect", icon: "🚩", power: 240, shield: 260, core: 480, cooldown: 140,
          loot: { stone: 320, herb: 40, ore: 20, rep: 30 }, xp: 60, desc: "谷中大宗，屡次欺凌小门派，早已结怨。" },
      ],
    },
    {
      id: "youming", name: "幽冥沼泽", en: "Nether Marsh", rep: 120, color: "#7a6fc4", icon: "🌫️",
      missions: [
        { id: "y_herb", type: "gather", name: "采集幽冥花", en: "Gather Nether Blossom", icon: "🌿", power: 270, duration: 30, cooldown: 25,
          rewards: { stone: 240, herb: 95, rep: 20 }, xp: 55, desc: "沼泽深处生有幽冥花，剧毒亦是良药。" },
        { id: "y_hunt", type: "hunt", name: "清剿黑水蟒", en: "Blackwater Python", icon: "🐍", power: 330, duration: 36, cooldown: 30,
          rewards: { stone: 330, herb: 40, ore: 22, rep: 26 }, xp: 70, desc: "黑水蟒盘踞水道，渔人不敢下水。" },
        { id: "y_escort", type: "escort", name: "护送药队", en: "Medicine Escort", icon: "🐎", power: 290, duration: 32, cooldown: 30,
          rewards: { stone: 420, rep: 22 }, xp: 60, desc: "护送采药队穿过迷雾区。" },
        { id: "y_realm", type: "realm", name: "秘境·阴煞洞", en: "Yinsha Cave", icon: "🌀", power: 540, duration: 80, cooldown: 190,
          rewards: { stone: 700, herb: 130, ore: 70, rep: 60 }, xp: 140, desc: "阴煞之气弥漫的洞穴，传闻藏有邪修遗宝。" },
      ],
      raids: [
        { id: "r_yinsha", name: "阴煞宗", en: "Yinsha Sect", icon: "☠️", power: 480, shield: 420, core: 720, cooldown: 160,
          loot: { stone: 560, herb: 60, ore: 40, rep: 46 }, xp: 110, desc: "盘踞沼泽的邪道宗门，以生人魂魄炼宝。" },
      ],
    },
    {
      id: "dahuang", name: "大荒域", en: "Great Wilds", rep: 300, color: "#c9a04f", icon: "🏜️",
      missions: [
        { id: "d_herb", type: "gather", name: "采集荒原参", en: "Wild Ginseng", icon: "🌿", power: 580, duration: 34, cooldown: 30,
          rewards: { stone: 480, herb: 150, rep: 32 }, xp: 95, desc: "荒原老参，千年难遇。" },
        { id: "d_hunt", type: "hunt", name: "围猎沙蝎王", en: "Sand Scorpion King", icon: "🦂", power: 680, duration: 40, cooldown: 35,
          rewards: { stone: 620, herb: 70, ore: 45, rep: 40 }, xp: 120, desc: "沙蝎王夜袭商道，商旅死伤无数。" },
        { id: "d_escort", type: "escort", name: "护送灵矿队", en: "Ore Convoy", icon: "🐎", power: 620, duration: 36, cooldown: 35,
          rewards: { stone: 780, rep: 34 }, xp: 105, desc: "护送灵矿车队穿越风沙地带。" },
        { id: "d_realm", type: "realm", name: "秘境·大荒古殿", en: "Great Wilds Ruins", icon: "🌀", power: 1000, duration: 90, cooldown: 210,
          rewards: { stone: 1200, herb: 200, ore: 130, rep: 90 }, xp: 230, desc: "上古宗门遗迹，殿中禁制仍在运转。" },
      ],
      raids: [
        { id: "r_xuemo", name: "血魔教", en: "Blood Demon Cult", icon: "🩸", power: 900, shield: 680, core: 1100, cooldown: 180,
          loot: { stone: 950, herb: 100, ore: 80, rep: 70 }, xp: 190, desc: "以血祭魔的大教，覆灭过三个小宗门。" },
      ],
    },
    {
      id: "zixiao", name: "紫霄天", en: "Purple Firmament", rep: 700, color: "#c9a4ff", icon: "🌌",
      missions: [
        { id: "z_herb", type: "gather", name: "采集紫霄芝", en: "Firmament Lingzhi", icon: "🌿", power: 1150, duration: 38, cooldown: 35,
          rewards: { stone: 900, herb: 230, rep: 50 }, xp: 160, desc: "紫霄山巅的灵芝，吸日月精华而生。" },
        { id: "z_hunt", type: "hunt", name: "镇压雷鹰", en: "Thunder Eagle", icon: "🦅", power: 1300, duration: 44, cooldown: 40,
          rewards: { stone: 1100, herb: 120, ore: 80, rep: 60 }, xp: 200, desc: "雷鹰搅动天象，万里无晴日。" },
        { id: "z_escort", type: "escort", name: "护送飞舟队", en: "Skyship Escort", icon: "🛸", power: 1200, duration: 40, cooldown: 40,
          rewards: { stone: 1400, rep: 55 }, xp: 180, desc: "护送飞舟商队穿过雷云。" },
        { id: "z_realm", type: "realm", name: "秘境·紫霄宫", en: "Firmament Palace", icon: "🌀", power: 1900, duration: 100, cooldown: 240,
          rewards: { stone: 2200, herb: 320, ore: 240, rep: 140 }, xp: 380, desc: "上古紫霄宫的入口，仙缘与杀机同在。" },
      ],
      raids: [
        { id: "r_tianxie", name: "天邪宗", en: "Tianxie Sect", icon: "👹", power: 1700, shield: 1100, core: 1700, cooldown: 200,
          loot: { stone: 1650, herb: 180, ore: 150, rep: 110 }, xp: 330, desc: "魔道魁首，正道公敌。灭之，则天下扬名。" },
      ],
    },
  ];

  /* ── enemy name pools ────────────────────── */
  D.BEASTS = {
    qingyun: ["赤炎狼", "青木蛇", "岩甲熊", "黑风猪", "噬灵鼠"],
    luoxia: ["火蜥", "疾风雕", "赤霞蜈", "落日鹫"],
    youming: ["幽冥蝠", "黑水蟒", "噬骨鳄", "阴魂藤"],
    dahuang: ["沙蝎", "荒原狮", "铁背猿", "风蚀鹫"],
    zixiao: ["紫霄雷鹰", "吞天蟒", "雷纹豹", "天火蜥"],
  };
  D.BANDITS = {
    qingyun: ["黑风寨喽啰", "黑风寨刀客", "黑风寨寨主"],
    luoxia: ["赤霞宗外门", "赤霞宗执事", "赤霞宗长老"],
    youming: ["阴煞宗弟子", "阴煞宗护法", "阴煞宗长老"],
    dahuang: ["血魔教徒", "血魔教执事", "血魔教护法"],
    zixiao: ["天邪宗弟子", "天邪宗护法", "天邪宗太上长老"],
  };
  D.ENEMY_STYLES = ["aggressive", "tank", "swift", "control", "aggressive", "balanced"];

  /* ── random events ───────────────────────── */
  D.EVENTS = [
    { id: "vein", weight: 12, icon: "💠", title: "灵脉发现", en: "Spirit Vein Found",
      desc: "门下弟子于后山发现一处小型灵石矿脉，如何处置？",
      choices: [
        { text: "立即开采（+320 灵石）", apply: (S) => S.addRes("stone", 320) },
        { text: "上报州府（+80 声望）", apply: (S) => S.addRep(80) },
      ] },
    { id: "hermit", weight: 10, icon: "🧑‍🦳", title: "游方散修", en: "Wandering Cultivator",
      desc: "一位云游散修路过山门，恳请拜入宗门。",
      choices: [
        { text: "收留入门（获得一名弟子）", apply: (S) => { const d = S.spawnDisciple({ free: true }); return "散修「" + d.name + "」拜入山门！"; } },
        { text: "婉拒并赠路费（-60 灵石，+10 声望）", cost: { stone: 60 }, apply: (S) => { S.addRes("stone", -60); S.addRep(10); } },
      ] },
    { id: "beast", weight: 10, icon: "🐗", title: "妖兽袭扰", en: "Beast Incursion",
      desc: "一群妖兽嗅着灵药香气逼近山门！",
      choices: [
        { text: "开启山门迎战！", apply: (S) => { S.startDefense("beasts"); return "妖兽来袭，全宗迎战！"; } },
        { text: "紧闭山门（妖兽毁坏药园，-40 灵草）", apply: (S) => { S.addRes("herb", -Math.min(40, S.res.herb)); } },
      ] },
    { id: "trader", weight: 14, icon: "🧳", title: "游方商人", en: "Wandering Merchant",
      desc: "一名商人愿以灵草换取你的矿石。",
      choices: [
        { text: "交换：-30 灵草 → +22 矿石", cost: { herb: 30 }, apply: (S) => { S.addRes("herb", -30); S.addRes("ore", 22); } },
        { text: "不感兴趣", apply: () => { } },
      ] },
    { id: "tournament", weight: 8, icon: "🏆", title: "宗门大比", en: "Sect Tournament",
      desc: "门下弟子举行切磋大比，气氛热烈。",
      choices: [
        { text: "重赏胜者（-120 灵石，+25 声望，众弟子修为精进）", cost: { stone: 120 },
          apply: (S) => { S.addRes("stone", -120); S.addRep(25); S.allDisciples().forEach((d) => { d.xp += Math.round(D.STEPS[d.step].xp * 0.15); d.checkLevel(); }); } },
        { text: "口头嘉奖（+6 声望）", apply: (S) => S.addRep(6) },
      ] },
    { id: "spring", weight: 8, icon: "⛲", title: "灵泉涌现", en: "Spirit Spring",
      desc: "后山灵泉涌现，灵气充沛，正是修炼良机。",
      choices: [
        { text: "引泉入宗（-80 灵石，众弟子修为精进）", cost: { stone: 80 },
          apply: (S) => { S.addRes("stone", -80); S.allDisciples().forEach((d) => { d.xp += Math.round(D.STEPS[d.step].xp * 0.2); d.checkLevel(); }); } },
        { text: "顺其自然", apply: () => { } },
      ] },
  ];

  /* ── tutorial tasks ──────────────────────── */
  D.TASKS = [
    { id: "t1", icon: "🏯", title: "初立山门", en: "Found the Sect", desc: "建造一座练功房", reward: { stone: 100 } },
    { id: "t2", icon: "🧑‍🌾", title: "广纳门徒", en: "Recruit Disciples", desc: "弟子总数达到 4 名", reward: { stone: 120 } },
    { id: "t3", icon: "⚗️", title: "种药炼丹", en: "Grow & Brew", desc: "建造灵药园与炼丹房，并炼成 3 枚丹药", reward: { herb: 30, pill_qi: 2 } },
    { id: "t4", icon: "🌟", title: "初窥门径", en: "First Steps", desc: "任一名弟子修炼至炼气三层", reward: { stone: 150 } },
    { id: "t5", icon: "🌿", title: "小试牛刀", en: "First Mission", desc: "完成一次「采集灵草」任务", reward: { stone: 150 } },
    { id: "t6", icon: "📜", title: "藏经传功", en: "Library Research", desc: "建造藏经阁并研究「聚灵阵」", reward: { stone: 200 } },
    { id: "t7", icon: "⚔️", title: "演武练兵", en: "Train the Sect", desc: "建造演武场，宗门战力达到 140", reward: { stone: 200 } },
    { id: "t8", icon: "🏴‍☠️", title: "扬名立万", en: "Make a Name", desc: "攻破黑风寨", reward: { stone: 300, pill_zhu: 1 } },
  ];
  D.taskChecks = {
    t1: (S) => S.rooms.some((r) => r.type === "med"),
    t2: (S) => S.disciples.length >= 4,
    t3: (S) => S.rooms.some((r) => r.type === "garden") && S.rooms.some((r) => r.type === "alch") && S.stats.pillsMade >= 3,
    t4: (S) => S.disciples.some((d) => d.step >= 2),
    t5: (S) => S.stats.missionsDone >= 1,
    t6: (S) => S.techs.unlocked.includes("array_spirit"),
    t7: (S) => S.rooms.some((r) => r.type === "train") && S.sectPower() >= 140,
    t8: (S) => S.stats.raidsWon >= 1,
  };

  /* ── names ───────────────────────────────── */
  D.SURNAMES = ["林", "苏", "陈", "韩", "叶", "秦", "白", "萧", "顾", "沈", "云", "洛", "慕", "楚", "陆", "江", "温", "谢", "慕容", "上官", "南宫", "东方", "司徒", "百里"];
  D.GIVEN_M = ["锋", "尘", "昊", "宇", "星辰", "天", "无涯", "惊鸿", "沐风", "知秋", "子墨", "长歌", "玄", "云帆", "剑心", "浩然", "景行", "寒", "青", "归海"];
  D.GIVEN_F = ["霜月", "婉儿", "清瑶", "灵曦", "雪", "若水", "诗涵", "语嫣", "青黛", "紫烟", "梦蝶", "听雨", "浅月", "凝霜", "采薇", "素心", "云裳", "落霞", "芷若", "念初"];

  /* ── misc constants ──────────────────────── */
  D.GRID = { w: 16, h: 12 };
  D.DAY_SECS = 600; // 1 game-day at 1x speed = 10 real minutes
  D.SHICHEN = ["子时", "丑时", "寅时", "卯时", "辰时", "巳时", "午时", "未时", "申时", "酉时", "戌时", "亥时"];
  D.BASE_CAPS = { stone: 2500, herb: 300, ore: 200, pill: 60, talisman: 60, art: 40 };
  D.SQUAD_MAX = 5;
})();
