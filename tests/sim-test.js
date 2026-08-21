/* Headless engine test — run: node tests/sim-test.js */
"use strict";
const fs = require("fs");
const path = require("path");

// fake browser globals so the IIFEs attach to window.G
global.window = global;
global.document = undefined;
global.localStorage = { _m: {}, setItem(k, v) { this._m[k] = v; }, getItem(k) { return this._m[k] || null; }, removeItem(k) { delete this._m[k]; } };

const load = (f) => eval(fs.readFileSync(path.join(__dirname, "..", f), "utf8"));
load("js/data.js");
load("js/state.js");
load("js/battle.js");

const G = global.G;
const D = G;
let pass = 0, fail = 0;
function ok(cond, msg) {
  if (cond) { pass++; console.log("  ✓ " + msg); }
  else { fail++; console.log("  ✗ FAIL: " + msg); }
}

// G.Battle.showBattle is browser-only; emulate direct resolve in node
G.Battle.showBattle = (setup, onDone) => {
  const res = G.Battle.simulate(setup);
  onDone(res);
};

/* ── 1. new game basics ───────────────────── */
console.log("1) new game");
const S = new G.GameState();
S.newGame();
ok(S.disciples.length === 2, "starts with 2 disciples");
ok(S.rooms.length === 1 && S.rooms[0].type === "hall", "starts with main hall");
ok(S.res.stone === 400, "starts with 400 stones");

/* ── 2. build & production ────────────────── */
console.log("2) build & production");
let res = S.placeRoom("med", 2, 2);
ok(res === "ok", "place meditation room: " + res);
res = S.placeRoom("garden", 2, 4);
ok(res === "ok", "place herb garden");
S.res.stone += 200; // fund the mine
res = S.placeRoom("mine", 0, 6);
ok(res === "ok", "place mine");
const med = S.rooms.find((r) => r.type === "med");
S.assignTo(S.disciples[0], med.id);
const xp0 = S.disciples[0].xp, step0 = S.disciples[0].step;
S.update(60);
ok(S.disciples[0].xp > xp0, "meditation gains xp (" + xp0.toFixed(1) + " → " + S.disciples[0].xp.toFixed(1) + ")");
ok(S.res.herb > 0, "garden produces herbs (" + S.res.herb.toFixed(1) + ")");
ok(S.res.stone > 400 - 150 - 150 - 120, "mine produces stones (" + S.res.stone.toFixed(0) + ")");
ok(S.disciples[0].step >= step0, "disciple levels (step " + S.disciples[0].step + ")");

/* ── 3. alchemy ───────────────────────────── */
console.log("3) alchemy");
res = S.placeRoom("alch", 4, 6);
ok(res === "ok", "place alchemy hall");
const alch = S.rooms.find((r) => r.type === "alch");
S.assignTo(S.disciples[1], alch.id);
S.res.herb = 50;
S.update(120);
ok(S.stats.pillsMade > 0, "pills brewed: " + S.stats.pillsMade);
ok(S.res.pill_qi > 0, "qi pills in stock: " + S.res.pill_qi);

/* ── 4. mission flow ──────────────────────── */
console.log("4) mission flow");
const before = S.res.stone;
res = S.startMission("q_herb", [S.disciples[0].id, S.disciples[1].id]);
ok(res === "ok", "start mission: " + res);
ok(S.disciples[0].mission !== null, "disciple is away");
S.update(25);
ok(S.stats.missionsDone >= 1 || S.stats.battlesLost >= 1, "mission resolved");
ok(S.disciples[0].mission === null, "disciple returned");
console.log("   mission result: won=" + (S.stats.missionsDone >= 1) + ", stones " + before + "→" + S.res.stone + ", rep " + S.rep);

/* ── 5. raid flow (full squad, mirroring real progression) ── */
console.log("5) raid flow");
S.disciples.forEach((d) => { d.step = Math.min(6, d.step + 3); d.woundedUntil = 0; d.xp = 0; });
for (let i = 0; i < 3; i++) S.spawnDisciple({ aptitude: 1.1, step: 4 });
S.placeRoom("array", 10, 8); // guardian array for later
const raidSquad = S.disciples.slice(0, 5).map((d) => d.id);
res = S.startRaid("r_heifeng", raidSquad);
ok(res === "ok", "start raid: " + res);
S.update(30);
ok(S.stats.raidsWon + S.stats.battlesLost >= 1, "raid resolved (won=" + S.stats.raidsWon + ", lost=" + S.stats.battlesLost + ")");
ok(S.stats.raidsWon >= 1, "full squad of qi 4-6 beats 黑风寨");

/* ── 6. breakthrough stats ────────────────── */
console.log("6) breakthrough");
const d = S.disciples[0];
d.step = 8; d.xp = D.STEPS[8].xp; d.woundedUntil = 0;
let wins = 0, tries = 200;
for (let i = 0; i < tries; i++) {
  d.step = 8; d.xp = D.STEPS[8].xp; d.woundedUntil = 0;
  S.res.pill_zhu = 200;
  const r = S.tryBreakthrough(d, true);
  if (r && r.ok) wins++;
}
ok(wins / tries > 0.8 && wins / tries < 1.0, "breakthrough w/ pill ≈ 94%: got " + (wins / tries * 100).toFixed(0) + "%");
d.woundedUntil = 0;

