/* ═══════════════════════════════════════════════════════
   仙门掌门 · Sect Master — MAIN LOOP & INPUT
   ═══════════════════════════════════════════════════════ */
(function () {
  "use strict";
  const D = (window.G = window.G || {});
  const U = D.UI;

  let state = null;
  G.state = null;
  G.buildTool = null;
  G.selectedRoomId = null;
  G.panelKind = "build";
  G.panelArg = null;
  G.battleOpen = false;

  const mouse = { x: 0, y: 0, down: false, moved: false, sx: 0, sy: 0, dragging: false, btn: 0 };
  const ui = { hover: null, buildGhost: null, selectedRoomId: null };

  /* ── boot ────────────────────────────────── */
  function boot() {
    const saved = D.GameState.loadSave();
    if (saved) state = saved;
    else { state = new D.GameState(); state.newGame(); }
    G.state = state;
    D.Render.init(document.getElementById("game-canvas"));

    const spd = (n) => { state.speed = n; U.updateTopbar(); state.save(); };
    document.querySelectorAll(".spd-btn").forEach((b) => {
      b.onclick = () => spd(+b.dataset.spd);
    });
    document.querySelectorAll(".tab-btn").forEach((b) => {
      b.onclick = () => U.setTab(b.dataset.tab);
    });
    $("#sect-name").onclick = editSectName;

    wireEvents();
    U.setTab("build");
    U.updateTasks();

    // sound toggle (persisted)
    D.Sound.updateBtn();
    const soundBtn = document.getElementById("sound-btn");
    if (soundBtn) soundBtn.onclick = () => { D.Sound.toggle(); D.Sound.updateBtn(); };

    if (saved) {
      // resume: pending warnings
      if (state.events.pending) U.showEvent(state.events.pending);
      if (state.defense && state.time < state.defense.until) U.showDefenseWarning(state.defense);
      $("#intro").style.display = "none";
      toastQuiet("存档已载入：第 " + (Math.floor(state.time / D.DAY_SECS) + 1) + " 天");
      // offline earnings
      const elapsed = D.GameState.elapsedSinceSeen();
      if (elapsed >= 60) {
        const off = state.simulateOffline(elapsed);
        if (off) {
          const g = off.gains;
          const parts = [];
          if (g.stone > 0) parts.push("💠 +" + D.fmt(g.stone));
          if (g.herb > 0) parts.push("🌿 +" + D.fmt(g.herb));
          if (g.ore > 0) parts.push("⛏️ +" + D.fmt(g.ore));
          if (g.pill > 0) parts.push("💊 +" + g.pill);
          if (g.talis > 0) parts.push("🧿 +" + g.talis);
          if (g.art > 0) parts.push("⚔️ +" + g.art);
          if (parts.length) {
            U.toast("🌙 离线 " + D.fmtTime(off.seconds) + " 收成：" + parts.join("  "), "good");
          }
        }
      }
      D.GameState.touchSeen();
    }

    D.Render.resize();
    D.Render.fitView();
    window.addEventListener("resize", () => { D.Render.resize(); });
    setInterval(() => { state.save(); D.GameState.touchSeen(); }, 10000);
    window.addEventListener("beforeunload", () => { state.save(); D.GameState.touchSeen(); });
    document.addEventListener("visibilitychange", () => { if (document.hidden) { state.save(); D.GameState.touchSeen(); } });
    requestAnimationFrame(loop);
  }

  function $(s) { return document.querySelector(s); }

  /* ── event wiring ────────────────────────── */
  const lastToast = {};
  function wireEvents() {
    D.onEvent = (ev, data) => {
      switch (ev) {
        case "toast": U.toast(data.text, data.bad ? "bad" : "good"); break;
        case "cap": {
          const now = state.time;
          if (!lastToast[data.key] || now - lastToast[data.key] > 30) {
            lastToast[data.key] = now;
            U.toast("仓储已满，部分产出停滞（可建造聚宝阁扩容）", "bad");
          }
          break;
        }
        case "replevel": U.toast("🌟 宗门声望提升至 Lv." + data.lvl + "！", "good"); break;
        case "roomplaced": {
          D.Render.spawnText(roomCX(data.room), roomCY(data.room), "落成", "#f0c060");
          D.Sound.play("build");
          U.updateTopbar();
          break;
        }
        case "roomupgraded": {
          D.Render.spawnText(roomCX(data.room), roomCY(data.room), "升级 Lv." + data.room.lvl, "#f0c060", true);
          D.Sound.play("upgrade");
          U.refreshPanel(); U.updateTopbar();
          break;
        }
        case "roomdemolished": U.toast("殿阁已拆除"); U.updateTopbar(); break;
        case "recruit": D.Sound.play("recruit"); U.toast("🧑‍🌾 「" + data.d.name + "」拜入宗门"); U.updateTopbar(); break;
        case "levelup": {
          const p = disciplePos(data.d);
          D.Render.spawnText(p.x, p.y, "修为精进", "#5fc9a2");
          D.Sound.play("levelup");
          if (data.step % 3 === 0) U.toast("🌟 " + data.d.name + " 晋升" + D.STEPS[data.step].cn);
          break;
        }
        case "breakthrough": {
          const p = disciplePos(data.d);
          D.Render.spawnText(p.x, p.y, "突破！" + D.STEPS[data.step].cn, "#f0c060", true);
          for (let i = 0; i < 14; i++) D.Render.spawnPart(p.x + D.rand(-20, 20), p.y + D.rand(-20, 10), i % 2 ? "gold" : "spark");
          D.Render.addShake(0.5);
          D.Sound.play("breakthrough");
          U.toast("⚡ " + data.d.name + " 突破成功，晋升" + D.STEPS[data.step].cn + "！", "good");
          break;
        }
        case "breakfail": {
          const p = disciplePos(data.d);
          D.Render.spawnText(p.x, p.y, "走火入魔", "#d9685e", true);
          D.Sound.play("fail");
          U.toast("😰 " + data.d.name + " 突破失败，修为受损……", "bad");
          break;
        }
        case "pilluse": U.toast(data.d.name + " 服用丹药，修为大涨", "good"); break;
        case "craft": {
          const r = data.room;
          D.Sound.play("craft");
          if (data.kind === "art") {
            const a = state.arts[state.arts.length - 1];
            D.Render.spawnText(roomCX(r), roomCY(r), D.TIERS[a.tier].name + "！", D.TIERS[a.tier].color, true);
            U.toast("⚔️ 炼成" + D.TIERS[a.tier].name + (D.BLUEPRINTS.find((b) => b.key === a.bp) || {}).name, "good");
          } else {
            D.Render.spawnPart(roomCX(r) + D.rand(-10, 10), roomCY(r) - 10, data.kind === "pill" ? "smoke" : "gold");
          }
          break;
        }
        case "techdone": {
          const t = D.TECHS.find((x) => x.key === data.key);
          D.Sound.play("upgrade");
          U.toast("📜 研究完成：「" + t.name + "」" + t.desc, "good");
          U.refreshPanel();
          break;
        }
        case "missionsent": U.refreshPanel(); break;
        case "raidsent": U.refreshPanel(); break;
        case "taskdone": {
          D.Sound.play("victory");
          U.toast("🎯 完成掌门试炼：「" + data.task.title + "」", "good");
          U.updateTasks(); U.updateTopbar();
          break;
        }
        case "event": U.showEvent(data.ev); break;
        case "defensewarning": D.Sound.play("battle"); U.showDefenseWarning(state.defense); break;
        case "battledone": {
          U.updateTopbar(); U.updateTasks(); U.refreshPanel();
          if (data.win) {
            D.Sound.play("victory");
            U.toast("🏆 " + (data.mdef ? data.mdef.name : "战斗") + " 大捷！", "good");
          } else {
            D.Sound.play("defeat");
          }
          break;
        }
      }
    };
  }

  function roomCX(r) { return (r.x + D.ROOMS[r.type].size[0] / 2) * D.Render.TILE; }
  function roomCY(r) { return (r.y + D.ROOMS[r.type].size[1] / 2) * D.Render.TILE; }
  G.camFxX = (d) => disciplePos(d).x;
  G.camFxY = (d) => disciplePos(d).y;

  function disciplePos(d) {
    if (d.workRoom) {
      const r = state.rooms.find((x) => x.id === d.workRoom);
      if (r) {
        const def = D.ROOMS[r.type];
        return { x: (r.x + def.size[0] / 2) * D.Render.TILE, y: (r.y + def.size[1] - 0.5) * D.Render.TILE };
      }
    }
    return { x: D.GRID.w * D.Render.TILE / 2, y: D.GRID.h * D.Render.TILE / 2 };
  }

  /* ── sect name ───────────────────────────── */
  function editSectName() {
    U.openModal('<h3>修改宗门名号<span class="en">SECT NAME</span></h3>' +
      '<div class="m-desc" style="text-align:center"><input type="text" id="name-input" maxlength="8" value="' + state.sectName + '" style="width:70%;text-align:center"></div>' +
      '<div class="m-foot"><button class="btn primary" id="name-ok">确定</button><button class="btn" data-close>取消</button></div>');
    const inp = $("#name-input");
    inp.focus(); inp.select();
    $("#name-ok").onclick = () => {
      const v = inp.value.trim();
      if (v) { state.sectName = v; U.updateTopbar(); }
      U.closeTopModal();
    };
    inp.onkeydown = (e) => { if (e.key === "Enter") $("#name-ok").click(); };
  }

  /* ── input ───────────────────────────────── */
  const canvas = document.getElementById("game-canvas");
  function bindInput() {
    canvas.addEventListener("contextmenu", (e) => e.preventDefault());
    canvas.addEventListener("mousedown", (e) => {
      mouse.down = true; mouse.btn = e.button;
      mouse.sx = e.clientX; mouse.sy = e.clientY;
      mouse.moved = false; mouse.dragging = false;
    });
    window.addEventListener("mousemove", (e) => {
      mouse.x = e.clientX; mouse.y = e.clientY;
      if (mouse.down) {
        const dx = e.clientX - mouse.sx, dy = e.clientY - mouse.sy;
        if (Math.abs(dx) + Math.abs(dy) > 4) mouse.moved = true;
        if (mouse.moved && (mouse.btn === 0 && !G.buildTool || mouse.btn === 2 || mouse.btn === 1)) {
          mouse.dragging = true;
          D.Render.cam.x += dx; D.Render.cam.y += dy;
          mouse.sx = e.clientX; mouse.sy = e.clientY;
        }
      }
    });
    window.addEventListener("mouseup", (e) => {
      const wasClick = !mouse.moved;
      mouse.down = false;
      if (e.button === 2) { if (G.buildTool) { G.buildTool = null; U.toast("已取消"); } return; }
      if (!wasClick) return;
      const rect = canvas.getBoundingClientRect();
      const sx = e.clientX - rect.left, sy = e.clientY - rect.top;
      handleClick(sx, sy);
    });
    canvas.addEventListener("wheel", (e) => {
      e.preventDefault();
      const rect = canvas.getBoundingClientRect();
      const sx = e.clientX - rect.left, sy = e.clientY - rect.top;
      const factor = Math.pow(1.0015, -e.deltaY);
      const nz = D.clamp(D.Render.cam.zoom * factor, 0.4, 2.4);
      const wx = (sx - D.Render.cam.x) / D.Render.cam.zoom;
      const wy = (sy - D.Render.cam.y) / D.Render.cam.zoom;
      D.Render.cam.zoom = nz;
      D.Render.cam.x = sx - wx * nz;
      D.Render.cam.y = sy - wy * nz;
    }, { passive: false });
    window.addEventListener("keydown", (e) => {
      if (e.target.tagName === "INPUT" || e.target.tagName === "SELECT") return;
      if (e.key === "Escape") { G.buildTool = null; G.selectedRoomId = null; U.showPanel("build"); }
      if (e.key === " ") { e.preventDefault(); state.speed = state.speed === 0 ? 1 : 0; U.updateTopbar(); }
      if (e.key === "1") state.speed = 0, U.updateTopbar();
      if (e.key === "2") state.speed = 1, U.updateTopbar();
      if (e.key === "3") state.speed = 2, U.updateTopbar();
      if (e.key === "4") state.speed = 4, U.updateTopbar();
    });

    // unlock audio on first gesture (autoplay policy)
    const unlockAudio = () => { D.Sound.init(); };
    window.addEventListener("pointerdown", unlockAudio, { once: true });
    window.addEventListener("keydown", unlockAudio, { once: true });

    // soft click blip for any button interaction
    document.addEventListener("click", (e) => {
      if (e.target.closest("button, .tab-btn, .spd-btn")) D.Sound.play("click");
    }, true);

    // ── touch input (Android / mobile): drag=pan, pinch=zoom, tap=click ──
    let tStart = null, tLast = null, pinch0 = 0, tMoved = false;
    const tPos = (t) => {
      const rect = canvas.getBoundingClientRect();
      return { x: t.clientX - rect.left, y: t.clientY - rect.top };
    };
    canvas.addEventListener("touchstart", (e) => {
      D.Sound.init();
      if (e.touches.length === 1) {
        tStart = tPos(e.touches[0]); tLast = tStart; tMoved = false;
      } else if (e.touches.length === 2) {
        const a = tPos(e.touches[0]), b = tPos(e.touches[1]);
        pinch0 = Math.hypot(a.x - b.x, a.y - b.y);
        tStart = null;
      }
      e.preventDefault();
    }, { passive: false });
    canvas.addEventListener("touchmove", (e) => {
      if (e.touches.length === 1 && tStart) {
        const p = tPos(e.touches[0]);
        const dx = p.x - tLast.x, dy = p.y - tLast.y;
        if (Math.abs(dx) + Math.abs(dy) > 4) tMoved = true;
        D.Render.cam.x += dx; D.Render.cam.y += dy;
        tLast = p;
      } else if (e.touches.length === 2) {
        const a = tPos(e.touches[0]), b = tPos(e.touches[1]);
        const d = Math.hypot(a.x - b.x, a.y - b.y);
        if (pinch0 > 0) {
          const cx = (a.x + b.x) / 2, cy = (a.y + b.y) / 2;
          const nz = D.clamp(D.Render.cam.zoom * (d / pinch0), 0.4, 2.4);
          const wx = (cx - D.Render.cam.x) / D.Render.cam.zoom;
          const wy = (cy - D.Render.cam.y) / D.Render.cam.zoom;
          D.Render.cam.zoom = nz;
          D.Render.cam.x = cx - wx * nz;
          D.Render.cam.y = cy - wy * nz;
        }
        pinch0 = d;
      }
      e.preventDefault();
    }, { passive: false });
    canvas.addEventListener("touchend", (e) => {
      if (tStart && !tMoved && e.changedTouches.length === 1) {
        handleClick(tStart.x, tStart.y);
      }
      tStart = null; pinch0 = 0;
    }, { passive: false });
  }

  function handleClick(sx, sy) {
    if (G.buildTool && G.buildTool.mode === "build") {
      const t = D.Render.tileAt(sx, sy);
      const def = D.ROOMS[G.buildTool.type];
      const gx = D.clamp(t.tx - Math.floor(def.size[0] / 2), 0, D.GRID.w - def.size[0]);
      const gy = D.clamp(t.ty - Math.floor(def.size[1] / 2), 0, D.GRID.h - def.size[1]);
      const res = state.placeRoom(G.buildTool.type, gx, gy);
      if (res === "ok") { U.updateTopbar(); U.refreshPanel(); }
      else if (res === "cost") U.toast("灵石不足", "bad");
      else if (res === "rep") U.toast("声望不足", "bad");
      else if (res === "maxrooms") U.toast("殿阁数量已达上限，请升级大殿", "bad");
      return;
    }
    if (G.buildTool && G.buildTool.mode === "demolish") {
      const t = D.Render.tileAt(sx, sy);
      const room = state.roomAt(t.tx, t.ty);
      if (room && room.type !== "hall") {
        U.openModal('<h3>拆除「' + D.ROOMS[room.type].name + '」</h3><p class="m-desc">返还 40% 建造费用。弟子将被遣回待命。</p>' +
          '<div class="m-foot"><button class="btn danger" id="m-ok">确认拆除</button><button class="btn" data-close>取消</button></div>');
        $("#m-ok").onclick = () => { state.demolishRoom(room); U.closeTopModal(); U.updateTopbar(); };
      }
      return;
    }
    const t = D.Render.tileAt(sx, sy);
    const room = state.roomAt(t.tx, t.ty);
    if (room) {
      G.selectedRoomId = room.id;
      ui.selectedRoomId = room.id;
      G.panelKind = "room"; G.panelArg = room;
      U.renderRoomPanel(room);
    } else {
      G.selectedRoomId = null;
      ui.selectedRoomId = null;
      U.showPanel("build");
    }
  }

  /* ── hover tooltip ───────────────────────── */
  let lastTipKey = "";
  function updateHover() {
    const rect = canvas.getBoundingClientRect();
    const sx = mouse.x - rect.left, sy = mouse.y - rect.top;
    if (sx < 0 || sy < 0 || sx > rect.width || sy > rect.height) { U.hideTooltip(); lastTipKey = ""; return; }
    const t = D.Render.tileAt(sx, sy);
    ui.hover = t;
    const room = state.roomAt(t.tx, t.ty);
    if (room) {
      const def = D.ROOMS[room.type];
      const fx = D.roomEffects[room.type] ? D.roomEffects[room.type](room.lvl) : {};
      let eff = "";
      if (room.type === "med") eff = "位置 " + fx.slots + " · " + fx.xpRate.toFixed(1) + " 修为/秒";
      if (room.type === "garden") eff = fx.herbRate.toFixed(2) + " 灵草/秒";
      if (room.type === "mine") eff = fx.stoneRate.toFixed(2) + " 灵石/秒";
      if (room.type === "train") eff = fx.xpRate.toFixed(2) + " 修为/秒 · 攻击 +" + Math.round(fx.squadAtk * 100) + "%";
      if (room.type === "array") eff = "护盾 " + fx.shield;
      if (room.type === "hall") eff = "灵气 +" + fx.energy;
      if (room.type === "dorm") eff = "弟子上限 +" + fx.disciples;
      if (room.type === "store") eff = "储量 +" + Math.round(fx.capMult * 100) + "%";
      if (room.type === "lib") eff = "研究速度 +" + Math.round(fx.speedBonus * 100) + "%";
      const tipKey = "r" + room.id + room.lvl + room.damaged;
      if (tipKey !== lastTipKey) {
        lastTipKey = tipKey;
        U.showTooltip("<b>" + def.icon + " " + def.name + "</b> <span class='tt-sub'>Lv." + room.lvl + " · " + def.en + "</span><br>" + (eff || def.desc) +
          (room.damaged ? "<br><span style='color:#d9685e'>受损：产能减半，点击修复</span>" : "") +
          "<br><span class='tt-sub'>点击管理</span>", mouse.x, mouse.y);
      }
      return;
    }
    // disciple hover
    const d = discipleNear(t);
    if (d) {
      const stt = state.discipleStats(d);
      const tipKey = "d" + d.id + d.step + stt.power;
      if (tipKey !== lastTipKey) {
        lastTipKey = tipKey;
        U.showTooltip("<b>" + d.name + "</b> <span class='tt-sub'>" + D.ROOTS[d.root].name + "</span><br>" +
          D.STEPS[d.step].cn + " · 战力 " + stt.power + "<br>攻 " + stt.atk + " · 血 " + stt.hp, mouse.x, mouse.y);
      }
      return;
    }
    if (lastTipKey !== "") { U.hideTooltip(); lastTipKey = ""; }
  }
  function discipleNear(t) {
    const wx = t.tx * D.Render.TILE, wy = t.ty * D.Render.TILE;
    for (const d of state.disciples) {
      if (state.busy(d) || !d.workRoom) continue;
      const p = disciplePos(d);
      if (Math.abs(p.x - wx) < 40 && Math.abs(p.y - wy) < 40) return d;
    }
    return null;
  }

  /* ── build ghost update ──────────────────── */
  function updateGhost() {
    if (!G.buildTool || G.buildTool.mode !== "build") { ui.buildGhost = null; return; }
    const rect = canvas.getBoundingClientRect();
    const sx = mouse.x - rect.left, sy = mouse.y - rect.top;
    const t = D.Render.tileAt(sx, sy);
    const def = D.ROOMS[G.buildTool.type];
    const gx = D.clamp(t.tx - Math.floor(def.size[0] / 2), 0, D.GRID.w - def.size[0]);
    const gy = D.clamp(t.ty - Math.floor(def.size[1] / 2), 0, D.GRID.h - def.size[1]);
    ui.buildGhost = { type: G.buildTool.type, x: gx, y: gy };
  }

  /* ── main loop ───────────────────────────── */
  let last = performance.now();
  let topbarTimer = 0;
  function loop(now) {
    const dtReal = Math.min(0.1, (now - last) / 1000);
    last = now;
    const battleOpen = document.getElementById("battle-root").classList.contains("open");
    G.battleOpen = battleOpen;

    if (!battleOpen) {
      const gameDt = dtReal * state.speed;
      if (gameDt > 0) state.update(gameDt);
      D.Render.updateWander(state, dtReal);
      D.Render.updateFx(dtReal);
      updateGhost();
      updateHover();
      topbarTimer += dtReal;
      if (topbarTimer > 0.25) { U.updateTopbar(); topbarTimer = 0; }
    }

    ui.selectedRoomId = G.selectedRoomId;
    D.Render.draw(state, ui, now);
    requestAnimationFrame(loop);
  }

  function toastQuiet(t) { U.toast(t); }

  /* ── intro ───────────────────────────────── */
  const intro = document.getElementById("intro");
  if (intro) {
    const showIntro = !D.GameState.loadSave();
    if (!showIntro) intro.style.display = "none";
    else state.speed = 0; // hold time while the intro is shown
    document.getElementById("intro-btn").onclick = () => {
      intro.style.display = "none";
      state.speed = 1;
      state.save();
      U.updateTopbar();
      U.toast("掌门，宗门百废待兴。先从建造练功房开始吧！", "good");
    };
  }

  bindInput();
  boot();
})();
