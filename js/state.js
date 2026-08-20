/* ═══════════════════════════════════════════════════════
   仙门掌门 · Sect Master — GAME STATE & SIMULATION
   ═══════════════════════════════════════════════════════ */
(function () {
  "use strict";
  const D = (window.G = window.G || {});
  const SAVE_KEY = "sect-master-save-v1";

  // global event hook (toasts, particles, battle trigger) — wired in main.js
  D.onEvent = () => { };

  class GameState {
    constructor() {
      this.version = 1;
      this.sectName = "青云门";
      this.time = 0;
      this.speed = 1;
      this.res = { stone: 400, herb: 40, ore: 10, pill_qi: 0, pill_pei: 0, pill_zhu: 0, pill_jin: 0, pill_ying: 0, pill_shen: 0, pill_du: 0, talisman_fire: 0, talisman_vajra: 0 };
      this.arts = []; // {id, bp, tier, stat}
      this.rep = 0;
      this.rooms = [];
      this.disciples = [];
      this.techs = { unlocked: [], researching: null };
      this.tasksDone = [];
      this.stats = { pillsMade: 0, artsMade: 0, talisMade: 0, missionsDone: 0, missionsWon: 0, raidsWon: 0, defensesWon: 0, battlesLost: 0, breakthroughs: 0 };
      this.missions = { active: [], lastDone: {} };
      this.raids = { last: {} };
      this.recruit = { offers: [], nextRefresh: 0 };
      this.events = { nextAt: D.rand(120, 200), pending: null, nextRaidAt: D.rand(300, 420) };
      this.defense = null; // {kind, targetId, until, warned}
      this.buildMode = null;
    }

    /* ── new game setup ────────────────────── */
    newGame() {
      const hall = { id: D.rid(), type: "hall", x: 6, y: 4, lvl: 1 };
      this.rooms = [hall];
      // deterministic-ish starting rocks
      const rocks = [[1, 1], [13, 9], [0, 10], [14, 2], [2, 9], [12, 1], [15, 6], [4, 0], [9, 11], [7, 11]];
      this.rocks = rocks;
      const d1 = this.spawnDisciple({ name: "林锋", gender: "m", root: "fire", aptitude: 1.18, step: 2 });
      const d2 = this.spawnDisciple({ name: "苏婉儿", gender: "f", root: "wood", aptitude: 1.08, step: 1 });
      this.disciples = [d1, d2];
      this.refreshRecruit(true);
      this.events = { nextAt: this.time + D.rand(90, 160), pending: null, nextRaidAt: this.time + D.rand(360, 480) };
      this.tryAutoAssign();
    }

    /* ── resources ─────────────────────────── */
    caps() {
      let mult = 1;
      this.rooms.forEach((r) => { if (r.type === "store") mult += 0.35 * r.lvl; });
      const c = D.BASE_CAPS;
      return {
        stone: Math.round(c.stone * mult), herb: Math.round(c.herb * mult), ore: Math.round(c.ore * mult),
        pill: Math.round(c.pill * mult), talisman: Math.round(c.talisman * mult), art: Math.round(c.art * mult),
      };
    }
    pillCount() { return this.res.pill_qi + this.res.pill_pei + this.res.pill_zhu + this.res.pill_jin + this.res.pill_ying + this.res.pill_shen + this.res.pill_du; }
    talisCount() { return this.res.talisman_fire + this.res.talisman_vajra; }
    addRes(key, n, quiet) {
      const cap = this.caps();
      const pillKeys = ["pill_qi", "pill_pei", "pill_zhu", "pill_jin", "pill_ying", "pill_shen", "pill_du"];
      const talisKeys = ["talisman_fire", "talisman_vajra"];
      let capped = false;
      if (pillKeys.includes(key)) {
        const total = this.pillCount();
        if (n > 0 && total >= cap.pill) { capped = true; return; }
        const room = cap.pill - total;
        n = n > 0 ? Math.min(n, room) : n;
        this.res[key] = Math.max(0, (this.res[key] || 0) + n);
      } else if (talisKeys.includes(key)) {
        const total = this.talisCount();
        if (n > 0 && total >= cap.talisman) { capped = true; return; }
        const room = cap.talisman - total;
        n = n > 0 ? Math.min(n, room) : n;
        this.res[key] = Math.max(0, (this.res[key] || 0) + n);
      } else {
        const before = this.res[key] || 0;
        let after = before + n;
        if (after > cap[key]) { capped = true; after = cap[key]; }
        this.res[key] = Math.max(0, after);
      }
      if (capped && !quiet) D.onEvent("cap", { key });
      return capped;
    }
    addRep(n) {
      const before = D.repLevel(this.rep);
      this.rep += n;
      const after = D.repLevel(this.rep);
      if (after > before) D.onEvent("replevel", { lvl: after });
    }

    /* ── energy / caps / power ─────────────── */
    energyInfo() {
      let supply = 0, demand = 0;
      this.rooms.forEach((r) => {
        if (r.type === "hall") supply += 4 + 4 * r.lvl;
        else demand += 1;
      });
      if (this.techs.unlocked.includes("array_spirit")) supply += 6;
      return { supply, demand, factor: demand <= supply ? 1 : 0.55 };
    }
    maxRooms() { return 4 + 3 * this.hallLevel(); }
    maxDisciples() {
      let cap = 3 + 2 * this.hallLevel();
      this.rooms.forEach((r) => { if (r.type === "dorm") cap += 2 * r.lvl; });
      return cap;
    }
    hallLevel() { const h = this.rooms.find((r) => r.type === "hall"); return h ? h.lvl : 1; }
    hallRoom() { return this.rooms.find((r) => r.type === "hall"); }

    discipleStats(d) {
      const s = D.STEPS[d.step], r = D.ROOTS[d.root];
      let atk = s.atk * (1 + r.atk);
      let hp = s.hp * (1 + r.hp);
      const wep = this.getArt(d.equip && d.equip.weapon);
      const arm = this.getArt(d.equip && d.equip.armor);
      if (wep) atk += wep.stat;
      if (arm) hp += arm.stat;
      const tech = this.techs.unlocked;
      if (tech.includes("sword_qingfeng")) atk *= 1.1;
      if (tech.includes("sword_formation")) atk *= 1.15;
      if (tech.includes("iron_wall")) hp *= 1.15;
      if (tech.includes("golden_body")) hp *= 1.2;
      let squadAtk = 0;
      this.rooms.forEach((rr) => { if (rr.type === "train") squadAtk += 0.005 * rr.lvl; });
      atk *= 1 + squadAtk;
      if (this.wounded(d)) { atk *= 0.7; hp *= 0.7; }
      const interval = 1.0 * (1 - r.spd) * (tech.includes("swift_step") ? 0.92 : 1);
      const crit = 0.08 + (tech.includes("sword_qingfeng") ? 0.05 : 0);
      const power = Math.round(atk + hp * 0.6);
      return { atk: Math.round(atk), hp: Math.round(hp), power, interval, crit, style: r.style, root: d.root };
    }
    sectPower() {
      let p = 0;
      this.disciples.forEach((d) => { p += this.discipleStats(d).power; });
      return Math.round(p);
    }
    squadPower(ids) {
      let p = 0;
      ids.forEach((id) => { const d = this.getDisciple(id); if (d) p += this.discipleStats(d).power; });
      return Math.round(p);
    }

    /* ── rooms ─────────────────────────────── */
    roomAt(x, y) { return this.rooms.find((r) => x >= r.x && x < r.x + D.ROOMS[r.type].size[0] && y >= r.y && y < r.y + D.ROOMS[r.type].size[1]); }
    isRock(x, y) { return (this.rocks || []).some(([rx, ry]) => rx === x && ry === y); }
    canPlace(type, x, y) {
      const def = D.ROOMS[type], [w, h] = def.size;
      if (type === "hall") return "no";
      if (x < 0 || y < 0 || x + w > D.GRID.w || y + h > D.GRID.h) return "out";
      if (this.rooms.length >= this.maxRooms() && type !== "hall") return "maxrooms";
      const repLvl = D.repLevel(this.rep);
      if (def.unlockRep && repLvl < def.unlockRep) return "rep";
      for (let i = 0; i < w; i++) for (let j = 0; j < h; j++) {
        if (this.isRock(x + i, y + j)) return "rock";
        if (this.roomAt(x + i, y + j)) return "overlap";
      }
      if (!this.canAfford({ stone: def.cost.stone[0] })) return "cost";
      return "ok";
    }
    placeRoom(type, x, y) {
      const st = this.canPlace(type, x, y);
      if (st !== "ok") return st;
      const def = D.ROOMS[type];
      this.payCost({ stone: def.cost.stone[0] });
      const room = {
        id: D.rid(), type, x, y, lvl: 1, builtAt: this.time,
        assigned: [], recipe: null, progress: 0, damaged: false,
      };
      if (type === "alch") room.recipe = "pill_qi";
      if (type === "forge") room.recipe = "sword";
      if (type === "talis") room.recipe = "talisman_fire";
      this.rooms.push(room);
      this.tryAutoAssign();
      D.onEvent("roomplaced", { room });
      return "ok";
    }
    canAfford(cost) { return Object.keys(cost).every((k) => (this.res[k] || 0) >= cost[k]); }
    payCost(cost) { Object.keys(cost).forEach((k) => { this.res[k] = Math.max(0, (this.res[k] || 0) - cost[k]); }); }

    upgradeCost(room) {
      const def = D.ROOMS[room.type];
      const lvl = room.lvl;
      if (lvl >= def.maxLvl) return null;
      return { stone: def.cost.stone[lvl], time: def.time[lvl] };
    }
    upgradeRoom(room) {
      const c = this.upgradeCost(room);
      if (!c || !this.canAfford(c)) return false;
      const repLvl = D.repLevel(this.rep);
      if (room.type === "hall" && room.lvl >= 2 && repLvl < [0, 0, 3, 5, 8][room.lvl]) return false;
      this.payCost(c);
      room.lvl++;
      D.onEvent("roomupgraded", { room });
      return true;
    }
    demolishRoom(room) {
      if (room.type === "hall") return false;
      const def = D.ROOMS[room.type];
      const refund = Math.round(def.cost.stone[room.lvl - 1] * 0.4);
      // free assigned disciples
      room.assigned.forEach((id) => { const d = this.getDisciple(id); if (d) d.assignTo(null); });
      this.rooms = this.rooms.filter((r) => r.id !== room.id);
      if (room.type === "lib" && this.techs.researching) this.cancelResearch();
      this.res.stone = Math.min(this.caps().stone, this.res.stone + refund);
      D.onEvent("roomdemolished", { room });
      return true;
    }
    repairRoom(room) {
      if (!room.damaged) return false;
      const cost = Math.round(D.ROOMS[room.type].cost.stone[room.lvl - 1] * 0.25) || 40;
      if (this.res.stone < cost) return false;
      this.res.stone -= cost;
      room.damaged = false;
      return true;
    }

    /* ── disciples ─────────────────────────── */
    getDisciple(id) { return this.disciples.find((d) => d.id === id); }
    getArt(id) { return this.arts.find((a) => a.id === id); }
    allDisciples() { return this.disciples; }

    makeName(gender) {
      const sur = D.pick(D.SURNAMES);
      const g = gender === "f" ? D.pick(D.GIVEN_F) : D.pick(D.GIVEN_M);
      return sur + g;
    }
    rollRoot() {
      const r = Math.random();
      if (r < 0.03) return "heaven";
      if (r < 0.12) return D.pick(["thunder", "ice", "wind"]);
      return D.pick(["metal", "wood", "water", "fire", "earth"]);
    }
    spawnDisciple(o) {
      o = o || {};
      const gender = o.gender || (Math.random() < 0.45 ? "f" : "m");
      const d = {
        id: D.rid(), name: o.name || this.makeName(gender), gender,
        root: o.root || this.rollRoot(), aptitude: o.aptitude || D.rand(0.75, 1.45),
        step: o.step || 0, xp: 0,
        skills: {
          plant: o.plant ?? D.randi(0, 12), alchemy: o.alchemy ?? D.randi(0, 12),
          forge: o.forge ?? D.randi(0, 12), talisman: o.talisman ?? D.randi(0, 12),
        },
        equip: o.equip || { weapon: null, armor: null },
        workRoom: null, mission: null, woundedUntil: 0,
      };
      this.disciples.push(d);
      return d;
    }
    wounded(d) { return this.time < d.woundedUntil; }
    busy(d) { return !!d.mission || this.wounded(d); }
    assignTo(d, roomId) {
      const old = this.rooms.find((r) => r.id === d.workRoom);
      if (old) old.assigned = old.assigned.filter((x) => x !== d.id);
      d.workRoom = roomId;
      if (roomId) {
        const r = this.rooms.find((x) => x.id === roomId);
        if (r && !r.assigned.includes(d.id)) r.assigned.push(d.id);
      }
    }
    autoAssignable() { return this.disciples.filter((d) => !d.workRoom && !this.busy(d)); }
    tryAutoAssign() {
      const priority = ["med", "mine", "garden", "train", "alch", "forge", "talis"];
      const idle = this.autoAssignable();
      idle.forEach((d) => {
        for (const type of priority) {
          const room = this.rooms.find((r) => r.type === type && r.assigned.length < this.roomSlots(r));
          if (room) { this.assignTo(d, room.id); return; }
        }
      });
    }
    roomSlots(r) { const fx = D.roomEffects[r.type]; if (!fx) return 0; return fx(r.lvl).slots; }

    recruitCost(d) {
      const r = D.ROOTS[d.root];
      return 110 + r.rare * 140 + (d.aptitude > 1.25 ? 70 : d.aptitude > 1.05 ? 30 : 0);
    }
    refreshRecruit(force) {
      if (!force && this.time < this.recruit.nextRefresh) return;
      this.recruit.offers = [];
      for (let i = 0; i < 3; i++) {
        const gender = Math.random() < 0.45 ? "f" : "m";
        const o = { name: this.makeName(gender), gender, root: this.rollRoot(), aptitude: D.rand(0.75, 1.45) };
        o.cost = this.recruitCost(o);
        this.recruit.offers.push(o);
      }
      this.recruit.nextRefresh = this.time + 90;
    }
    hire(o) {
      if (this.disciples.length >= this.maxDisciples()) return "full";
      if (!this.canAfford({ stone: o.cost })) return "cost";
      this.payCost({ stone: o.cost });
      const d = this.spawnDisciple(o);
      this.recruit.offers = this.recruit.offers.filter((x) => x !== o);
      this.tryAutoAssign();
      D.onEvent("recruit", { d });
      return "ok";
    }

    /* ── cultivation ───────────────────────── */
    gateForStep(step) { return D.MAJOR_GATE.find((g) => g.from === step); }
    giveXp(d, n) {
      d.xp += n;
      this.checkLevel(d);
    }
    checkLevel(d) {
      let guard = 0;
      while (guard++ < 30 && d.step < D.STEPS.length - 1) {
        const need = D.STEPS[d.step].xp;
        const gate = this.gateForStep(d.step);
        if (d.xp >= need) {
          if (gate) { d.xp = need; break; } // major gate: wait for manual breakthrough
          d.xp -= need; d.step++;
          D.onEvent("levelup", { d, step: d.step });
        } else break;
      }
    }
    tryBreakthrough(d, usePill) {
      const gate = this.gateForStep(d.step);
      if (!gate) return null;
      const need = D.STEPS[d.step].xp;
      if (d.xp < need) return { err: "notfull" };
      let pillUsed = false;
      if (usePill) {
        if ((this.res[gate.pill] || 0) < 1) return { err: "nopill" };
        this.res[gate.pill]--;
        pillUsed = true;
      }
      const chance = D.clamp(gate.chance + (pillUsed ? 0.22 : 0), 0.05, 0.98);
      if (Math.random() < chance) {
        d.step = gate.to; d.xp = 0;
        this.stats.breakthroughs++;
        D.onEvent("breakthrough", { d, step: d.step });
        return { ok: true, chance };
      }
      d.xp = Math.round(d.xp * (1 - gate.failLose));
      D.onEvent("breakfail", { d, chance });
      return { ok: false, chance };
    }
    usePill(d, key) {
      const rec = D.RECIPES.find((r) => r.key === key);
      if (!rec || (this.res[key] || 0) < 1) return false;
      if (rec.breakPill) return false;
      const need = D.STEPS[d.step].xp;
      const gain = Math.round(need * rec.xpMult);
      this.res[key]--;
      this.giveXp(d, gain);
      D.onEvent("pilluse", { d, key });
      return true;
    }

    /* ── production tick ───────────────────── */
    production(dt) {
      const energy = this.energyInfo().factor;
      this.rooms.forEach((r) => {
        if (r.damaged) return;
        const eff = energy * (r.type === "hall" ? 1 : 1);
        switch (r.type) {
          case "med": {
            const rate = D.roomEffects.med(r.lvl).xpRate;
            r.assigned.forEach((id) => {
              const d = this.getDisciple(id);
              if (!d || this.busy(d)) return;
              const rdef = D.ROOTS[d.root];
              this.giveXp(d, rate * d.aptitude * (1 + rdef.xp) * eff * dt);
            });
            break;
          }
          case "train": {
            const rate = D.roomEffects.train(r.lvl).xpRate;
            r.assigned.forEach((id) => {
              const d = this.getDisciple(id);
              if (!d || this.busy(d)) return;
              this.giveXp(d, rate * d.aptitude * eff * dt);
            });
            break;
          }
          case "garden": {
            let skillSum = 0, workers = 0;
            r.assigned.forEach((id) => { const d = this.getDisciple(id); if (d && !this.busy(d)) { skillSum += d.skills.plant; workers++; } });
            const rate = D.roomEffects.garden(r.lvl).herbRate * (1 + skillSum * 0.006);
            this.addRes("herb", rate * eff * dt);
            this.trainSkill(r, "plant", workers, dt, 0.5);
            break;
          }
          case "mine": {
            let workers = 0;
            r.assigned.forEach((id) => { const d = this.getDisciple(id); if (d && !this.busy(d)) workers++; });
            const rate = D.roomEffects.mine(r.lvl).stoneRate * (1 + 0.2 * Math.min(workers, 3));
            this.addRes("stone", rate * eff * dt);
            this.trainSkill(r, "talisman", 0, dt, 0); // miners use no skill; keep signature simple
            break;
          }
          case "alch": {
            const rec = D.RECIPES.find((x) => x.key === r.recipe);
            if (!rec) break;
            let skillSum = 0, workers = 0;
            r.assigned.forEach((id) => { const d = this.getDisciple(id); if (d && !this.busy(d)) { skillSum += d.skills.alchemy; workers++; } });
            if (workers === 0) break; // needs an alchemist
            const dao = this.techs.unlocked.includes("dao_pill") ? 1.3 : 1;
            const speed = (1 - Math.min(skillSum * 0.005, 0.6)) * (1 + D.roomEffects.alch(r.lvl).speedBonus) * dao * eff;
            r.progress += dt * speed;
            const total = rec.time;
            while (r.progress >= total) {
              if (this.canAfford(rec.cost)) {
                this.payCost(rec.cost);
                this.addRes(rec.key, 1);
                this.stats.pillsMade++;
                r.progress -= total;
                D.onEvent("craft", { room: r, kind: "pill", key: rec.key });
              } else { r.progress = total; break; }
            }
            this.trainSkill(r, "alchemy", workers, dt, 0.8);
            break;
          }
          case "forge": {
            const bp = D.BLUEPRINTS.find((x) => x.key === r.recipe);
            if (!bp) break;
            let skillSum = 0, workers = 0;
            r.assigned.forEach((id) => { const d = this.getDisciple(id); if (d && !this.busy(d)) { skillSum += d.skills.forge; workers++; } });
            if (workers === 0) break;
            const dao = this.techs.unlocked.includes("forge_master") ? 1.3 : 1;
            const speed = (1 - Math.min(skillSum * 0.005, 0.6)) * (1 + D.roomEffects.forge(r.lvl).speedBonus) * dao * eff;
            r.progress += dt * speed;
            while (r.progress >= bp.time) {
              if (this.arts.length < this.caps().art && this.canAfford(bp.cost)) {
                this.payCost(bp.cost);
                this.arts.push(this.rollArt(bp, r.lvl, skillSum));
                this.stats.artsMade++;
                r.progress -= bp.time;
                D.onEvent("craft", { room: r, kind: "art", key: bp.key });
              } else { r.progress = bp.time; break; }
            }
            this.trainSkill(r, "forge", workers, dt, 0.8);
            break;
          }
          case "talis": {
            const tp = D.TALISMANS.find((x) => x.key === r.recipe);
            if (!tp) break;
            let skillSum = 0, workers = 0;
            r.assigned.forEach((id) => { const d = this.getDisciple(id); if (d && !this.busy(d)) { skillSum += d.skills.talisman; workers++; } });
            if (workers === 0) break;
            const speed = (1 - Math.min(skillSum * 0.005, 0.6)) * (1 + D.roomEffects.talis(r.lvl).speedBonus) * eff;
            r.progress += dt * speed;
            while (r.progress >= tp.time) {
              if (this.canAfford(tp.cost)) {
                this.payCost(tp.cost);
                this.addRes(tp.key, 1);
                this.stats.talisMade++;
                r.progress -= tp.time;
                D.onEvent("craft", { room: r, kind: "talis", key: tp.key });
              } else { r.progress = tp.time; break; }
            }
            this.trainSkill(r, "talisman", workers, dt, 0.8);
            break;
          }
          case "lib": {
            const tech = this.techs.researching;
            if (!tech) break;
            const tdef = D.TECHS.find((t) => t.key === tech);
            const speed = (1 + D.roomEffects.lib(r.lvl).speedBonus);
            r.progress += dt * speed;
            if (r.progress >= tdef.time) {
              this.techs.unlocked.push(tech);
              this.techs.researching = null;
              r.progress = 0;
              D.onEvent("techdone", { key: tech });
            }
            break;
          }
        }
      });
    }
    trainSkill(r, key, workers, dt, rate) {
      if (workers <= 0) return;
      r.assigned.forEach((id) => {
        const d = this.getDisciple(id);
        if (d && !this.busy(d) && d.skills[key] < 100) {
          d.skills[key] = Math.min(100, d.skills[key] + rate * d.aptitude * dt);
        }
      });
    }
    rollArt(bp, lvl, skillSum) {
      const score = lvl * 4.5 + skillSum * 0.35 + Math.random() * 100 + (this.techs.unlocked.includes("forge_master") ? 8 : 0);
      let tier = 0;
      if (score >= 99) tier = 4; else if (score >= 90) tier = 3; else if (score >= 72) tier = 2; else if (score >= 45) tier = 1;
      const stat = Math.round(bp.base[tier] * D.rand(0.9, 1.15));
      return { id: D.rid(), bp: bp.key, tier, stat };
    }

    /* ── tech ──────────────────────────────── */
    startResearch(key) {
      if (this.techs.researching || this.techs.unlocked.includes(key)) return false;
      const t = D.TECHS.find((x) => x.key === key);
      const repLvl = D.repLevel(this.rep);
      if (repLvl < t.unlockRep || !this.canAfford(t.cost)) return false;
      this.payCost(t.cost);
      const lib = this.rooms.find((r) => r.type === "lib" && !r.damaged);
      if (!lib) return false;
      this.techs.researching = key;
      lib.progress = 0;
      return true;
    }
    cancelResearch() {
      if (!this.techs.researching) return;
      const t = D.TECHS.find((x) => x.key === this.techs.researching);
      Object.keys(t.cost).forEach((k) => { this.addRes(k, t.cost[k]); });
      this.techs.researching = null;
      const lib = this.rooms.find((r) => r.type === "lib");
      if (lib) lib.progress = 0;
    }

    /* ── missions & raids ──────────────────── */
    startMission(mid, squadIds) {
      let mdef = null, region = null;
      for (const rg of D.REGIONS) { const m = rg.missions.find((x) => x.id === mid); if (m) { mdef = m; region = rg; break; } }
      if (!mdef) return "nomission";
      const last = this.missions.lastDone[mid];
      if (last && this.time - last < mdef.cooldown) return "cd";
      const ok = this.checkSquad(squadIds);
      if (ok !== "ok") return ok;
      const until = this.time + mdef.duration;
      squadIds.forEach((id) => { const d = this.getDisciple(id); d.mission = { kind: "mission", targetId: mid, until }; this.assignTo(d, null); });
      this.missions.active.push({ kind: "mission", targetId: mid, squad: squadIds, until, duration: mdef.duration });
      D.onEvent("missionsent", { mid });
      return "ok";
    }
    startRaid(rid, squadIds) {
      let rdef = null, region = null;
      for (const rg of D.REGIONS) { const r = rg.raids.find((x) => x.id === rid); if (r) { rdef = r; region = rg; break; } }
      if (!rdef) return "noraid";
      if (D.repLevel(this.rep) < D.repLevel(region.rep)) return "rep";
      const last = this.raids.last[rid];
      if (last && this.time - last < rdef.cooldown) return "cd";
      const ok = this.checkSquad(squadIds);
      if (ok !== "ok") return ok;
      const until = this.time + 25; // travel time
      squadIds.forEach((id) => { const d = this.getDisciple(id); d.mission = { kind: "raid", targetId: rid, until }; this.assignTo(d, null); });
      this.missions.active.push({ kind: "raid", targetId: rid, squad: squadIds, until, duration: 25 });
      D.onEvent("raidsent", { rid });
      return "ok";
    }
    checkSquad(ids) {
      if (!ids.length) return "empty";
      if (ids.length > D.SQUAD_MAX) return "toomany";
      for (const id of ids) {
        const d = this.getDisciple(id);
        if (!d) return "missing";
        if (this.busy(d)) return "busy";
      }
      return "ok";
    }
    availableFighters() { return this.disciples.filter((d) => !this.busy(d)); }

    startDefense(kind, targetId) {
      // called by event choice / raid timer
      this.defense = { kind, targetId, until: this.time + 22, warned: false };
      D.onEvent("defensewarning", { kind, targetId });
    }

    resolveDefense() {
      if (!this.defense) return;
      const def = this.defense;
      this.defense = null;
      const fighters = this.disciples.filter((d) => !d.mission && !this.wounded(d));
      if (!fighters.length) { D.onEvent("toast", { text: "宗门无人迎战……", bad: true }); this.applyDefenseLoss(def); return; }
      const ids = fighters.map((f) => f.id);
      const setup = this.buildBattle(def.kind === "beasts" ? "beasts" : "raid", def.targetId, ids, true);
      G.Battle.showBattle(setup, (result) => this.applyBattle(result, { kind: "defense", targetId: def.targetId, squad: ids, defender: true }));
    }

    buildBattle(kind, targetId, playerIds, isDefense) {
      const player = playerIds.map((id) => { const d = this.getDisciple(id); const st = this.discipleStats(d); return { refId: id, name: d.name, root: d.root, style: st.style, atk: st.atk, hp: st.hp, interval: st.interval, crit: st.crit, isPlayer: true }; });
      let enemyPower = 0, count = 3, enemyDef = null, region = null, useBandits = false;
      if (kind === "beasts") { enemyPower = Math.round(this.sectPower() * 0.72); count = D.randi(3, 4); }
      else {
        for (const rg of D.REGIONS) {
          const r = rg.raids.find((x) => x.id === targetId);
          if (r) { enemyDef = r; region = rg; break; }
          const m = rg.missions.find((x) => x.id === targetId);
          if (m) { enemyDef = m; region = rg; break; }
        }
        if (!enemyDef) { enemyPower = 50; }
        else {
          enemyPower = enemyDef.power;
          if (enemyDef.type === "escort") { count = 3; useBandits = true; }
          else if (enemyDef.type === "realm") { count = D.randi(4, 5); useBandits = Math.random() < 0.5; }
          else if (enemyDef.type === "gather") { count = 3; }
          else if (enemyDef.type === "hunt") { count = 4; }
          else if (enemyDef.cooldown && enemyDef.shield !== undefined) { count = D.randi(4, 5); useBandits = true; } // raid
          else count = 3;
        }
      }
      const enemies = [];
      let remaining = enemyPower;
      const regionId = region ? region.id : "qingyun";
      for (let i = 0; i < count; i++) {
        const share = i === count - 1 ? remaining : enemyPower / count * D.rand(0.75, 1.25);
        remaining -= share;
        const p = Math.max(4, share);
        const style = D.pick(D.ENEMY_STYLES);
        const atk = Math.round((2.5 + p * 0.5) * D.rand(0.9, 1.1));
        const hp = Math.round((22 + p * 2.2) * D.rand(0.9, 1.1));
        const namePool = useBandits ? D.BANDITS[regionId] : D.BEASTS[regionId];
        const name = namePool[Math.min(i, namePool.length - 1)] + (i === count - 1 && count > 2 ? "" : "");
        enemies.push({ name, style, atk, hp, interval: 1.0, crit: 0.06, isPlayer: false });
      }
      let shieldA = 0, shieldB = 0, coreB = 0;
      if (isDefense) {
        this.rooms.forEach((r) => { if (r.type === "array" && !r.damaged) shieldA += 90 * r.lvl; });
        this.disciples.forEach((d) => { void d; });
        const talis = this.consumeTalismans();
        return G.Battle.setup({ sideA: player, sideB: enemies, shieldA: shieldA + talis.vajra * 30, shieldB, coreB, talisAtk: talis.fire * 12,
          soulLink: this.techs.unlocked.includes("soul_link"),
          title: kind === "beasts" ? "妖兽袭扰" : (enemyDef ? enemyDef.name + "来袭" : "敌袭"), subtitle: "防御战 · Defense", isDefense: true });
      }
      // attack: shield belongs to defender (B)
      if (kind === "raid") { shieldB = enemyDef.shield; coreB = enemyDef.core; }
      else { shieldB = 0; coreB = 0; }
      const talis = this.consumeTalismans();
      return G.Battle.setup({
        sideA: player, sideB: enemies, shieldA: 0, shieldB, coreB, talisAtk: talis.fire * 12, talisShield: talis.vajra * 30,
        soulLink: this.techs.unlocked.includes("soul_link"),
        title: enemyDef ? enemyDef.name : "战斗", subtitle: kind === "raid" ? "攻打敌宗 · Raid" : "外出历练 · Mission",
      });
    }
    consumeTalismans() {
      let fire = Math.min(3, this.res.talisman_fire);
      let vajra = Math.min(2, this.res.talisman_vajra);
      this.res.talisman_fire -= fire;
      this.res.talisman_vajra -= vajra;
      return { fire, vajra };
    }

    applyBattle(result, ctx) {
      const { winner, fighters, shieldLeftA, shieldLeftB, coreLeftB } = result;
      const win = winner === "A";
      const squad = ctx.squad.map((id) => this.getDisciple(id)).filter(Boolean);
      let mdef = null, region = null;
      for (const rg of D.REGIONS) {
        const m = rg.missions.find((x) => x.id === ctx.targetId) || rg.raids.find((x) => x.id === ctx.targetId);
        if (m) { mdef = m; region = rg; break; }
      }
      const enemyTotalPower = mdef ? (mdef.power || 0) : 50;

      // experience & wounds
      squad.forEach((d) => {
        const f = fighters.find((x) => x.refId === d.id);
        if (f) {
          const frac = f.hp / f.maxHp;
          d.xp += Math.round(enemyTotalPower * 0.22) + (win ? (mdef && mdef.xp ? mdef.xp : 0) : Math.round(enemyTotalPower * 0.08));
          this.checkLevel(d);
          if (frac < 0.3 || !f.alive) d.woundedUntil = Math.max(d.woundedUntil, this.time + 90);
        }
        d.mission = null;
      });

      if (win) {
        if (ctx.kind === "mission" && mdef && mdef.rewards) {
          Object.keys(mdef.rewards).forEach((k) => { if (k === "rep") this.addRep(mdef.rewards[k]); else this.addRes(k, mdef.rewards[k]); });
          this.stats.missionsWon++;
          this.stats.missionsDone++;
        }
        if (ctx.kind === "raid" && mdef && mdef.loot) {
          Object.keys(mdef.loot).forEach((k) => { if (k === "rep") this.addRep(mdef.loot[k]); else this.addRes(k, mdef.loot[k]); });
          this.stats.raidsWon++;
        }
        if (ctx.kind === "defense") { this.stats.defensesWon++; if (mdef) { this.addRep(Math.round(mdef.loot ? mdef.loot.rep * 0.4 : 12)); this.addRes("stone", Math.round((mdef.loot ? mdef.loot.stone : 80) * 0.3)); } }
      } else {
        this.stats.battlesLost++;
        if (ctx.kind === "raid") { const lose = Math.round(this.res.stone * 0.1); this.addRes("stone", -lose); D.onEvent("toast", { text: "战败……损失 " + lose + " 灵石", bad: true }); }
        if (ctx.kind === "defense") this.applyDefenseLoss(ctx);
      }
      this.tryAutoAssign();
      D.onEvent("battledone", { win, ctx, mdef });
    }
    applyDefenseLoss(def) {
      const lose = Math.round(this.res.stone * 0.18);
      this.addRes("stone", -lose);
      const candidates = this.rooms.filter((r) => r.type !== "hall" && !r.damaged);
      let damaged = null;
      if (candidates.length && Math.random() < 0.7) { damaged = D.pick(candidates); damaged.damaged = true; }
      D.onEvent("toast", { text: "山门失守！损失 " + lose + " 灵石" + (damaged ? "，「" + D.ROOMS[damaged.type].name + "」受损" : ""), bad: true });
    }

    /* ── events & raid timer ───────────────── */
    update(dt) {
      this.time += dt;
      this.production(dt);
      // missions completing
      const done = this.missions.active.filter((m) => this.time >= m.until);
      this.missions.active = this.missions.active.filter((m) => this.time < m.until);
      done.forEach((m) => {
        if (m.kind === "mission") this.missions.lastDone[m.targetId] = this.time;
        if (m.kind === "raid") this.raids.last[m.targetId] = this.time;
        const ids = m.squad.filter((id) => { const d = this.getDisciple(id); return d && d.mission && d.mission.targetId === m.targetId; });
        const setup = this.buildBattle(m.kind === "raid" ? "raid" : "mission", m.targetId, ids, false);
        G.Battle.showBattle(setup, (result) => this.applyBattle(result, { kind: m.kind, targetId: m.targetId, squad: ids }));
      });
      // defense warning → battle
      if (this.defense && this.time >= this.defense.until) this.resolveDefense();
      // random event
      if (this.time >= this.events.nextAt && !this.events.pending && !this.defense) {
        this.events.pending = D.pick(D.EVENTS);
        D.onEvent("event", { ev: this.events.pending });
      }
      // raid attack timer
      if (this.time >= this.events.nextRaidAt && !this.defense) {
        this.events.nextRaidAt = this.time + D.rand(300, 460);
        const power = this.sectPower();
        const pool = [];
        D.REGIONS.forEach((rg) => {
          if (D.repLevel(this.rep) >= D.repLevel(rg.rep)) rg.raids.forEach((r) => { if (r.power <= power * 1.25) pool.push({ r, rg }); });
        });
        if (pool.length) {
          const pick = D.pick(pool);
          this.startDefense("raid", pick.r.id);
        } else this.events.nextRaidAt = this.time + 120;
      }
      // recruit refresh
      if (this.time >= this.recruit.nextRefresh && this.recruit.offers.length < 3) this.refreshRecruit(true);
      // task checks
      D.TASKS.forEach((t) => {
        if (this.tasksDone.includes(t.id)) return;
        if (D.taskChecks[t.id] && D.taskChecks[t.id](this)) {
          this.tasksDone.push(t.id);
          Object.keys(t.reward).forEach((k) => { if (k === "rep") this.addRep(t.reward[k]); else this.addRes(k, t.reward[k]); });
          D.onEvent("taskdone", { task: t });
        }
      });
    }

    /* ── save / load ───────────────────────── */
    toJSON() {
      return {
        version: this.version, sectName: this.sectName, time: this.time,
        res: this.res, arts: this.arts, rep: this.rep,
        rooms: this.rooms, disciples: this.disciples,
        rocks: this.rocks, techs: this.techs, tasksDone: this.tasksDone, stats: this.stats,
        missions: this.missions, raids: this.raids, recruit: this.recruit,
        events: this.events, defense: this.defense,
      };
    }
    static load(json) {
      if (!json || json.version !== 1) return null;
      const s = new GameState();
      Object.assign(s, json);
      // safety: ensure fields exist
      s.arts = s.arts || []; s.rocks = s.rocks || []; s.tasksDone = s.tasksDone || [];
      s.stats = Object.assign({ pillsMade: 0, artsMade: 0, talisMade: 0, missionsDone: 0, missionsWon: 0, raidsWon: 0, defensesWon: 0, battlesLost: 0, breakthroughs: 0 }, s.stats);
      s.techs = s.techs || { unlocked: [], researching: null };
      s.missions = s.missions || { active: [], lastDone: {} };
      s.raids = s.raids || { last: {} };
      s.recruit = s.recruit || { offers: [], nextRefresh: 0 };
      s.events = s.events || { nextAt: 100, pending: null, nextRaidAt: 300 };
      if (!s.recruit.offers.length) s.refreshRecruit(true);
      return s;
    }
    save() {
      try { localStorage.setItem(SAVE_KEY, JSON.stringify(this.toJSON())); } catch (e) { /* ignore */ }
    }
    static loadSave() {
      try {
        const raw = localStorage.getItem(SAVE_KEY);
        if (raw) return GameState.load(JSON.parse(raw));
      } catch (e) { /* ignore */ }
      return null;
    }
    static wipe() { try { localStorage.removeItem(SAVE_KEY); } catch (e) { /* ignore */ } }
  }

  D.GameState = GameState;
})();
