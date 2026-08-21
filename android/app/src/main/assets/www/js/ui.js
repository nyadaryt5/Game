/* ═══════════════════════════════════════════════════════
   仙门掌门 · Sect Master — UI LAYER
   ═══════════════════════════════════════════════════════ */
(function () {
  "use strict";
  const D = (window.G = window.G || {});

  const RES_ICON = {
    stone: "💠", herb: "🌿", ore: "⛏️",
    pill_qi: "💊", pill_pei: "💊", pill_zhu: "💮", pill_jin: "💮", pill_ying: "💮", pill_shen: "💮", pill_du: "💮",
    talisman_fire: "🔥", talisman_vajra: "🛡️", rep: "🌟",
  };
  const RES_NAME = {
    stone: "灵石", herb: "灵草", ore: "矿石",
    pill_qi: "聚气丹", pill_pei: "培元丹", pill_zhu: "筑基丹", pill_jin: "结金丹", pill_ying: "化婴丹", pill_shen: "化神丹", pill_du: "渡劫丹",
    talisman_fire: "火球符", talisman_vajra: "金刚符", rep: "声望",
  };

  function $(sel) { return document.querySelector(sel); }
  function el(html) { const t = document.createElement("template"); t.innerHTML = html.trim(); return t.content.firstChild; }
  function S() { return G.state; }

  function costHtml(cost, big) {
    return Object.keys(cost).map((k) => {
      const ok = (S().res[k] || 0) >= cost[k];
      return '<span class="cost ' + (ok ? "ok" : "no") + '">' + (RES_ICON[k] || "·") + (big ? " <b>" + D.fmt(cost[k]) + "</b>" : " " + D.fmt(cost[k])) + "</span>";
    }).join(" ");
  }

  /* ── toast ───────────────────────────────── */
  function toast(text, cls) {
    const box = $("#toasts");
    const t = el('<div class="toast ' + (cls || "") + '">' + text + "</div>");
    box.appendChild(t);
    setTimeout(() => { t.classList.add("out"); setTimeout(() => t.remove(), 450); }, 3200);
    while (box.children.length > 5) box.firstChild.remove();
  }

  /* ── modal ───────────────────────────────── */
  function openModal(html, opts) {
    const root = $("#modal-root");
    const wrap = el('<div class="modal-backdrop"></div>');
    const m = el('<div class="modal">' + html + "</div>");
    const closer = () => { root.classList.remove("open"); root.innerHTML = ""; };
    wrap.onclick = (e) => { if (e.target === wrap && !(opts && opts.sticky)) closer(); };
    root.innerHTML = "";
    root.appendChild(wrap);
    root.appendChild(m);
    root.classList.add("open");
    m.querySelectorAll("[data-close]").forEach((b) => { b.onclick = closer; });
    return { el: m, close: closer };
  }

  /* ── side panel ──────────────────────────── */
  let currentTab = "build";
  function setTab(tab) {
    currentTab = tab;
    document.querySelectorAll(".tab-btn").forEach((b) => b.classList.toggle("on", b.dataset.tab === tab));
    if (tab === "build") { G.buildTool = { mode: "build", type: null, roomId: null }; }
    if (tab === "disc") G.buildTool = null;
    if (tab === "quest") G.buildTool = null;
    if (tab === "lib") G.buildTool = null;
    if (tab === "store") G.buildTool = null;
    if (tab === "tasks") G.buildTool = null;
    G.selectedRoomId = null;
    showPanel(tab);
  }
  function showPanel(kind, arg) {
    const sp = $("#sidepanel");
    if (kind === "room" && arg) { renderRoomPanel(arg); return; }
    if (kind === "discdetail" && arg) { renderDiscDetail(arg); return; }
    switch (kind) {
      case "build": renderBuild(); break;
      case "disc": renderDisc(); break;
      case "quest": renderQuest(); break;
      case "lib": renderLib(); break;
      case "store": renderStore(); break;
      case "tasks": renderTasks(); break;
    }
  }
  function refreshPanel() {
    showPanel(G.panelKind || currentTab, G.panelArg);
  }
  function head(title, sub, extra) {
    return '<div class="sp-head">' + title + (sub ? ' <span class="sub">' + sub + "</span>" : "") + (extra || "") + "</div>";
  }

  /* ── BUILD ───────────────────────────────── */
  function renderBuild() {
    const st = S();
    const sp = $("#sidepanel");
    const repLvl = D.repLevel(st.rep);
    let items = "";
    const order = ["med", "garden", "mine", "train", "alch", "dorm", "store", "lib", "forge", "talis", "array"];
    order.forEach((key) => {
      const def = D.ROOMS[key];
      const owned = st.rooms.filter((r) => r.type === key);
      const locked = def.unlockRep && repLvl < def.unlockRep;
      const c0 = def.cost.stone[0];
      const afford = st.res.stone >= c0;
      const maxed = owned.length > 0 && owned.every((r) => r.lvl >= def.maxLvl);
      items +=
        '<div class="build-item' + (locked ? " locked" : "") + '" data-build="' + key + '">' +
        '<div class="b-icon">' + def.icon + "</div>" +
        '<div class="b-info">' +
        '<div class="b-name">' + def.name + '<span class="en">' + def.en + "</span>" + (owned.length ? '<span class="owned-badge">×' + owned.length + "</span>" : "") + "</div>" +
        '<div class="b-desc">' + def.desc + "</div>" +
        '<div class="b-foot">' + def.size[0] + "×" + def.size[1] + " · 💠" + D.fmt(c0) + " · " + D.fmtTime(def.time[0]) +
        (locked ? ' · <span class="red">需声望 Lv.' + def.unlockRep + "</span>" : "") +
        (maxed ? ' · <span class="jade">已满级</span>' : "") + "</div>" +
        "</div></div>";
    });
    const energy = st.energyInfo();
    sp.innerHTML =
      head("建筑 · Build", "共 " + st.rooms.length + "/" + st.maxRooms() + " 座", "") +
      '<div class="sp-body">' +
      '<div class="card" style="font-size:11px;color:var(--dim);line-height:1.7">' +
      '灵气 <b class="' + (energy.demand > energy.supply ? "red" : "jade") + '">' + energy.supply + "/" + energy.demand + "</b>" +
      (energy.factor < 1 ? ' <span class="red">（灵气不足，产能 55%）</span>' : "") +
      " · 升殿可增灵气<br>声望 Lv." + repLvl + " · 拆除返还 40% 灵石" +
      "</div>" +
      items +
      '<div class="card" style="text-align:center"><button class="btn danger" id="btn-demolish">🔨 拆除模式</button></div>' +
      "</div>";
    sp.querySelectorAll("[data-build]").forEach((item) => {
      item.onclick = () => {
        const key = item.dataset.build;
        const def = D.ROOMS[key];
        const locked = def.unlockRep && repLvl < def.unlockRep;
        if (locked) { toast("声望不足，无法建造「" + def.name + "」", "bad"); return; }
        G.buildTool = { mode: "build", type: key };
        G.panelKind = "build";
        toast("点击山门空地放置「" + def.name + "」 · 右键/Esc 取消");
      };
    });
    const dem = sp.querySelector("#btn-demolish");
    if (dem) dem.onclick = () => {
      G.buildTool = { mode: "demolish", type: null };
      toast("点击建筑进行拆除（大殿不可拆）");
    };
  }

  function renderRoomPanel(room) {
    const st = S();
    const def = D.ROOMS[room.type];
    const sp = $("#sidepanel");
    const up = st.upgradeCost(room);
    let assignedHtml = "";
    room.assigned.forEach((id) => {
      const d = st.getDisciple(id);
      if (!d) return;
      assignedHtml += '<div class="row" style="justify-content:space-between">' +
        '<span class="disc-chip" style="cursor:pointer" data-disc="' + d.id + '"><span class="avatar" style="width:20px;height:20px;font-size:10px;background:' + D.ROOTS[d.root].robe + '">' + (d.gender === "f" ? "女" : "男") + "</span> " + d.name + "</span>" +
        '<button class="btn small" data-unassign="' + d.id + '">移出</button></div>';
    });
    const slots = st.roomSlots(room) || 0;
    let workers = room.assigned.map((id) => st.getDisciple(id)).filter((d) => d && !st.busy(d));
    let idleD = st.disciples.filter((d) => !d.workRoom && !st.busy(d) && !room.assigned.includes(d.id));

    let effectTxt = "";
    const fx = D.roomEffects[room.type] ? D.roomEffects[room.type](room.lvl) : {};
    if (room.type === "hall") effectTxt = "灵气 +" + fx.energy + " · 建筑上限 " + fx.rooms + " · 弟子上限 +" + fx.disciples;
    if (room.type === "med") effectTxt = "位置 " + fx.slots + " · 每名弟子 " + fx.xpRate.toFixed(1) + " 修为/秒";
    if (room.type === "garden") effectTxt = "位置 " + fx.slots + " · 产出 " + fx.herbRate.toFixed(2) + " 灵草/秒";
    if (room.type === "mine") effectTxt = "位置 " + fx.slots + " · 产出 " + fx.stoneRate.toFixed(2) + " 灵石/秒";
    if (room.type === "train") effectTxt = "位置 " + fx.slots + " · " + fx.xpRate.toFixed(2) + " 修为/秒 · 全宗攻击 +" + Math.round(fx.squadAtk * 100) + "%";
    if (room.type === "dorm") effectTxt = "弟子上限 +" + fx.disciples;
    if (room.type === "store") effectTxt = "资源储量 +" + Math.round(fx.capMult * 100) + "%";
    if (room.type === "lib") effectTxt = "研究速度 +" + Math.round(fx.speedBonus * 100) + "%";
    if (room.type === "array") effectTxt = "护盾 " + fx.shield + " · 全宗防御 +" + Math.round(fx.squadDef * 100) + "%";
    if (room.type === "alch") effectTxt = "位置 " + fx.slots + " · 炼丹速度 +" + Math.round(fx.speedBonus * 100) + "%";
    if (room.type === "forge") effectTxt = "位置 " + fx.slots + " · 炼器速度 +" + Math.round(fx.speedBonus * 100) + "% · 品质 +" + fx.quality;
    if (room.type === "talis") effectTxt = "位置 " + fx.slots + " · 制符速度 +" + Math.round(fx.speedBonus * 100) + "%";

    let recipeHtml = "";
    if (room.type === "alch") {
      recipeHtml = '<div class="row" style="margin-top:4px"><select id="sel-recipe">' +
        D.RECIPES.map((r) => '<option value="' + r.key + '" ' + (room.recipe === r.key ? "selected" : "") + " " + (D.repLevel(st.rep) < r.unlockRep ? "disabled" : "") + ">" +
          r.name + (D.repLevel(st.rep) < r.unlockRep ? "（声望 Lv." + r.unlockRep + "）" : "") + "</option>").join("") +
        "</select></div>";
    }
    if (room.type === "forge") {
      recipeHtml = '<div class="row" style="margin-top:4px"><select id="sel-recipe">' +
        D.BLUEPRINTS.map((r) => '<option value="' + r.key + '" ' + (room.recipe === r.key ? "selected" : "") + " " + (D.repLevel(st.rep) < r.unlockRep ? "disabled" : "") + ">" + r.name + "</option>").join("") +
        "</select></div>";
    }
    if (room.type === "talis") {
      recipeHtml = '<div class="row" style="margin-top:4px"><select id="sel-recipe">' +
        D.TALISMANS.map((r) => '<option value="' + r.key + '" ' + (room.recipe === r.key ? "selected" : "") + " " + (D.repLevel(st.rep) < r.unlockRep ? "disabled" : "") + ">" + r.name + "</option>").join("") +
        "</select></div>";
    }

    sp.innerHTML =
      head(def.icon + " " + def.name + '<span class="sub">' + def.en + "</span>", "Lv." + room.lvl + "/" + def.maxLvl,
        '<button class="sp-close" data-panel-close>×</button>') +
      '<div class="sp-body">' +
      '<div class="card"><p>' + def.desc + "</p><p class=" + (room.damaged ? "red" : "jade") + ">" + effectTxt + "</p></div>" +
      (room.damaged
        ? '<div class="card"><h4>🔧 建筑受损（产能减半）</h4><button class="btn primary" id="btn-repair">修复 · 💠 ' + D.fmt(Math.round(def.cost.stone[room.lvl - 1] * 0.25) || 40) + "</button></div>"
        : "") +
      (slots > 0 ? '<div class="card"><h4>🧑‍🌾 职司弟子（' + workers.length + "/" + slots + "）</h4>" + assignedHtml +
        (idleD.length ? '<div class="hr"></div><select id="sel-assign"><option value="">指派弟子…</option>' +
          idleD.map((d) => '<option value="' + d.id + '">' + d.name + "（" + D.STEPS[d.step].cn + "）</option>").join("") +
          "</select>" : "") + "</div>" : "") +
      recipeHtml +
      '<div class="row" style="margin-top:6px">' +
      '<button class="btn primary" id="btn-upgrade" ' + (up && st.canAfford(up) ? "" : "disabled") + ">" +
      (up ? "⬆ 升级到 Lv." + (room.lvl + 1) + " · 💠" + D.fmt(up.stone) + " · " + D.fmtTime(up.time) : "已满级") + "</button>" +
      (room.type !== "hall" ? '<button class="btn danger" id="btn-demo">拆除</button>' : "") +
      "</div>" +
      (room.type === "hall" ? '<p class="muted" style="margin-top:6px">大殿不可拆除。若被攻破则宗门失守。</p>' : "") +
      "</div>";

    sp.querySelector("[data-panel-close]").onclick = () => { G.selectedRoomId = null; G.panelKind = "build"; showPanel("build"); };
    const upBtn = sp.querySelector("#btn-upgrade");
    if (upBtn) upBtn.onclick = () => {
      if (st.upgradeRoom(room)) { toast("「" + def.name + "」升到 Lv." + room.lvl, "good"); refreshPanel(); }
      else toast("灵石不足", "bad");
    };
    const demoBtn = sp.querySelector("#btn-demo");
    if (demoBtn) demoBtn.onclick = () => {
      openModal('<h3>拆除「' + def.name + '」</h3><p class="m-desc">将返还 40% 建造费用（约 💠' + D.fmt(Math.round(def.cost.stone[room.lvl - 1] * 0.4)) + "）。弟子将被遣回待命。</p>" +
        '<div class="m-foot"><button class="btn primary" id="m-ok">确认拆除</button><button class="btn" data-close>取消</button></div>');
      $("#m-ok").onclick = () => { st.demolishRoom(room); G.selectedRoomId = null; showPanel("build"); toast("已拆除", ""); closeTopModal(); };
    };
    const repairBtn = sp.querySelector("#btn-repair");
    if (repairBtn) repairBtn.onclick = () => { if (st.repairRoom(room)) { toast("修缮完成", "good"); refreshPanel(); } else toast("灵石不足", "bad"); };
    const selAssign = sp.querySelector("#sel-assign");
    if (selAssign) selAssign.onchange = () => {
      if (selAssign.value) { st.assignTo(st.getDisciple(selAssign.value), room.id); refreshPanel(); }
    };
    sp.querySelectorAll("[data-unassign]").forEach((b) => {
      b.onclick = () => { const d = st.getDisciple(b.dataset.unassign); if (d) { st.assignTo(d, null); st.tryAutoAssign(); refreshPanel(); } };
    });
    sp.querySelectorAll("[data-disc]").forEach((b) => {
      b.onclick = () => { G.panelKind = "room"; G.panelArg = room; renderDiscDetail(b.dataset.disc, room); };
    });
    const selRec = sp.querySelector("#sel-recipe");
    if (selRec) selRec.onchange = () => { room.recipe = selRec.value; room.progress = 0; refreshPanel(); };
  }
  function closeTopModal() { const root = $("#modal-root"); root.classList.remove("open"); root.innerHTML = ""; }

  /* ── DISCIPLES ───────────────────────────── */
  function discStatusHtml(d) {
    if (d.mission) {
      const target = d.mission.targetId;
      let name = "历练中";
      D.REGIONS.forEach((rg) => {
        const m = rg.missions.find((x) => x.id === target) || rg.raids.find((x) => x.id === target);
        if (m) name = m.name;
      });
      return '<span class="status-badge busy">⏳ ' + name + "</span>";
    }
    if (S().wounded(d)) return '<span class="status-badge wound">🤕 疗伤 ' + D.fmtTime(d.woundedUntil - S().time) + "</span>";
    if (d.workRoom) {
      const r = S().rooms.find((x) => x.id === d.workRoom);
      return '<span class="status-badge idle">' + (r ? D.ROOMS[r.type].icon + D.ROOMS[r.type].name : "工作") + "</span>";
    }
    return '<span class="status-badge idle">待命</span>';
  }

  function renderDisc() {
    const st = S();
    const sp = $("#sidepanel");
    const pool = st.recruit.offers;
    const refreshLeft = Math.max(0, st.recruit.nextRefresh - st.time);
    let poolHtml = pool.map((o, i) => {
      const r = D.ROOTS[o.root];
      return '<div class="row" style="justify-content:space-between;margin-bottom:6px">' +
        '<span><span class="avatar" style="width:26px;height:26px;font-size:11px;background:' + r.robe + '">' + o.name[0] + "</span> " + o.name +
        ' <span class="muted">' + r.name + " · 悟性 " + o.aptitude.toFixed(2) + "</span></span>" +
        '<button class="btn small primary" data-hire="' + i + '" ' + (st.disciples.length >= st.maxDisciples() || !st.canAfford({ stone: o.cost }) ? "disabled" : "") + ">招募 💠" + D.fmt(o.cost) + "</button></div>";
    }).join("");
    let listHtml = st.disciples.map((d) => {
      const stt = st.discipleStats(d);
      const r = D.ROOTS[d.root];
      return '<div class="disc-row" data-disc="' + d.id + '">' +
        '<div class="avatar" style="background:' + r.robe + '">' + (d.gender === "f" ? "女" : "男") + "</div>" +
        '<div class="grow"><div class="d-name">' + d.name + ' <span class="muted" style="font-size:10px">' + r.name + "</span></div>" +
        '<div class="d-sub">' + D.STEPS[d.step].cn + " · 战力 " + stt.power + " · " + discStatusHtml(d) + "</div></div></div>";
    }).join("");

    sp.innerHTML =
      head("弟子 · Disciples", st.disciples.length + "/" + st.maxDisciples() + " 名", "") +
      '<div class="sp-body">' +
      '<div class="card"><h4>🧲 接引仙缘 <span class="muted" style="font-weight:400">（' + D.fmtTime(refreshLeft) + " 后轮换）</span></h4>" + poolHtml +
      '<button class="btn small" id="btn-refresh" ' + (refreshLeft > 0 ? "disabled" : "") + ">刷新（免费）</button></div>" +
      '<div class="card"><h4>门中弟子</h4>' + (listHtml || '<p>暂无弟子</p>') + "</div>" +
      "</div>";
    sp.querySelectorAll("[data-hire]").forEach((b) => {
      b.onclick = () => {
        const o = pool[+b.dataset.hire];
        const res = st.hire(o);
        if (res === "ok") { toast("「" + o.name + "」拜入宗门！", "good"); }
        else if (res === "full") toast("弟子名额已满，需升级大殿或建造居所", "bad");
        else toast("灵石不足", "bad");
        renderDisc();
      };
    });
    const rb = sp.querySelector("#btn-refresh");
    if (rb) rb.onclick = () => { st.refreshRecruit(true); renderDisc(); };
    sp.querySelectorAll("[data-disc]").forEach((row) => {
      row.onclick = () => { G.panelKind = "disc"; G.panelArg = row.dataset.disc; renderDiscDetail(row.dataset.disc); };
    });
  }

  function renderDiscDetail(did, roomCtx) {
    const st = S();
    const d = st.getDisciple(did);
    if (!d) { showPanel("disc"); return; }
    const sp = $("#sidepanel");
    const r = D.ROOTS[d.root];
    const stt = st.discipleStats(d);
    const step = D.STEPS[d.step];
    const gate = st.gateForStep(d.step);
    const gateReady = gate && d.xp >= step.xp;
    const wep = st.getArt(d.equip.weapon);
    const arm = st.getArt(d.equip.armor);
    const pillKeys = ["pill_qi", "pill_pei"];
    const breakPill = gate ? gate.pill : null;

    const skillRow = (label, v) =>
      '<div style="display:flex;gap:6px;align-items:center;margin-bottom:3px"><span style="width:44px;font-size:10.5px;color:var(--dim)">' + label + "</span>" +
      '<div class="pbar blue" style="flex:1"><div style="width:' + v + '%"></div><span>' + Math.round(v) + "</span></div></div>";

    let assignSel = '<select id="sel-work" style="width:100%"><option value="">待命（自由走动）</option>';
    st.rooms.forEach((rm) => {
      const slots = st.roomSlots(rm) || 0;
      const inRoom = rm.assigned.includes(d.id);
      const free = inRoom || rm.assigned.length < slots;
      if (slots > 0) {
        assignSel += '<option value="' + rm.id + '" ' + (d.workRoom === rm.id ? "selected" : "") + " " + (free ? "" : "disabled") + ">" +
          D.ROOMS[rm.type].icon + D.ROOMS[rm.type].name + (inRoom ? "（现职）" : "") + "</option>";
      }
    });
    assignSel += "</select>";

    let breakHtml = "";
    if (gateReady) {
      const chance = D.clamp(gate.chance + (breakPill && (st.res[breakPill] || 0) > 0 ? 0.22 : 0), 0.05, 0.98);
      breakHtml = '<div class="card" style="border-color:var(--gold-dim)"><h4>⚡ 突破之机</h4>' +
        '<p>冲关' + gate.name + "！成功率 <b class=\"gold\">" + Math.round(chance * 100) + "%</b>" + (breakPill && (st.res[breakPill] || 0) > 0 ? "（含" + RES_NAME[breakPill] + "）" : "") + "</p>" +
        '<button class="btn gold big" id="btn-break">⛰ 冲击' + gate.name + "</button>" +
        (breakPill ? '<p class="muted" style="margin-top:4px">持有 ' + RES_NAME[breakPill] + " ×" + (st.res[breakPill] || 0) + "，冲关时自动服用 (+22%)</p>" : "") +
        "</div>";
    } else if (gate && d.xp >= step.xp) {
      breakHtml = '<div class="card" style="border-color:var(--gold-dim)"><h4>⚡ 瓶颈</h4><p>修为已至圆满，需要「' + RES_NAME[gate.pill] + '」辅助才能冲击' + gate.name + "。</p></div>";
    }

    sp.innerHTML =
      head("🧑‍🌾 " + d.name + '<span class="sub">' + (d.gender === "f" ? "女弟子" : "男弟子") + "</span>",
        D.STEPS[d.step].tierName, '<button class="sp-close" data-panel-close>×</button>') +
      '<div class="sp-body">' +
      '<div class="card">' +
      '<div class="row"><span class="avatar" style="width:44px;height:44px;font-size:17px;background:' + r.robe + '">' + d.name[0] + "</span>" +
      '<div class="grow"><div>' + r.name + ' <span class="muted">· ' + r.en + '</span> <span class="muted" style="font-size:10px">(' + ((D.Battle.STYLE_INFO[r.style] || {}).label || "") + ")</span></div>" +
      '<div class="muted" style="font-size:11px">悟性 ' + d.aptitude.toFixed(2) + " · 战力 <b class=\"gold\">" + stt.power + "</b></div>" +
      '<div class="muted" style="font-size:11px">攻 ' + stt.atk + " · 血 " + stt.hp + "</div></div></div>" +
      '<div class="hr"></div>' +
      '<div class="muted" style="font-size:10.5px;margin-bottom:3px">境界 · ' + step.cn + " (" + step.en + ")</div>" +
      '<div class="pbar gold"><div style="width:' + (step.xp ? Math.min(100, d.xp / step.xp * 100) : 100) + '%"></div><span>' + D.fmt(d.xp) + " / " + D.fmt(step.xp) + " 修为</span></div>" +
      "</div>" +
      breakHtml +
      '<div class="card"><h4>💊 丹药</h4><div class="row">' +
      pillKeys.map((k) => {
        const rec = D.RECIPES.find((x) => x.key === k);
        return '<button class="btn small" data-pill="' + k + '" ' + ((st.res[k] || 0) > 0 && !gateReady ? "" : "disabled") + ">" + RES_ICON[k] + " " + rec.name + " ×" + (st.res[k] || 0) + "</button>";
      }).join("") +
      (breakPill ? '<button class="btn small gold" data-pill="' + breakPill + '" ' + (gateReady && (st.res[breakPill] || 0) > 0 ? "" : "disabled") + ">服用" + RES_NAME[breakPill] + "</button>" : "") +
      "</div></div>" +
      '<div class="card"><h4>⚔️ 法器</h4>' +
      '<div class="row" style="justify-content:space-between;margin-bottom:4px"><span class="muted">🗡️ 武器</span><span>' + (wep ? wepLabel(wep) : "无") + ' <button class="btn small" data-equip="weapon">换</button></span></div>' +
      '<div class="row" style="justify-content:space-between"><span class="muted">🥋 衣袍</span><span>' + (arm ? wepLabel(arm) : "无") + ' <button class="btn small" data-equip="armor">换</button></span></div>' +
      "</div>" +
      '<div class="card"><h4>📚 技艺</h4>' +
      skillRow("种植", d.skills.plant) + skillRow("炼丹", d.skills.alchemy) + skillRow("炼器", d.skills.forge) + skillRow("制符", d.skills.talisman) +
      "</div>" +
      '<div class="card"><h4>🏯 职司</h4>' + assignSel + "</div>" +
      "</div>";

    sp.querySelector("[data-panel-close]").onclick = () => { G.panelKind = roomCtx ? "room" : "disc"; G.panelArg = roomCtx ? roomCtx : null; showPanel(G.panelKind, G.panelArg); };
    const selWork = sp.querySelector("#sel-work");
    if (selWork) selWork.onchange = () => {
      st.assignTo(d, selWork.value || null);
      if (roomCtx) renderRoomPanel(roomCtx); else renderDiscDetail(did);
    };
    sp.querySelectorAll("[data-pill]").forEach((b) => {
      b.onclick = () => {
        const key = b.dataset.pill;
        if (gateReady && key === gate.pill) { st.tryBreakthrough(d, true); renderDiscDetail(did, roomCtx); return; }
        if (st.usePill(d, key)) { toast(d.name + " 服用「" + RES_NAME[key] + "」，修为大涨！", "good"); renderDiscDetail(did, roomCtx); }
        else toast("暂不可服用", "bad");
      };
    });
    const brkBtn = sp.querySelector("#btn-break");
    if (brkBtn) brkBtn.onclick = () => {
      const usePill = breakPill && (st.res[breakPill] || 0) > 0;
      const chance = D.clamp(gate.chance + (usePill ? 0.22 : 0), 0.05, 0.98);
      openModal('<h3>冲击' + gate.name + "！<span class=\"en\">BREAKTHROUGH</span></h3>" +
        '<p class="m-desc" style="text-align:center">' + d.name + " 盘坐闭关，冲击" + D.STEPS[gate.to].cn + "。<br>成功率 <b class=\"gold\">" + Math.round(chance * 100) + "%</b>；失败将损失 25% 修为。</p>" +
        '<div class="m-foot"><button class="btn gold" id="m-go">闭关突破</button><button class="btn" data-close>再等等</button></div>');
      $("#m-go").onclick = () => {
        closeTopModal();
        const res = st.tryBreakthrough(d, usePill);
        if (res.ok) { toast("🎉 " + d.name + " 突破成功，晋升" + D.STEPS[d.step].cn + "！", "good"); G.Render.spawnText(G.camFxX(d), G.camFxY(d), "突破！", "#f0c060", true); }
        else if (res.err === "nopill") toast("没有丹药", "bad");
        else { toast("😰 走火入魔……修为受损", "bad"); G.Render.spawnText(G.camFxX(d), G.camFxY(d), "走火入魔", "#d9685e", true); }
        renderDiscDetail(did, roomCtx);
      };
    };
    sp.querySelectorAll("[data-equip]").forEach((b) => {
      b.onclick = () => openEquipModal(d, b.dataset.equip, () => renderDiscDetail(did, roomCtx));
    });
  }

  function wepLabel(a) {
    const t = D.TIERS[a.tier];
    const bp = D.BLUEPRINTS.find((x) => x.key === a.bp);
    return '<span style="color:' + t.color + ';font-size:11px">' + (bp ? bp.icon : "") + t.name + (bp ? bp.name : "") + " +" + a.stat + "</span>";
  }

  function openEquipModal(d, slot, cb) {
    const st = S();
    const bpKeys = D.BLUEPRINTS.filter((bp) => bp.slot === slot).map((bp) => bp.key);
    const pool = st.arts.filter((a) => bpKeys.includes(a.bp) && !st.disciples.some((x) => x.id !== d.id && (x.equip.weapon === a.id || x.equip.armor === a.id)));
    const cur = st.getArt(d.equip[slot]);
    let list = pool.map((a) => {
      const t = D.TIERS[a.tier], bp = D.BLUEPRINTS.find((x) => x.key === a.bp);
      return '<div class="squad-item" data-art="' + a.id + '"><span>' + bp.icon + "</span>" +
        '<div class="grow"><div class="s-name" style="color:' + t.color + '">' + t.name + bp.name + " +" + a.stat + "</div>" +
        '<div class="s-sub">' + (bp.stat === "atk" ? "攻击" : "气血") + "</div></div>" +
        '<button class="btn small">装备</button></div>';
    }).join("");
    if (cur) {
      list = '<div class="squad-item" data-unequip="' + cur.id + '"><span>↩️</span><div class="grow"><div class="s-name">卸下当前法器</div></div></div>' + list;
    }
    openModal('<h3>选择' + (slot === "weapon" ? "武器" : "衣袍") + '<span class="en">EQUIP</span></h3>' +
      '<div class="squad-list">' + (list || '<p class="muted">仓库中没有可用的法器，去炼器坊炼制吧。</p>') + "</div>" +
      '<div class="m-foot"><button class="btn" data-close>关闭</button></div>');
    document.querySelectorAll("[data-art]").forEach((row) => {
      row.onclick = () => {
        const old = d.equip[slot];
        if (old) st.getArt(old); // keep in inventory automatically (arts persist)
        d.equip[slot] = row.dataset.art;
        closeTopModal(); cb();
      };
    });
    document.querySelectorAll("[data-unequip]").forEach((row) => {
      row.onclick = () => { d.equip[slot] = null; closeTopModal(); cb(); };
    });
  }

  /* ── QUEST / MISSIONS ────────────────────── */
  function renderQuest() {
    const st = S();
    const sp = $("#sidepanel");
    let activeHtml = "";
    st.missions.active.forEach((m) => {
      let name = "历练中";
      D.REGIONS.forEach((rg) => {
        const t = rg.missions.find((x) => x.id === m.targetId) || rg.raids.find((x) => x.id === m.targetId);
        if (t) name = t.name;
      });
      const left = Math.max(0, m.until - st.time);
      const dur = m.duration || 60;
      activeHtml += '<div class="m-row"><div class="m-icon">⏳</div><div class="m-info"><div class="m-name">' + name + "</div>" +
        '<div class="pbar blue" style="height:8px"><div style="width:' + D.clamp((1 - left / dur) * 100, 0, 100) + '%"></div></div>' +
        '<div class="m-sub">' + D.fmtTime(left) + " 后接战 · " + m.squad.length + " 名弟子</div></div></div>";
    });
    let regionHtml = "";
    D.REGIONS.forEach((rg) => {
      const unlocked = D.repLevel(st.rep) >= D.repLevel(rg.rep);
      if (!unlocked) {
        regionHtml += '<div class="card" style="opacity:.5"><h4>' + rg.icon + " " + rg.name + ' <span class="muted">· 声望 Lv.' + D.repLevel(rg.rep) + " 解锁</span></h4></div>";
        return;
      }
      let ms = rg.missions.map((m) => {
        const cooling = st.missions.lastDone[m.id] && st.time - st.missions.lastDone[m.id] < m.cooldown;
        const cdLeft = cooling ? m.cooldown - (st.time - st.missions.lastDone[m.id]) : 0;
        const rewards = Object.keys(m.rewards).map((k) => RES_ICON[k] + (k === "rep" ? "" : D.fmt(m.rewards[k]))).join(" ");
        return '<div class="m-row' + (cooling ? " cooling" : "") + '" data-mission="' + m.id + '">' +
          '<div class="m-icon">' + m.icon + "</div>" +
          '<div class="m-info"><div class="m-name">' + m.name + '<span class="en">' + m.en + "</span></div>" +
          '<div class="m-sub">' + m.desc + "<br>奖励: <b>" + rewards + "</b> · 修为 x" + m.xp + "</div></div>" +
          '<div class="power-badge">⚔ ' + m.power + "</div>" +
          (cooling ? '<div class="cd">' + D.fmtTime(cdLeft) + "</div>" : '<button class="btn small primary" data-go="' + m.id + '">出发</button>') +
          "</div>";
      }).join("");
      let rs = rg.raids.map((r) => {
        const cooling = st.raids.last[r.id] && st.time - st.raids.last[r.id] < r.cooldown;
        const cdLeft = cooling ? r.cooldown - (st.time - st.raids.last[r.id]) : 0;
        const loot = Object.keys(r.loot).map((k) => RES_ICON[k] + (k === "rep" ? "" : D.fmt(r.loot[k]))).join(" ");
        return '<div class="m-row' + (cooling ? " cooling" : "") + '" style="border-color:#5c2a26">' +
          '<div class="m-icon">' + r.icon + "</div>" +
          '<div class="m-info"><div class="m-name" style="color:#e8a098">' + r.name + '<span class="en">' + r.en + "</span></div>" +
          '<div class="m-sub">' + r.desc + "<br>战利品: <b>" + loot + "</b></div></div>" +
          '<div class="power-badge">⚔ ' + r.power + "</div>" +
          (cooling ? '<div class="cd">' + D.fmtTime(cdLeft) + "</div>" : '<button class="btn small gold" data-raid="' + r.id + '">攻伐</button>') +
          "</div>";
      }).join("");
      regionHtml += '<div class="card"><h4>' + rg.icon + " " + rg.name + ' <span class="muted" style="font-weight:400">' + rg.en + "</span></h4>" + ms + rs + "</div>";
    });

    sp.innerHTML =
      head("历练 · Missions", "宗门战力 <b class=\"gold\">" + st.sectPower() + "</b>", "") +
      '<div class="sp-body">' +
      (activeHtml ? '<div class="card"><h4>⏳ 进行中的历练</h4>' + activeHtml + "</div>" : "") +
      regionHtml +
      "</div>";

    sp.querySelectorAll("[data-go]").forEach((b) => {
      b.onclick = () => openSquadPicker("mission", b.dataset.go);
    });
    sp.querySelectorAll("[data-raid]").forEach((b) => {
      b.onclick = () => openSquadPicker("raid", b.dataset.raid);
    });
  }

  function findTarget(kind, id) {
    for (const rg of D.REGIONS) {
      const t = kind === "mission" ? rg.missions.find((x) => x.id === id) : rg.raids.find((x) => x.id === id);
      if (t) return { t, rg };
    }
    return null;
  }

  function openSquadPicker(kind, id) {
    const st = S();
    const info = findTarget(kind, id);
    if (!info) return;
    const { t, rg } = info;
    const squadPower = () => st.squadPower(selected);
    const selected = [];
    let list = st.availableFighters().map((d) => {
      const stt = st.discipleStats(d);
      return { d, stt };
    });
    const draw = () => {
      const rows = list.map(({ d, stt }) => {
        const on = selected.includes(d.id);
        const disabled = !on && selected.length >= D.SQUAD_MAX;
        return '<div class="squad-item' + (on ? " on" : "") + (disabled ? " disabled" : "") + '" data-pick="' + d.id + '">' +
          '<span class="avatar" style="width:26px;height:26px;font-size:10px;background:' + D.ROOTS[d.root].robe + '">' + d.name[0] + "</span>" +
          '<div class="grow"><div class="s-name">' + d.name + "</div>" +
          '<div class="s-sub">' + D.STEPS[d.step].cn + " · 攻" + stt.atk + " 血" + stt.hp + " · " + D.ROOTS[d.root].name + "</div></div>" +
          '<span class="power-badge">' + stt.power + "</span></div>";
      }).join("");
      const m = $("#squad-list");
      if (m) m.innerHTML = rows || '<p class="muted">没有可出战的弟子（可能在疗伤或已外出）</p>';
      const pw = $("#squad-power");
      if (pw) pw.innerHTML = "出战战力 <b class=\"gold\">" + squadPower() + "</b> / 敌方 <b>" + (t.power || 0) + "</b>";
      const go = $("#squad-go");
      if (go) go.disabled = selected.length === 0;
      document.querySelectorAll("[data-pick]").forEach((row) => {
        row.onclick = () => {
          const did = row.dataset.pick;
          if (selected.includes(did)) selected.splice(selected.indexOf(did), 1);
          else if (selected.length < D.SQUAD_MAX) selected.push(did);
          draw();
        };
      });
    };
    const loot = (kind === "mission" ? t.rewards : t.loot) || {};
    const lootTxt = Object.keys(loot).map((k) => RES_ICON[k] + (k === "rep" ? D.fmt(loot[k]) : D.fmt(loot[k]))).join(" ");
    openModal('<h3>' + (kind === "mission" ? "派出历练" : "攻打敌宗") + '<span class="en">' + t.name + " · " + t.en + "</span></h3>" +
      '<p class="m-desc">' + t.desc + "<br>奖励: <b class=\"gold\">" + lootTxt + "</b> · 修为 x" + t.xp + " · " + D.fmtTime(t.duration || 25) + "</p>" +
      '<div id="squad-power" class="muted" style="text-align:center;margin-bottom:4px"></div>' +
      '<div class="squad-list" id="squad-list"></div>' +
      '<div class="m-foot"><button class="btn primary" id="squad-go">出击！</button><button class="btn" data-close>取消</button></div>');
    draw();
    $("#squad-go").onclick = () => {
      const res = kind === "mission" ? st.startMission(id, selected.slice()) : st.startRaid(id, selected.slice());
      if (res === "ok") { closeTopModal(); toast("弟子已出发：" + t.name, "good"); renderQuest(); }
      else if (res === "cd") toast("冷却中，稍后再来", "bad");
      else if (res === "rep") toast("声望不足", "bad");
      else toast("无法出战", "bad");
    };
  }

  /* ── LIBRARY ─────────────────────────────── */
  function renderLib() {
    const st = S();
    const sp = $("#sidepanel");
    const repLvl = D.repLevel(st.rep);
    const lib = st.rooms.find((r) => r.type === "lib");
    let researchHtml = "";
    if (st.techs.researching) {
      const t = D.TECHS.find((x) => x.key === st.techs.researching);
      const libProg = lib ? lib.progress : 0;
      researchHtml = '<div class="card" style="border-color:var(--gold-dim)"><h4>📖 研究中：' + t.icon + t.name + "</h4>" +
        '<div class="pbar gold"><div style="width:' + D.clamp(libProg / t.time * 100, 0, 100) + '%"></div><span>' + Math.round(D.clamp(libProg / t.time, 0, 1) * 100) + "%</span></div>" +
        '<div class="row" style="margin-top:6px"><button class="btn small danger" id="btn-cancel">取消（返还费用）</button></div></div>';
    }
    let techsHtml = D.TECHS.map((t) => {
      const done = st.techs.unlocked.includes(t.key);
      const researching = st.techs.researching === t.key;
      const locked = repLvl < t.unlockRep;
      const afford = st.canAfford(t.cost);
      return '<div class="tech-row' + (done ? " done" : "") + (researching ? " researching" : "") + '">' +
        '<div class="t-icon">' + t.icon + "</div>" +
        '<div class="t-info"><div class="t-name">' + t.name + ' <span class="en muted" style="font-size:10px">' + t.en + "</span></div>" +
        '<div class="t-sub">' + t.desc + "</div>" +
        '<div class="cost" style="margin-top:2px">' + costHtml(t.cost) + " · " + D.fmtTime(t.time) + (locked ? ' · <span class="red">声望 Lv.' + t.unlockRep + "</span>" : "") + "</div></div>" +
        (done ? '<span class="jade" style="font-size:11px">✓</span>' :
          researching ? '<span class="gold" style="font-size:11px">研…</span>' :
            '<button class="btn small primary" data-tech="' + t.key + '" ' + ((locked || !afford || st.techs.researching || !lib) ? "disabled" : "") + ">研究</button>") +
        "</div>";
    }).join("");
    sp.innerHTML =
      head("藏经阁 · Library", "功法研究", "") +
      '<div class="sp-body">' +
      (lib ? "" : '<div class="card"><p class="red">尚未建造藏经阁，无法研究功法。</p></div>') +
      researchHtml + techsHtml +
      "</div>";
    const cancel = sp.querySelector("#btn-cancel");
    if (cancel) cancel.onclick = () => { st.cancelResearch(); renderLib(); };
    sp.querySelectorAll("[data-tech]").forEach((b) => {
      b.onclick = () => {
        if (st.startResearch(b.dataset.tech)) { toast("开始研究「" + D.TECHS.find((x) => x.key === b.dataset.tech).name + "」"); renderLib(); }
        else toast("无法研究", "bad");
      };
    });
  }

  /* ── STORE ──────────────────────────────── */
  function renderStore() {
    const st = S();
    const sp = $("#sidepanel");
    const caps = st.caps();
    const pillRows = D.RECIPES.map((r) => {
      const n = st.res[r.key] || 0;
      return '<div class="store-row"><div class="s-icon">' + r.icon + "</div>" +
        '<div class="s-info"><div class="s-name">' + r.name + ' <span class="muted" style="font-size:10px">' + r.en + "</span></div>" +
        '<div class="s-sub">' + r.desc + "</div></div>" +
        '<div class="s-num">' + n + "</div></div>";
    }).join("");
    const talisRows = D.TALISMANS.map((r) => {
      const n = st.res[r.key] || 0;
      return '<div class="store-row"><div class="s-icon">' + r.icon + "</div>" +
        '<div class="s-info"><div class="s-name">' + r.name + ' <span class="muted" style="font-size:10px">' + r.en + "</span></div>" +
        '<div class="s-sub">' + r.desc + "</div></div>" +
        '<div class="s-num">' + n + "</div></div>";
    }).join("");
    let artRows = st.arts.map((a) => {
      const t = D.TIERS[a.tier], bp = D.BLUEPRINTS.find((x) => x.key === a.bp);
      const holder = st.disciples.find((x) => x.equip.weapon === a.id || x.equip.armor === a.id);
      return '<div class="store-row"><div class="s-icon">' + bp.icon + "</div>" +
        '<div class="s-info"><div class="s-name" style="color:' + t.color + '">' + t.name + bp.name + " +" + a.stat + "</div>" +
        '<div class="s-sub">' + (bp.stat === "atk" ? "攻击" : "气血") + (holder ? " · " + holder.name + " 佩戴中" : " · 未装备") + "</div></div></div>";
    }).join("");
    sp.innerHTML =
      head("仓库 · Inventory", "聚宝阁储量上限 +" + Math.round((st.rooms.filter((r) => r.type === "store").reduce((s, r) => s + 0.35 * r.lvl, 0)) * 100) + "%", "") +
      '<div class="sp-body">' +
      '<div class="card"><h4>📦 储量</h4><p>💠 灵石 ' + D.fmt(st.res.stone) + "/" + D.fmt(caps.stone) + " · 🌿 灵草 " + D.fmt(st.res.herb) + "/" + D.fmt(caps.herb) + "<br>⛏️ 矿石 " + D.fmt(st.res.ore) + "/" + D.fmt(caps.ore) + " · 💊 丹药 " + st.pillCount() + "/" + caps.pill + " · 🧿 符箓 " + st.talisCount() + "/" + caps.talisman + " · ⚔️ 法器 " + st.arts.length + "/" + caps.art + "</p></div>" +
      '<div class="card"><h4>💊 灵丹</h4>' + (pillRows || "<p>暂无丹药</p>") + "</div>" +
      '<div class="card"><h4>🧿 符箓</h4>' + (talisRows || "<p>暂无符箓</p>") + "</div>" +
      '<div class="card"><h4>⚔️ 法器</h4>' + (artRows || "<p>暂无法器，去炼器坊炼制</p>") + "</div>" +
      "</div>";
  }

  /* ── TASKS ───────────────────────────────── */
  function renderTasks() {
    const st = S();
    const sp = $("#sidepanel");
    let rows = D.TASKS.map((t) => {
      const done = st.tasksDone.includes(t.id);
      return '<div class="task-line' + (done ? " done" : "") + '"><span class="t-ico">' + (done ? "✅" : t.icon) + "</span>" +
        '<span class="t-txt"><b>' + t.title + "</b> · " + t.desc + "</span>" +
        "<span class=\"muted\">" + Object.keys(t.reward).map((k) => RES_ICON[k] + D.fmt(t.reward[k])).join(" ") + "</span></div>";
    }).join("");
    const repLvl = D.repLevel(st.rep);
    const next = D.REP_LEVELS[repLvl];
    sp.innerHTML =
      head("议事 · Tasks", "掌门日志", "") +
      '<div class="sp-body">' +
      '<div class="card"><h4>🏮 宗门纪要</h4><p>' +
      "声望 <b class=\"gold\">" + D.fmt(st.rep) + "</b>（Lv." + repLvl + (next ? "，距 Lv." + (repLvl + 1) + " 还需 " + D.fmt(next - st.rep) : "，已登峰造极") + "）<br>" +
      "弟子 " + st.disciples.length + " 名 · 殿阁 " + st.rooms.length + " 座<br>" +
      "历练 " + st.stats.missionsWon + " 胜 · 攻伐 " + st.stats.raidsWon + " 胜 · 守山 " + st.stats.defensesWon + " 胜 · " + st.stats.battlesLost + " 败<br>" +
      "炼成丹药 " + st.stats.pillsMade + " · 法器 " + st.stats.artsMade + " · 符箓 " + st.stats.talisMade + " · 突破 " + st.stats.breakthroughs + " 次" +
      "</p></div>" +
      '<div class="card"><h4>📋 掌门试炼</h4>' + rows + "</div>" +
      '<div class="card" style="text-align:center">' +
      '<button class="btn danger small" id="btn-reset">🔥 重新开山立派（清除存档）</button>' +
      "</div></div>";
    const reset = sp.querySelector("#btn-reset");
    if (reset) reset.onclick = () => {
      openModal('<h3>重新开始？</h3><p class="m-desc">当前存档将被清除，宗门将重归白纸。</p>' +
        '<div class="m-foot"><button class="btn danger" id="m-reset">确定</button><button class="btn" data-close>取消</button></div>');
      $("#m-reset").onclick = () => {
        G.GameState.wipe();
        location.reload();
      };
    };
  }

  /* ── TOP BAR ─────────────────────────────── */
  function updateTopbar() {
    const st = S();
    if (!st) return;
    $("#sect-name").textContent = st.sectName;
    const repLvl = D.repLevel(st.rep);
    const repIdx = repLvl - 1;
    const repLo = D.REP_LEVELS[repIdx] || 0;
    const repHi = D.REP_LEVELS[repIdx + 1] || repLo + 1;
    $("#rep-fill").style.width = D.clamp((st.rep - repLo) / (repHi - repLo) * 100, 0, 100) + "%";
    $("#rep-txt").textContent = "声望 Lv." + repLvl;
    const caps = st.caps();
    const setChip = (id, val, cap, warnThreshold) => {
      const c = $(id);
      if (!c) return;
      c.classList.toggle("full", cap && val >= cap);
      c.classList.toggle("low", warnThreshold && val < warnThreshold);
      c.querySelector("b").textContent = D.fmt(val);
    };
    setChip("#r-stone", st.res.stone, caps.stone, 200);
    setChip("#r-herb", st.res.herb, caps.herb, 50);
    setChip("#r-pill", st.pillCount(), caps.pill, 5);
    setChip("#r-ore", st.res.ore, caps.ore, 20);
    setChip("#r-art", st.arts.length, caps.art, 0);
    setChip("#r-talis", st.talisCount(), caps.talisman, 3);
    const energy = st.energyInfo();
    const ec = $("#energy-chip");
    ec.classList.toggle("warn", energy.demand > energy.supply);
    ec.querySelector("b").textContent = energy.supply + "/" + energy.demand;
    $("#power-chip b").textContent = st.sectPower();
    const day = Math.floor(st.time / D.DAY_SECS) + 1;
    $("#day-txt").textContent = "第 " + day + " 天";
    $("#shichen").textContent = D.SHICHEN[Math.floor(st.time / 50) % 12];
    document.querySelectorAll(".spd-btn").forEach((b) => {
      b.classList.toggle("on", +b.dataset.spd === st.speed);
    });
  }

  /* ── task tracker ────────────────────────── */
  function updateTasks() {
    const st = S();
    const box = $("#tasktracker");
    if (!box) return;
    const pending = D.TASKS.filter((t) => !st.tasksDone.includes(t.id));
    const active = pending[0];
    box.innerHTML = active
      ? '<div class="task-line"><span class="t-ico">🎯</span><span class="t-txt"><b>掌门试炼</b> · ' + active.title + "：<span style=\"color:var(--dim)\">" + active.desc + "</span></span></div>"
      : '<div class="task-line done"><span class="t-ico">🎊</span><span class="t-txt">掌门试炼已全部完成！继续开疆拓土吧。</span></div>';
  }

  /* ── event modal ─────────────────────────── */
  function showEvent(ev) {
    const st = S();
    let choices = ev.choices.map((c, i) => {
      const afford = c.cost ? st.canAfford(c.cost) : true;
      return '<button class="btn big" style="margin-bottom:6px" data-choice="' + i + '" ' + (afford ? "" : "disabled") + ">" + c.text + "</button>";
    }).join("");
    const m = openModal('<h3>' + ev.icon + " " + ev.title + '<span class="en">' + ev.en + "</span></h3>" +
      '<p class="m-desc">' + ev.desc + "</p>" + choices);
    m.el.querySelectorAll("[data-choice]").forEach((b) => {
      b.onclick = () => {
        const c = ev.choices[+b.dataset.choice];
        if (c.cost) st.payCost(c.cost);
        const extra = c.apply(st) || "";
        st.events.pending = null;
        st.events.nextAt = st.time + D.rand(140, 260);
        closeTopModal();
        if (extra) toast(extra, "good");
        else toast(ev.title + "：已决断");
        // a defense may have been triggered by the choice (e.g. beast incursion)
        if (st.defense && st.time < st.defense.until) showDefenseWarning(st.defense);
        refreshPanel();
      };
    });
  }

  /* ── defense warning ─────────────────────── */
  function showDefenseWarning(def) {
    const st = S();
    let name = "来敌";
    if (def.kind === "raid") {
      for (const rg of D.REGIONS) {
        const r = rg.raids.find((x) => x.id === def.targetId);
        if (r) name = r.name;
      }
    }
    const until = () => Math.max(0, st.defense.until - st.time);
    const m = openModal('<h3>🚨 敌袭！<span class="en">SECT UNDER ATTACK</span></h3>' +
      '<p class="m-desc" style="text-align:center">' + name + " 大举来犯！<br>全宗弟子将自动出战，护山大阵亦将开启。</p>" +
      '<div class="pbar gold" style="margin-bottom:10px"><div id="warn-fill" style="width:100%"></div><span id="warn-txt"></span></div>' +
      '<div class="m-foot"><button class="btn primary" id="warn-go">⚔ 立即迎战！</button></div>', { sticky: true });
    const iv = setInterval(() => {
      const left = until();
      const fill = $("#warn-fill"), txt = $("#warn-txt");
      if (fill) fill.style.width = (left / 22 * 100) + "%";
      if (txt) txt.textContent = Math.ceil(left) + " 秒后开战";
      if (!st.defense || left <= 0) { clearInterval(iv); closeTopModal(); }
    }, 200);
    const go = m.el.querySelector("#warn-go");
    if (go) go.onclick = () => { clearInterval(iv); closeTopModal(); st.resolveDefense(); };
  }

  /* ── tooltip ─────────────────────────────── */
  function showTooltip(html, x, y) {
    const t = $("#tooltip");
    t.innerHTML = html;
    t.style.display = "block";
    const w = t.offsetWidth;
    t.style.left = Math.min(x + 14, window.innerWidth - w - 10) + "px";
    t.style.top = Math.min(y + 14, window.innerHeight - 60) + "px";
  }
  function hideTooltip() { const t = $("#tooltip"); t.style.display = "none"; }

  D.UI = {
    setTab, showPanel, refreshPanel, renderRoomPanel, renderDiscDetail, renderQuest,
    updateTopbar, updateTasks, toast, openModal, closeTopModal, showEvent, showDefenseWarning,
    showTooltip, hideTooltip, costHtml, RES_ICON, RES_NAME,
  };
})();