/* ── 7. battle balance ────────────────────── */
console.log("7) battle balance (equal power)");
function mkF(name, power, side) {
  const p = power;
  return { refId: name, name, root: "fire", style: "aggressive", atk: Math.round(2.5 + p * 0.5), hp: Math.round(22 + p * 2.2), interval: 1, crit: 0.06, isPlayer: side === "A" };
}
let aWins = 0;
const N = 400;
for (let i = 0; i < N; i++) {
  const A = [mkF("甲", 20, "A"), mkF("乙", 20, "A"), mkF("丙", 20, "A")];
  const B = [mkF("狼", 20, "B"), mkF("蛇", 20, "B"), mkF("熊", 20, "B")];
  const b = G.Battle.setup({ sideA: A, sideB: B });
  const r = G.Battle.simulate(b);
  if (r.winner === "A") aWins++;
}
const rate = aWins / N;
ok(rate > 0.35 && rate < 0.65, "equal fight ≈ 50% win: " + (rate * 100).toFixed(0) + "%");

// healer composition check
console.log("8) healer composition");
let healWins = 0;
for (let i = 0; i < 100; i++) {
  const A = [mkF("剑", 22, "A"), mkF("医", 16, "A"), mkF("剑", 22, "A")];
  A[1].style = "healer"; A[1].atk = 18;
  const B = [mkF("狼", 20, "B"), mkF("蛇", 20, "B"), mkF("熊", 20, "B")];
  const b = G.Battle.setup({ sideA: A, sideB: B });
  if (G.Battle.simulate(b).winner === "A") healWins++;
}
console.log("   healer squad win rate: " + healWins + "% (baseline " + (rate * 100).toFixed(0) + "%)");
ok(healWins > rate * 100 * 0.8, "healer not strictly worse");

/* ── 9. save/load ─────────────────────────── */
console.log("9) save/load roundtrip");
S.save();
const S2 = G.GameState.loadSave();
ok(!!S2, "load returns state");
ok(S2.sectName === S.sectName && S2.rooms.length === S.rooms.length && S2.disciples.length === S.disciples.length, "state matches");
ok(S2 instanceof G.GameState, "instance type ok");

/* ── 10. soak test ────────────────────────── */
console.log("10) soak 2h sim with full economy");
const T = new G.GameState();
T.newGame();
T.res.stone = 5000; // fund the full economy
T.rep = 300; // unlock all building tiers
T.rooms.find((r) => r.type === "hall").lvl = 3; // raise room cap (7 → 13)
const builds = [["med", 2, 2], ["garden", 2, 4], ["mine", 0, 6], ["alch", 4, 6], ["train", 6, 8], ["dorm", 10, 2], ["store", 10, 4], ["lib", 8, 2], ["forge", 12, 4], ["talis", 12, 6]];
builds.forEach(([t, x, y]) => { if (T.placeRoom(t, x, y) !== "ok") console.log("   build fail: " + t); });
for (let i = 0; i < 12; i++) T.spawnDisciple({ aptitude: 1.2 });
T.tryAutoAssign();
T.rooms.forEach((r) => { if (["med", "garden", "mine", "train"].includes(r.type)) r.lvl = Math.min(5, r.lvl + 2); });
// explicitly staff the craft halls so every production path runs
const idleD = T.disciples.filter((d) => !d.workRoom);
[["alch", "alchemy", 0.9], ["forge", "forge", 0.9], ["talis", "talisman", 0.9]].forEach(([rt, sk, v], i) => {
  const rm = T.rooms.find((r) => r.type === rt);
  if (rm && idleD[i]) { idleD[i].skills[sk] = 40; T.assignTo(idleD[i], rm.id); }
});
T.res.herb = 200; T.res.ore = 100; T.res.stone = 3000;
T.startResearch("array_spirit");
let errors = 0;
try {
  for (let t = 0; t < 7200; t += 10) {
    T.update(10);
    if (T.stats.pillsMade > 0 && T.stats.artsMade > 0 && T.stats.talisMade > 0) break;
  }
} catch (e) { errors++; console.log("   EXCEPTION: " + e.stack); }
ok(errors === 0, "no exceptions in soak");
console.log("   after soak: pills=" + T.stats.pillsMade + " arts=" + T.stats.artsMade + " talis=" + T.stats.talisMade +
  " rep=" + T.rep + " power=" + T.sectPower() + " step(max)=" + Math.max(...T.disciples.map((x) => x.step)));

/* ── 11. defense battle ───────────────────── */
console.log("11) defense battle");
const T2 = new G.GameState();
T2.newGame();
for (let i = 0; i < 3; i++) T2.spawnDisciple({ aptitude: 1.1, step: 5 });
T2.disciples.forEach((d) => { d.step = 6; });
T2.rep = 300; // unlock guardian array (rep Lv.4)
T2.placeRoom("array", 2, 2);
T2.rooms.find((r) => r.type === "array").lvl = 2;
T2.startDefense("raid", "r_heifeng");
T2.defense.until = T2.time; // force immediate
T2.update(1);
ok(T2.stats.defensesWon + T2.stats.battlesLost >= 1, "defense resolved (won=" + T2.stats.defensesWon + ")");
ok(T2.stats.defensesWon >= 1, "defense w/ array + qi-6 squad holds the gate");

console.log("\n════ RESULT: " + pass + " passed, " + fail + " failed ════");
process.exit(fail ? 1 : 0);
