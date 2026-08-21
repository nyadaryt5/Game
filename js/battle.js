/* ═══════════════════════════════════════════════════════
   仙门掌门 · Sect Master — BATTLE ENGINE (auto-resolve + viewer)
   Pure simulation (simulate) is DOM-free and node-testable;
   showBattle renders the replay in a fullscreen overlay.
   ═══════════════════════════════════════════════════════ */
(function () {
  "use strict";
  const D = (window.G = window.G || {});

  const STYLE_INFO = {
    aggressive: { label: "杀伐", color: "#d9685e", dmgMult: 1.08 },
    tank: { label: "铁壁", color: "#d9b078", dmgMult: 0.9, takeMult: 0.85 },
    healer: { label: "回春", color: "#86d98f", dmgMult: 1.0, healEvery: 3 },
    control: { label: "控场", color: "#9fe8f0", dmgMult: 1.0, slowChance: 0.3 },
    swift: { label: "疾风", color: "#b8e8c0", dmgMult: 1.0, intervalMult: 0.8 },
    balanced: { label: "均衡", color: "#c9c2ae", dmgMult: 1.0 },
  };

  let uidSeq = 0;
  function makeFighter(cfg, side, idx) {
    const style = STYLE_INFO[cfg.style] || STYLE_INFO.balanced;
    const interval = (cfg.interval || 1.0) * (style.intervalMult || 1);
    return {
      uid: side + (idx + 1), name: cfg.name, side, refId: cfg.refId || null, isPlayer: !!cfg.isPlayer,
      root: cfg.root || null, styleKey: cfg.style, style,
      atk: cfg.atk, hp: cfg.hp, maxHp: cfg.hp, interval, crit: cfg.crit || 0.06,
      alive: true, cd: D.rand(0.2, 1.2), slowUntil: 0, actions: 0,
      x: 0, y: 0,
    };
  }

  /* ── setup ───────────────────────────────── */
  function setup(cfg) {
    const sideA = cfg.sideA.map((c, i) => makeFighter(c, "A", i));
    const sideB = cfg.sideB.map((c, i) => makeFighter(c, "B", i));
    return {
      sideA, sideB,
      shieldA: cfg.shieldA || 0, shieldB: cfg.shieldB || 0,
      coreB: cfg.coreB || 0, coreBMax: cfg.coreB || 0,
      shieldAMax: cfg.shieldA || 0, shieldBMax: cfg.shieldB || 0,
      talisAtk: cfg.talisAtk || 0, talisShield: cfg.talisShield || 0,
      soulLink: !!cfg.soulLink,
      title: cfg.title || "战斗", subtitle: cfg.subtitle || "",
      isDefense: !!cfg.isDefense,
    };
  }

  /* ── pure simulation ─────────────────────── */
  function simulate(battle, opts) {
    opts = opts || {};
    const DT = 0.1, MAXT = opts.maxTime || 180;
    const log = [];
    const fA = battle.sideA, fB = battle.sideB;
    let shieldA = battle.shieldA + (battle.talisShield || 0);
    let shieldB = battle.shieldB;
    let coreB = battle.coreB;
    let t = 0, winner = null;

    const alive = (f) => f.alive;
    const team = (side) => (side === "A" ? fA : fB);
    const enemiesOf = (side) => (side === "A" ? fB : fA);

    const pickTarget = (f) => {
      const foes = enemiesOf(f.side).filter(alive);
      if (!foes.length) return null;
      if (f.styleKey === "control") return foes.slice().sort((a, b) => b.atk - a.atk)[0];
      return foes.slice().sort((a, b) => a.hp / a.maxHp - b.hp / b.maxHp)[0];
    };
    const healTargets = (f) => {
      const mates = team(f.side).filter(alive);
      if (!mates.length) return [];
      mates.sort((a, b) => a.hp / a.maxHp - b.hp / b.maxHp);
      return mates.slice(0, f.styleKey === "healer" && battle.soulLink ? 2 : 1);
    };

    const strike = (f, target, healInstead) => {
      const st = f.style;
      if (healInstead) {
        const mates = healTargets(f);
        mates.forEach((m) => {
          const heal = Math.max(1, Math.round(f.atk * (m.uid === f.uid ? 0.5 : 0.85) * D.rand(0.8, 1.2)));
          m.hp = Math.min(m.maxHp, m.hp + heal);
          log.push({ t, type: "heal", src: f.uid, dst: m.uid, amt: heal });
        });
        return;
      }
      let dmg = f.atk * st.dmgMult * D.rand(0.85, 1.15);
      if (battle.talisAtk) dmg += battle.talisAtk * D.rand(0.7, 1.3);
      let crit = false;
      if (Math.random() < f.crit) { dmg *= 1.6; crit = true; }
      dmg = Math.max(1, Math.round(dmg));
      if (target.style.takeMult) dmg = Math.round(dmg * target.style.takeMult);
      let absorbed = 0;
      if (target.side === "A" && shieldA > 0) {
        absorbed = Math.min(shieldA, dmg); shieldA -= absorbed; dmg -= absorbed;
        if (absorbed > 0) log.push({ t, type: "shieldA", amt: absorbed, left: shieldA });
      } else if (target.side === "B" && shieldB > 0) {
        absorbed = Math.min(shieldB, dmg); shieldB -= absorbed; dmg -= absorbed;
        if (absorbed > 0) log.push({ t, type: "shieldB", amt: absorbed, left: shieldB });
      }
      if (dmg > 0) {
        target.hp -= dmg;
        log.push({ t, type: "atk", src: f.uid, dst: target.uid, dmg, crit, absorbed });
        if (f.styleKey === "control" && Math.random() < (f.style.slowChance || 0)) {
          target.slowUntil = t + 1.6;
          log.push({ t, type: "slow", dst: target.uid });
        }
        if (target.hp <= 0) {
          target.hp = 0; target.alive = false;
          log.push({ t, type: "die", dst: target.uid });
        }
      }
    };

    const sideDead = (side) => !team(side).some(alive);
    const checkEnd = () => {
      if (sideDead("A")) { winner = "B"; return true; }
      if (sideDead("B") && coreB <= 0) { winner = "A"; return true; }
      return false;
    };

    while (t < MAXT && !winner) {
      t += DT;
      const all = fA.concat(fB);
      for (const f of all) {
        if (!f.alive) continue;
        f.cd -= DT * (t < f.slowUntil ? 0.66 : 1);
        if (f.cd > 0) continue;
        const foes = enemiesOf(f.side).filter(alive);
        if (!foes.length) {
          // no fighters on the other side: attackers hammer the core
          if (f.side === "A" && battle.coreB > 0 && coreB > 0) {
            f.cd = f.interval;
            const dmg = Math.max(1, Math.round(f.atk * 1.15 * D.rand(0.9, 1.1)));
            coreB -= dmg;
            log.push({ t, type: "core", src: f.uid, dmg, left: Math.max(0, coreB) });
            if (coreB <= 0) { coreB = 0; winner = "A"; break; }
          }
          continue;
        }
        f.actions++;
        const isHealTurn = f.style.healEvery && f.actions % f.style.healEvery === 0 &&
          team(f.side).some((m) => m.alive && m.hp < m.maxHp);
        const target = pickTarget(f);
        if (!target) continue;
        strike(f, target, !!isHealTurn);
        f.cd = f.interval * D.rand(0.92, 1.08);
      }
      if (checkEnd()) break;
    }
    if (!winner) {
      if (sideDead("B") && coreB > 0) { winner = "B"; log.push({ t, type: "timeout" }); } // defenders held the core
      else {
        const hpFrac = (arr) => arr.reduce((s, f) => s + Math.max(0, f.hp / f.maxHp), 0);
        winner = hpFrac(fA) >= hpFrac(fB) ? "A" : "B";
        log.push({ t, type: "timeout" });
      }
    }

    return {
      winner, log, t,
      fighters: fA.concat(fB),
      shieldLeftA: shieldA, shieldLeftB: shieldB,
      coreLeftB: coreB,
      talisAtk: battle.talisAtk, talisShield: battle.talisShield,
    };
  }

  D.Battle = { setup, simulate, STYLE_INFO };

  /* ═══════════ viewer (browser only) ═══════════ */
  if (typeof document === "undefined") return;

  function showBattle(battle, onDone) {
    const root = document.getElementById("battle-root");
    root.innerHTML = "";
    root.classList.add("open");

    const result = simulate(battle);

    const hud = document.createElement("div");
    hud.id = "battle-hud";
    hud.innerHTML =
      '<div class="team" style="text-align:right;flex:1;padding-right:40px"><span id="bt-nameB">敌方</span><span class="pow" id="bt-powB"></span></div>' +
      '<div class="vs">⚔</div>' +
      '<div class="team" style="text-align:left;flex:1;padding-left:40px"><span id="bt-nameA">' +
      (battle.isDefense ? "我方（守）" : "我方") + '</span><span class="pow" id="bt-powA"></span></div>';
    root.appendChild(hud);

    const canvas = document.createElement("canvas");
    canvas.id = "battle-canvas";
    root.appendChild(canvas);

    const foot = document.createElement("div");
    foot.id = "battle-foot";
    foot.innerHTML =
      '<span style="color:var(--dim);font-size:11px">' + battle.title + " · " + battle.subtitle + "</span>" +
      '<button class="btn small" id="bt-spd1">▶</button>' +
      '<button class="btn small on" id="bt-spd2">▶▶</button>' +
      '<button class="btn small" id="bt-spd4">▶▶▶</button>' +
      '<button class="btn small gold" id="bt-skip">跳过</button>';
    root.appendChild(foot);

    const resDiv = document.createElement("div");
    resDiv.id = "battle-result";
    root.appendChild(resDiv);

    /* layout */
    const fighters = result.fighters;
    const fA = fighters.filter((f) => f.side === "A");
    const fB = fighters.filter((f) => f.side === "B");
    function layout() {
      const W = canvas.width, H = canvas.height;
      const place = (list, xStart, dir) => {
        list.forEach((f, i) => {
          const col = i % 2, row = Math.floor(i / 2);
          const rows = Math.max(1, Math.ceil(list.length / 2));
          const rh = Math.min(120, (H - 160) / rows);
          f.x = xStart + dir * col * 150;
          f.y = 130 + row * rh + rh / 2;
        });
      };
      place(fB, W * 0.28, -1);
      place(fA, W * 0.72, 1);
    }
    function resize() {
      canvas.width = root.clientWidth;
      canvas.height = Math.max(320, root.clientHeight - 120);
      canvas.style.height = canvas.height + "px";
      layout();
    }
    resize();
    window.addEventListener("resize", resize);

    /* replay state */
    const mapF = {};
    fighters.forEach((f) => { mapF[f.uid] = f; });
    const vis = fighters.map((f) => ({
      f, hp: f.maxHp, alive: true, flash: 0, lunge: 0, hurt: 0, healFlash: 0, slow: 0, deathT: -1,
    }));
    const visOf = (uid) => vis.find((v) => v.f.uid === uid);
    const shieldV = { A: result.shieldLeftA, B: result.shieldLeftB };
    const shieldMax = { A: battle.shieldAMax + (battle.talisShield || 0), B: battle.shieldBMax };
    const coreV = { hp: battle.coreB, max: battle.coreBMax };
    let logIdx = 0, simT = 0, speed = 2, done = false, endT = -1;
    let texts = [];
    let slashes = [];
    const shieldFlashT = { A: -1, B: -1 };
    function shieldFlash(side) { shieldFlashT[side] = 0.35; }

    function applyEvent(ev) {
      if (ev.type === "atk") {
        const s = visOf(ev.src), d = visOf(ev.dst);
        if (s && d) {
          s.lunge = 0.18;
          if (ev.absorbed > 0) {
            shieldFlash(s.f.side === "A" ? "A" : "B");
            texts.push({ x: d.f.x, y: d.f.y - 34, txt: "-" + ev.absorbed + " 🛡", color: "#9fe8f0", life: 0.9 });
          }
          if (ev.dmg > 0) {
            d.hp = Math.max(0, d.hp - ev.dmg);
            d.hurt = 0.3;
            texts.push({ x: d.f.x + D.rand(-14, 14), y: d.f.y - 40, txt: (ev.crit ? "暴击 -" : "-") + ev.dmg, color: ev.crit ? "#f0c060" : "#ff8a7a", life: 0.9, big: ev.crit });
            slashes.push({ x1: s.f.x, y1: s.f.y - 16, x2: d.f.x, y2: d.f.y - 16, life: 0.16, color: s.f.isPlayer ? "#ffe9b0" : "#ff9c8a" });
          }
        }
      } else if (ev.type === "heal") {
        const d = visOf(ev.dst);
        if (d) { d.hp = Math.min(d.f.maxHp, d.hp + ev.amt); d.healFlash = 0.5; texts.push({ x: d.f.x, y: d.f.y - 40, txt: "+" + ev.amt, color: "#86d98f", life: 0.9 }); }
      } else if (ev.type === "die") {
        const v = visOf(ev.dst);
        if (v) { v.alive = false; v.deathT = 1.1; }
      } else if (ev.type === "shieldA") { shieldV.A = ev.left; shieldFlash("A"); }
      else if (ev.type === "shieldB") { shieldV.B = ev.left; shieldFlash("B"); }
      else if (ev.type === "core") {
        coreV.hp = ev.left;
        texts.push({ x: canvas.width * 0.28, y: 150, txt: "核心 -" + ev.dmg, color: "#f0c060", life: 0.9, big: true });
      } else if (ev.type === "slow") {
        const v = visOf(ev.dst);
        if (v) { v.slow = 1.6; texts.push({ x: v.f.x, y: v.f.y - 54, txt: "❄ 迟缓", color: "#9fe8f0", life: 0.9 }); }
      }
    }

    const ctx = canvas.getContext("2d");
    let last = performance.now();
    let rafId = null;

    function draw() {
      const W = canvas.width, H = canvas.height;
      ctx.clearRect(0, 0, W, H);
      const sky = ctx.createLinearGradient(0, 0, 0, H);
      sky.addColorStop(0, "#101a26"); sky.addColorStop(0.55, "#1a2430"); sky.addColorStop(1, "#1b241c");
      ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
      ctx.fillStyle = "rgba(240,230,200,.85)";
      ctx.beginPath(); ctx.arc(W * 0.5, H * 0.22, 42, 0, 7); ctx.fill();
      ctx.fillStyle = "rgba(16,26,38,.92)";
      ctx.beginPath(); ctx.arc(W * 0.5 - 14, H * 0.22 - 8, 38, 0, 7); ctx.fill();
      ctx.fillStyle = "rgba(20,32,30,.9)";
      ctx.beginPath(); ctx.moveTo(0, H * 0.6);
      ctx.lineTo(W * 0.18, H * 0.42); ctx.lineTo(W * 0.34, H * 0.6); ctx.lineTo(W * 0.52, H * 0.44);
      ctx.lineTo(W * 0.7, H * 0.62); ctx.lineTo(W * 0.88, H * 0.46); ctx.lineTo(W, H * 0.58); ctx.lineTo(W, H); ctx.lineTo(0, H);
      ctx.fill();
      ctx.fillStyle = "rgba(26,40,34,.95)";
      ctx.fillRect(0, H * 0.6, W, H * 0.4);
      const mistY = H * 0.58 + Math.sin(performance.now() / 3000) * 8;
      ctx.fillStyle = "rgba(150,170,160,.07)";
      ctx.beginPath(); ctx.ellipse(W * 0.25, mistY, W * 0.3, 26, 0, 0, 7); ctx.fill();
      ctx.beginPath(); ctx.ellipse(W * 0.72, mistY + 14, W * 0.32, 30, 0, 0, 7); ctx.fill();
      ctx.strokeStyle = "rgba(216,181,106,.15)"; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(0, H * 0.6); ctx.lineTo(W, H * 0.6); ctx.stroke();

      const drawShield = (side, centerX) => {
        const val = shieldV[side], mx = shieldMax[side];
        if (mx <= 0 || val <= 0) return;
        const flash = shieldFlashT[side] > 0;
        ctx.save();
        ctx.globalAlpha = flash ? 0.75 : 0.38;
        ctx.strokeStyle = side === "A" ? "#6db3e8" : "#d9685e";
        ctx.fillStyle = side === "A" ? "rgba(109,179,232,.10)" : "rgba(217,104,94,.10)";
        ctx.lineWidth = 3;
        ctx.beginPath(); ctx.ellipse(centerX, H * 0.5, 190, 190, 0, 0, 7); ctx.fill(); ctx.stroke();
        ctx.restore();
        const bw = 130;
        ctx.fillStyle = "rgba(0,0,0,.5)"; ctx.fillRect(centerX - bw / 2, H * 0.5 + 178, bw, 8);
        ctx.fillStyle = side === "A" ? "#6db3e8" : "#d9685e";
        ctx.fillRect(centerX - bw / 2, H * 0.5 + 178, bw * D.clamp(val / mx, 0, 1), 8);
        ctx.fillStyle = "#fff"; ctx.font = "9px serif"; ctx.textAlign = "center";
        ctx.fillText("护盾 " + Math.ceil(val), centerX, H * 0.5 + 185);
      };
      drawShield("B", W * 0.28);
      drawShield("A", W * 0.72);

      if (battle.coreB > 0) {
        const cx = W * 0.28, cy = H * 0.5;
        ctx.fillStyle = "rgba(120,40,36,.35)";
        ctx.fillRect(cx - 42, cy - 58, 84, 116);
        ctx.strokeStyle = "#a05048"; ctx.lineWidth = 2; ctx.strokeRect(cx - 42, cy - 58, 84, 116);
        ctx.fillStyle = "#d9685e"; ctx.font = "10px serif"; ctx.textAlign = "center";
        ctx.fillText("敌宗大殿", cx, cy - 66);
        ctx.fillStyle = "rgba(0,0,0,.5)"; ctx.fillRect(cx - 34, cy + 62, 68, 8);
        ctx.fillStyle = "#f0c060"; ctx.fillRect(cx - 34, cy + 62, 68 * D.clamp(coreV.hp / coreV.max, 0, 1), 8);
        ctx.fillStyle = "#fff"; ctx.font = "9px serif";
        ctx.fillText("核心 " + Math.max(0, Math.ceil(coreV.hp)), cx, cy + 78);
        if (coreV.hp <= 0) {
          ctx.fillStyle = "rgba(240,192,96,.8)"; ctx.font = "13px serif";
          ctx.fillText("大殿已破！", cx, cy - 84);
        }
      }

      const drawFighter = (v) => {
        const f = v.f, x = f.x, y = f.y;
        const alpha = v.alive ? 1 : Math.max(0, v.deathT / 1.1);
        ctx.save();
        ctx.globalAlpha = alpha;
        if (!v.alive && v.deathT > 0.5) ctx.globalAlpha *= 0.5;
        ctx.fillStyle = "rgba(0,0,0,.4)";
        ctx.beginPath(); ctx.ellipse(x, y + 26, 15, 4, 0, 0, 7); ctx.fill();
        let lx = 0, ly = 0;
        if (v.lunge > 0) lx = (f.side === "A" ? -1 : 1) * 12 * (v.lunge / 0.18);
        if (v.hurt > 0) ly = -3 * Math.sin(((0.3 - v.hurt) / 0.3) * Math.PI);
        const px = x + lx, py = y + ly;
        if (v.slow > 0) {
          ctx.strokeStyle = "rgba(159,232,240,.6)"; ctx.lineWidth = 2;
          ctx.beginPath(); ctx.arc(px, py - 6, 18, 0, 7); ctx.stroke();
        }
        if (v.healFlash > 0) {
          ctx.fillStyle = "rgba(134,217,143," + (v.healFlash / 0.5) * 0.5 + ")";
          ctx.beginPath(); ctx.arc(px, py - 6, 20, 0, 7); ctx.fill();
        }
        if (v.hurt > 0) {
          ctx.fillStyle = "rgba(255,255,255," + (v.hurt / 0.3) * 0.6 + ")";
          ctx.beginPath(); ctx.arc(px, py - 6, 15, 0, 7); ctx.fill();
        }
        const robe = f.isPlayer ? (D.ROOTS[f.root] ? D.ROOTS[f.root].robe : "#4e9c5f") : "#3a2a2a";
        const robeD = f.isPlayer ? (D.ROOTS[f.root] ? D.ROOTS[f.root].robeD : "#356b41") : "#241a1a";
        ctx.fillStyle = robe;
        ctx.beginPath();
        ctx.moveTo(px - 9, py + 18); ctx.lineTo(px - 12, py - 6); ctx.lineTo(px + 12, py - 6); ctx.lineTo(px + 9, py + 18);
        ctx.closePath(); ctx.fill();
        ctx.fillStyle = robeD; ctx.fillRect(px - 9, py + 2, 18, 4);
        ctx.fillStyle = f.isPlayer ? "#e8c8a8" : "#b89888";
        ctx.beginPath(); ctx.arc(px, py - 12, 8, 0, 7); ctx.fill();
        ctx.fillStyle = f.isPlayer ? "#2a2018" : "#1a1210";
        ctx.beginPath(); ctx.arc(px, py - 14, 8, Math.PI * 0.95, Math.PI * 2.05); ctx.fill();
        ctx.strokeStyle = f.isPlayer ? "#d8b56a" : "#8a6a5a"; ctx.lineWidth = 2;
        ctx.beginPath(); ctx.moveTo(px - 8, py - 13); ctx.lineTo(px + 8, py - 13); ctx.stroke();
        ctx.fillStyle = f.isPlayer ? "#1a1a1a" : "#ff5a4a";
        ctx.fillRect(px - 4, py - 12, 3, 2); ctx.fillRect(px + 2, py - 12, 3, 2);
        ctx.strokeStyle = "#cfd8e0"; ctx.lineWidth = 3;
        ctx.beginPath();
        if (v.lunge > 0) { ctx.moveTo(px + (f.side === "A" ? -14 : 14), py + 6); ctx.lineTo(px + (f.side === "A" ? 2 : -2), py - 16); }
        else { ctx.moveTo(px + (f.side === "A" ? -8 : 8), py + 10); ctx.lineTo(px + (f.side === "A" ? -14 : 14), py - 12); }
        ctx.stroke();
        if (!v.alive && v.deathT > 0.3) {
          ctx.fillStyle = "rgba(200,230,255," + ((1.1 - v.deathT) / 0.8) * 0.8 + ")";
          ctx.beginPath(); ctx.arc(px, py - 20 - (1.1 - v.deathT) * 34, 6, 0, 7); ctx.fill();
        }
        ctx.restore();
        const bw = 44;
        ctx.fillStyle = "rgba(0,0,0,.6)"; ctx.fillRect(x - bw / 2, y + 30, bw, 6);
        ctx.fillStyle = v.f.isPlayer ? "#5fc9a2" : "#d9685e";
        ctx.fillRect(x - bw / 2, y + 30, bw * D.clamp(v.hp / v.f.maxHp, 0, 1), 6);
        ctx.fillStyle = v.f.isPlayer ? "#e6e0cf" : "#e8b0a8";
        ctx.font = "11px serif"; ctx.textAlign = "center";
        ctx.fillText(v.f.name, x, y + 50);
        ctx.fillStyle = v.f.style.color; ctx.font = "9px serif";
        ctx.fillText(v.f.style.label, x, y + 62);
      };
      vis.forEach(drawFighter);

      slashes.forEach((s) => {
        const a = D.clamp(s.life / 0.16, 0, 1);
        ctx.strokeStyle = s.color; ctx.globalAlpha = a; ctx.lineWidth = 2.5;
        ctx.beginPath(); ctx.moveTo(s.x1, s.y1); ctx.lineTo(s.x2, s.y2); ctx.stroke();
        ctx.globalAlpha = 1;
      });
      texts.forEach((tx) => {
        const a = D.clamp(tx.life / 0.9, 0, 1);
        ctx.globalAlpha = a;
        ctx.fillStyle = tx.color;
        ctx.font = (tx.big ? "bold 16px " : "bold 12px ") + "serif";
        ctx.textAlign = "center";
        ctx.fillText(tx.txt, tx.x, tx.y - (0.9 - tx.life) * 34);
        ctx.globalAlpha = 1;
      });
    }

    function endBattle() {
      done = true;
      const win = result.winner === "A";
      resDiv.className = win ? "win" : "lose";
      resDiv.innerHTML = '<div class="big">' + (win ? "大 胜" : "惜 败") + "</div>";
      endT = 2.4;
    }

    function frame(now) {
      const dtReal = Math.min(0.1, (now - last) / 1000);
      last = now;
      if (!done) {
        simT += dtReal * speed;
        while (logIdx < result.log.length && result.log[logIdx].t <= simT) {
          applyEvent(result.log[logIdx]);
          logIdx++;
        }
        if (logIdx >= result.log.length && simT >= result.t) endBattle();
      } else if (endT > 0) {
        endT -= dtReal;
        if (endT <= 0) {
          cancelAnimationFrame(rafId);
          window.removeEventListener("resize", resize);
          root.classList.remove("open");
          root.innerHTML = "";
          onDone(result);
          return;
        }
      }
      vis.forEach((v) => {
        v.lunge = Math.max(0, v.lunge - dtReal);
        v.hurt = Math.max(0, v.hurt - dtReal);
        v.healFlash = Math.max(0, v.healFlash - dtReal);
        v.slow = Math.max(0, v.slow - dtReal);
        if (!v.alive) v.deathT = Math.max(0, v.deathT - dtReal);
      });
      texts.forEach((tx) => { tx.life -= dtReal; });
      texts = texts.filter((tx) => tx.life > 0);
      slashes.forEach((s) => { s.life -= dtReal; });
      slashes = slashes.filter((s) => s.life > 0);
      shieldFlashT.A = Math.max(-1, shieldFlashT.A - dtReal);
      shieldFlashT.B = Math.max(-1, shieldFlashT.B - dtReal);
      draw();
      rafId = requestAnimationFrame(frame);
    }

    function setSpeed(s) {
      speed = s;
      ["bt-spd1", "bt-spd2", "bt-spd4"].forEach((id, i) => {
        const el = document.getElementById(id);
        if (el) el.classList.toggle("on", [1, 2, 4][i] === s);
      });
    }
    document.getElementById("bt-spd1").onclick = () => setSpeed(1);
    document.getElementById("bt-spd2").onclick = () => setSpeed(2);
    document.getElementById("bt-spd4").onclick = () => setSpeed(4);
    document.getElementById("bt-skip").onclick = () => { if (!done) speed = 999; };

    rafId = requestAnimationFrame(frame);
  }

  D.Battle.showBattle = showBattle;
})();
