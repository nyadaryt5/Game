/* ═══════════════════════════════════════════════════════
   仙门掌门 · Sect Master — WORLD RENDERER
   ═══════════════════════════════════════════════════════ */
(function () {
  "use strict";
  const D = (window.G = window.G || {});
  const TILE = 44;

  const cam = { x: 0, y: 0, zoom: 1 };
  const wanderers = {}; // discipleId -> {x,y,tx,ty,wait,phase,dir}
  const fx = { texts: [], parts: [] };

  let canvas = null, ctx = null, W = 0, H = 0;

  /* ── palettes ─────────────────────────────── */
  function tierStyle(lvl) {
    const i = lvl >= 10 ? 3 : lvl >= 7 ? 2 : lvl >= 4 ? 1 : 0;
    return [
      { slab: "#39403a", slabD: "#2a2f2b", wall: "#8a7a5c", roof: "#6b4f35", edge: "#4a3826", accent: "#a08a5c" },
      { slab: "#35404c", slabD: "#262e37", wall: "#7a8a9c", roof: "#3f5f78", edge: "#2c4356", accent: "#6d93b0" },
      { slab: "#3c3348", slabD: "#2b2434", wall: "#8a7a9c", roof: "#5c3f78", edge: "#3e2c56", accent: "#8a6db0" },
      { slab: "#4a3d24", slabD: "#362c1a", wall: "#c9b078", roof: "#a8822c", edge: "#7a5c1e", accent: "#e0c070" },
    ][i];
  }

  /* ── setup ────────────────────────────────── */
  function init(cv) {
    canvas = cv;
    ctx = canvas.getContext("2d");
  }
  function resize() {
    if (!canvas) return;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    W = canvas.clientWidth; H = canvas.clientHeight;
    canvas.width = W * dpr; canvas.height = H * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
  function fitView() {
    const gw = D.GRID.w * TILE, gh = D.GRID.h * TILE + 40;
    const z = Math.min((W - 60) / gw, (H - 80) / gh, 1.4);
    cam.zoom = Math.max(0.5, z);
    cam.x = (W - gw * cam.zoom) / 2;
    cam.y = (H - gh * cam.zoom) / 2 + 10;
  }
  function w2s(x, y) { return [cam.x + x * cam.zoom, cam.y + y * cam.zoom]; }
  function s2w(x, y) { return [(x - cam.x) / cam.zoom, (y - cam.y) / cam.zoom]; }
  function tileAt(sx, sy) {
    const [wx, wy] = s2w(sx, sy);
    return { tx: Math.floor(wx / TILE), ty: Math.floor(wy / TILE), wx, wy };
  }

  /* ── effects ──────────────────────────────── */
  function spawnText(wx, wy, txt, color, big) {
    fx.texts.push({ x: wx, y: wy, txt, color: color || "#f3ead2", life: 1.4, big: !!big });
  }
  function spawnPart(wx, wy, type) {
    const colors = { smoke: "#9aa89c", gold: "#f0c060", jade: "#5fc9a2", red: "#d9685e", spark: "#ffe9b0" };
    fx.parts.push({
      x: wx, y: wy, vx: D.rand(-8, 8), vy: D.rand(-22, -8), life: D.rand(0.7, 1.4), t: 0,
      color: colors[type] || "#fff", size: D.rand(2, 4.5), type,
    });
  }
  function updateFx(dt) {
    fx.texts.forEach((t) => { t.life -= dt; t.y -= 16 * dt; });
    fx.texts = fx.texts.filter((t) => t.life > 0);
    fx.parts.forEach((p) => {
      p.t += dt; p.life -= dt; p.x += p.vx * dt; p.y += p.vy * dt;
      if (p.type === "smoke") { p.vx += D.rand(-3, 3) * dt; p.vy -= 2 * dt; }
    });
    fx.parts = fx.parts.filter((p) => p.life > 0);
  }

  /* ── tile drawing ─────────────────────────── */
  function hash2(x, y) { const n = Math.sin(x * 127.1 + y * 311.7) * 43758.5453; return n - Math.floor(n); }

  function drawGround(state, timeMs) {
    const [ox, oy] = w2s(0, 0);
    const gw = D.GRID.w * TILE, gh = D.GRID.h * TILE;
    // base ground
    ctx.fillStyle = "#243b2e";
    ctx.fillRect(ox, oy, gw * cam.zoom, gh * cam.zoom);
    // tile variation
    for (let ty = 0; ty < D.GRID.h; ty++) {
      for (let tx = 0; tx < D.GRID.w; tx++) {
        const [sx, sy] = w2s(tx * TILE, ty * TILE);
        const h = hash2(tx, ty);
        if (h < 0.5) {
          ctx.fillStyle = h < 0.25 ? "rgba(255,255,255,.028)" : "rgba(0,0,0,.03)";
          ctx.fillRect(sx, sy, TILE * cam.zoom, TILE * cam.zoom);
        }
        // grass tufts
        if (h > 0.86 && !state.isRock(tx, ty) && !state.roomAt(tx, ty)) {
          ctx.strokeStyle = "rgba(95,174,122,.5)"; ctx.lineWidth = 1;
          const gx = sx + (0.3 + h * 0.4) * TILE * cam.zoom, gy = sy + (0.3 + (h * 7 % 1) * 0.4) * TILE * cam.zoom;
          ctx.beginPath(); ctx.moveTo(gx, gy + 3 * cam.zoom);
          ctx.lineTo(gx - 2 * cam.zoom, gy); ctx.moveTo(gx, gy + 3 * cam.zoom);
          ctx.lineTo(gx + 2 * cam.zoom, gy);
          ctx.stroke();
        }
        // tile grid lines
        ctx.strokeStyle = "rgba(0,0,0,.14)"; ctx.lineWidth = 1;
        ctx.strokeRect(sx, sy, TILE * cam.zoom, TILE * cam.zoom);
      }
    }
    // rocks
    state.rocks.forEach(([rx, ry]) => {
      const [sx, sy] = w2s(rx * TILE, ry * TILE);
      const s = TILE * cam.zoom;
      ctx.fillStyle = "#5a6470";
      ctx.beginPath();
      ctx.moveTo(sx + s * 0.15, sy + s * 0.85);
      ctx.lineTo(sx + s * 0.1, sy + s * 0.55);
      ctx.lineTo(sx + s * 0.3, sy + s * 0.35);
      ctx.lineTo(sx + s * 0.55, sy + s * 0.28);
      ctx.lineTo(sx + s * 0.8, sy + s * 0.42);
      ctx.lineTo(sx + s * 0.9, sy + s * 0.62);
      ctx.lineTo(sx + s * 0.85, sy + s * 0.88);
      ctx.closePath(); ctx.fill();
      ctx.fillStyle = "#6d7886";
      ctx.beginPath(); ctx.arc(sx + s * 0.42, sy + s * 0.55, s * 0.16, 0, 7); ctx.fill();
      ctx.fillStyle = "#454e5a";
      ctx.beginPath(); ctx.arc(sx + s * 0.62, sy + s * 0.7, s * 0.1, 0, 7); ctx.fill();
    });
    // cliff below
    const cliffY = oy + gh * cam.zoom;
    ctx.fillStyle = "#2c3a34";
    ctx.fillRect(ox - 60, cliffY, (gw + 120) * cam.zoom, 60);
    ctx.fillStyle = "#1d2822";
    ctx.fillRect(ox - 60, cliffY + 30 * cam.zoom, (gw + 120) * cam.zoom, 60);
    ctx.strokeStyle = "rgba(216,181,106,.25)"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(ox - 60, cliffY); ctx.lineTo(ox + (gw + 120) * cam.zoom, cliffY); ctx.stroke();
    void timeMs;
  }

  /* ── roof helper (Chinese style) ─────────── */
  function roof(cx, yTop, w, ts, dark) {
    const half = w / 2, lift = w * 0.16;
    ctx.fillStyle = ts.roof;
    ctx.beginPath();
    ctx.moveTo(cx - half, yTop + lift);
    ctx.quadraticCurveTo(cx - half * 0.85, yTop + lift * 0.25, cx - half * 0.6, yTop);
    ctx.quadraticCurveTo(cx, yTop - lift * 0.75, cx + half * 0.6, yTop);
    ctx.quadraticCurveTo(cx + half * 0.85, yTop + lift * 0.25, cx + half, yTop + lift);
    ctx.quadraticCurveTo(cx, yTop + lift * 0.9, cx - half, yTop + lift);
    ctx.closePath(); ctx.fill();
    ctx.strokeStyle = ts.edge; ctx.lineWidth = 2; ctx.stroke();
    // ridge
    ctx.fillStyle = ts.edge;
    ctx.fillRect(cx - w * 0.14, yTop - lift * 0.7, w * 0.28, 4);
    void dark;
  }

  /* ── room drawing ─────────────────────────── */
  function drawRoom(state, room, timeMs, ui) {
    const def = D.ROOMS[room.type];
    const [w, h] = def.size;
    const [sx, sy] = w2s(room.x * TILE, room.y * TILE);
    const s = TILE * cam.zoom;
    const rw = w * s, rh = h * s;
    const ts = tierStyle(room.lvl);
    const cx = sx + rw / 2;

    // slab
    ctx.fillStyle = room.damaged ? "#3a2c28" : ts.slab;
    ctx.fillRect(sx + 2 * cam.zoom, sy + 2 * cam.zoom, rw - 4 * cam.zoom, rh - 4 * cam.zoom);
    ctx.strokeStyle = room.damaged ? "#7a4036" : ts.slabD;
    ctx.lineWidth = 2 * cam.zoom;
    ctx.strokeRect(sx + 2 * cam.zoom, sy + 2 * cam.zoom, rw - 4 * cam.zoom, rh - 4 * cam.zoom);
    // slab pattern
    ctx.strokeStyle = "rgba(0,0,0,.15)"; ctx.lineWidth = 1;
    for (let i = 1; i < w; i++) { ctx.beginPath(); ctx.moveTo(sx + i * s, sy + 2 * cam.zoom); ctx.lineTo(sx + i * s, sy + rh - 2 * cam.zoom); ctx.stroke(); }
    for (let j = 1; j < h; j++) { ctx.beginPath(); ctx.moveTo(sx + 2 * cam.zoom, sy + j * s); ctx.lineTo(sx + rw - 2 * cam.zoom, sy + j * s); ctx.stroke(); }

    const bx = sx + rw / 2; // building center x
    const by = sy + rh / 2;
    const bw = Math.min(rw, rh) * 0.62; // building width
    const bh = Math.min(rw, rh) * 0.66;

    ctx.save();
    // common wall
    const wall = () => {
      ctx.fillStyle = room.damaged ? "#5c4a3a" : ts.wall;
      ctx.fillRect(bx - bw / 2, by - bh * 0.15, bw, bh * 0.62);
      ctx.strokeStyle = "rgba(0,0,0,.3)"; ctx.lineWidth = 1.5;
      ctx.strokeRect(bx - bw / 2, by - bh * 0.15, bw, bh * 0.62);
      // door
      ctx.fillStyle = "#241c12";
      ctx.fillRect(bx - bw * 0.12, by + bh * 0.47 - bh * 0.22, bw * 0.24, bh * 0.22);
    };
    const smallRoof = (lift) => roof(bx, by - bh * 0.15 - lift, bw * 1.14, ts);

    switch (room.type) {
      case "hall": {
        wall();
        smallRoof(bh * 0.42);
        roof(bx, by - bh * 0.15 - bh * 0.72, bw * 1.5, ts);
        // plaque
        ctx.fillStyle = "#241c12";
        ctx.fillRect(bx - bw * 0.2, by - bh * 0.02, bw * 0.4, bh * 0.16);
        ctx.fillStyle = ts.accent;
        ctx.font = Math.round(bh * 0.13) + "px serif";
        ctx.textAlign = "center"; ctx.textBaseline = "middle";
        ctx.fillText(room.lvl >= 3 ? "青云殿" : "大殿", bx, by + bh * 0.065);
        // pillars
        ctx.fillStyle = ts.roof;
        ctx.fillRect(bx - bw * 0.42, by - bh * 0.1, 3 * cam.zoom, bh * 0.52);
        ctx.fillRect(bx + bw * 0.42 - 3 * cam.zoom, by - bh * 0.1, 3 * cam.zoom, bh * 0.52);
        break;
      }
      case "med": {
        wall();
        smallRoof(bh * 0.4);
        // incense burner + aura
        ctx.fillStyle = "#3a2f22";
        ctx.beginPath(); ctx.ellipse(bx, by + bh * 0.3, bw * 0.1, bh * 0.06, 0, 0, 7); ctx.fill();
        const pulse = 0.5 + Math.sin(timeMs / 700 + room.x) * 0.5;
        ctx.fillStyle = "rgba(95,201,162," + 0.35 * pulse + ")";
        ctx.beginPath(); ctx.arc(bx, by - bh * 0.05, bw * 0.3 * (0.8 + pulse * 0.4), 0, 7); ctx.fill();
        break;
      }
      case "garden": {
        // soil rows
        ctx.fillStyle = "#3a2c1c";
        for (let i = 0; i < 3; i++) {
          ctx.fillRect(sx + s * 0.12, sy + s * (0.14 + i * 0.3), rw * 0.76, s * 0.22);
        }
        ctx.strokeStyle = "#241c12"; ctx.lineWidth = 1;
        for (let i = 0; i < 3; i++) ctx.strokeRect(sx + s * 0.12, sy + s * (0.14 + i * 0.3), rw * 0.76, s * 0.22);
        // sprouts
        const grow = 0.5 + Math.sin(timeMs / 900 + room.x * 3) * 0.4;
        for (let i = 0; i < w * 2; i++) {
          const px = sx + s * (0.22 + i * 0.36);
          for (let j = 0; j < 3; j++) {
            const py = sy + s * (0.26 + j * 0.3);
            ctx.strokeStyle = "#5fae7a"; ctx.lineWidth = 1.5;
            ctx.beginPath();
            ctx.moveTo(px, py); ctx.lineTo(px - 2 * cam.zoom, py - 4 * cam.zoom * grow);
            ctx.moveTo(px, py); ctx.lineTo(px + 2 * cam.zoom, py - 4 * cam.zoom * grow);
            ctx.stroke();
            ctx.fillStyle = "#86d98f";
            ctx.fillRect(px - 2 * cam.zoom, py - 5 * cam.zoom * grow, 4 * cam.zoom, 3 * cam.zoom);
          }
        }
        break;
      }
      case "mine": {
        // cave mouth
        ctx.fillStyle = "#171a1e";
        ctx.beginPath(); ctx.arc(bx, by + bh * 0.1, bw * 0.5, Math.PI, 0); ctx.closePath(); ctx.fill();
        ctx.fillStyle = "#2a2e34";
        ctx.beginPath(); ctx.arc(bx, by + bh * 0.08, bw * 0.34, Math.PI, 0); ctx.closePath(); ctx.fill();
        // crystals
        const gl = 0.6 + Math.sin(timeMs / 500) * 0.4;
        [[-0.28, 0.2], [0.02, 0.3], [0.3, 0.16]].forEach(([dx, dy], i) => {
          ctx.fillStyle = i % 2 ? "rgba(109,179,232," + 0.5 + gl * 0.4 + ")" : "rgba(201,164,255," + 0.5 + gl * 0.4 + ")";
          ctx.beginPath();
          ctx.moveTo(bx + bw * dx, by + bh * 0.34); ctx.lineTo(bx + bw * dx - 4 * cam.zoom, by + bh * 0.02); ctx.lineTo(bx + bw * dx + 4 * cam.zoom, by + bh * 0.02);
          ctx.closePath(); ctx.fill();
        });
        // timber frame
        ctx.strokeStyle = "#6b4f35"; ctx.lineWidth = 3;
        ctx.beginPath(); ctx.moveTo(bx - bw * 0.5, by + bh * 0.02); ctx.lineTo(bx - bw * 0.5, by + bh * 0.36); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(bx + bw * 0.5, by + bh * 0.02); ctx.lineTo(bx + bw * 0.5, by + bh * 0.36); ctx.stroke();
        break;
      }
      case "train": {
        // courtyard floor
        ctx.fillStyle = "rgba(216,181,106,.07)";
        ctx.fillRect(sx + 4 * cam.zoom, sy + 4 * cam.zoom, rw - 8 * cam.zoom, rh - 8 * cam.zoom);
        // dummies
        for (let i = 0; i < 3; i++) {
          const dx = sx + rw * (0.25 + i * 0.25), dy = sy + rh * 0.38;
          ctx.fillStyle = "#8a6d3e";
          ctx.fillRect(dx - 3 * cam.zoom, dy - 8 * cam.zoom, 6 * cam.zoom, 16 * cam.zoom);
          ctx.fillStyle = "#c9b078";
          ctx.beginPath(); ctx.arc(dx, dy - 12 * cam.zoom, 5 * cam.zoom, 0, 7); ctx.fill();
        }
        // weapon rack
        ctx.strokeStyle = "#6b4f35"; ctx.lineWidth = 2;
        ctx.beginPath(); ctx.moveTo(sx + rw * 0.08, sy + rh * 0.16); ctx.lineTo(sx + rw * 0.08, sy + rh * 0.8); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(sx + rw * 0.2, sy + rh * 0.16); ctx.lineTo(sx + rw * 0.2, sy + rh * 0.8); ctx.stroke();
        for (let i = 0; i < 3; i++) {
          ctx.strokeStyle = "#cfd8e0"; ctx.lineWidth = 2;
          ctx.beginPath(); ctx.moveTo(sx + rw * (0.1 + i * 0.05), sy + rh * 0.16); ctx.lineTo(sx + rw * (0.1 + i * 0.05), sy + rh * 0.44); ctx.stroke();
        }
        break;
      }
      case "alch": {
        wall();
        smallRoof(bh * 0.38);
        // cauldron on top of wall
        ctx.fillStyle = "#241c12";
        ctx.beginPath(); ctx.arc(bx, by - bh * 0.3, bw * 0.22, Math.PI, 0); ctx.closePath(); ctx.fill();
        ctx.fillStyle = "#3a2f22";
        ctx.beginPath(); ctx.arc(bx, by - bh * 0.26, bw * 0.16, Math.PI, 0); ctx.closePath(); ctx.fill();
        const steam = Math.sin(timeMs / 400 + room.y) * 0.5 + 0.5;
        ctx.fillStyle = "rgba(154,168,156," + 0.3 + steam * 0.3 + ")";
        ctx.beginPath(); ctx.arc(bx - bw * 0.08, by - bh * 0.42 - steam * 3, 3 * cam.zoom, 0, 7); ctx.fill();
        ctx.beginPath(); ctx.arc(bx + bw * 0.09, by - bh * 0.48 - steam * 5, 2.5 * cam.zoom, 0, 7); ctx.fill();
        // window glow
        ctx.fillStyle = "rgba(240,120,60," + 0.3 + steam * 0.4 + ")";
        ctx.fillRect(bx - bw * 0.08, by + bh * 0.02, bw * 0.16, bh * 0.14);
        break;
      }
      case "forge": {
        wall();
        smallRoof(bh * 0.38);
        // furnace
        ctx.fillStyle = "#5c3a2a";
        ctx.fillRect(bx - bw * 0.18, by - bh * 0.28, bw * 0.36, bh * 0.34);
        const heat = 0.5 + Math.sin(timeMs / 350 + room.x * 7) * 0.5;
        ctx.fillStyle = "rgba(240,140,60," + 0.4 + heat * 0.5 + ")";
        ctx.fillRect(bx - bw * 0.12, by - bh * 0.2, bw * 0.24, bh * 0.18);
        // anvil
        ctx.fillStyle = "#3c444e";
        ctx.fillRect(bx - bw * 0.24, by + bh * 0.12, bw * 0.14, bh * 0.1);
        break;
      }
      case "talis": {
        wall();
        smallRoof(bh * 0.38);
        // hanging talismans
        for (let i = -1; i <= 1; i++) {
          const tx2 = bx + i * bw * 0.22;
          ctx.fillStyle = "#e8d070";
          ctx.fillRect(tx2 - 4 * cam.zoom, by - bh * 0.05, 8 * cam.zoom, 12 * cam.zoom);
          ctx.strokeStyle = "#c0382e"; ctx.lineWidth = 1.5;
          ctx.beginPath(); ctx.moveTo(tx2, by - bh * 0.02); ctx.lineTo(tx2, by + bh * 0.06); ctx.stroke();
          ctx.beginPath(); ctx.moveTo(tx2 - 2 * cam.zoom, by); ctx.lineTo(tx2 + 2 * cam.zoom, by - 2 * cam.zoom); ctx.stroke();
        }
        break;
      }
      case "lib": {
        wall();
        smallRoof(bh * 0.42);
        roof(bx, by - bh * 0.15 - bh * 0.7, bw * 1.42, ts);
        // scrolls in windows
        ctx.fillStyle = "rgba(240,220,160,.75)";
        ctx.fillRect(bx - bw * 0.16, by + bh * 0.02, bw * 0.1, bh * 0.16);
        ctx.fillRect(bx + bw * 0.06, by + bh * 0.02, bw * 0.1, bh * 0.16);
        break;
      }
      case "dorm": {
        wall();
        smallRoof(bh * 0.4);
        ctx.fillStyle = "#241c12";
        ctx.fillRect(bx - bw * 0.3, by + bh * 0.02, bw * 0.16, bh * 0.16);
        ctx.fillRect(bx + bw * 0.14, by + bh * 0.02, bw * 0.16, bh * 0.16);
        ctx.fillStyle = "rgba(240,200,120,.5)";
        ctx.fillRect(bx - bw * 0.28, by + bh * 0.045, bw * 0.12, bh * 0.1);
        ctx.fillRect(bx + bw * 0.16, by + bh * 0.045, bw * 0.12, bh * 0.1);
        break;
      }
      case "store": {
        wall();
        smallRoof(bh * 0.4);
        // sacks
        [[-0.24, 0.26], [0, 0.3], [0.24, 0.24]].forEach(([dx, dy]) => {
          ctx.fillStyle = "#8a6d3e";
          ctx.beginPath(); ctx.ellipse(bx + bw * dx, by + bh * dy, bw * 0.12, bh * 0.1, 0, 0, 7); ctx.fill();
          ctx.strokeStyle = "#5c451c"; ctx.lineWidth = 1.5;
          ctx.beginPath(); ctx.moveTo(bx + bw * dx - bw * 0.1, by + bh * dy - bh * 0.06); ctx.lineTo(bx + bw * dx + bw * 0.1, by + bh * dy - bh * 0.06); ctx.stroke();
        });
        break;
      }
      case "array": {
        // stone pillars + glowing circle
        const rot = timeMs / 2000;
        const r = Math.min(rw, rh) * 0.38;
        ctx.strokeStyle = "rgba(109,179,232,.5)"; ctx.lineWidth = 2;
        ctx.setLineDash([5 * cam.zoom, 4 * cam.zoom]);
        ctx.beginPath(); ctx.arc(cx, by, r, rot, rot + Math.PI * 1.5); ctx.stroke();
        ctx.setLineDash([]);
        for (let i = 0; i < 4; i++) {
          const a = rot * 0.6 + i * Math.PI / 2;
          const px = cx + Math.cos(a) * r, py = by + Math.sin(a) * r * 0.8;
          ctx.fillStyle = "#5a6470";
          ctx.fillRect(px - 4 * cam.zoom, py - 9 * cam.zoom, 8 * cam.zoom, 18 * cam.zoom);
          const gl = 0.5 + Math.sin(timeMs / 600 + i) * 0.5;
          ctx.fillStyle = "rgba(159,232,240," + 0.4 + gl * 0.5 + ")";
          ctx.beginPath(); ctx.arc(px, py - 12 * cam.zoom, 3.5 * cam.zoom, 0, 7); ctx.fill();
        }
        ctx.fillStyle = "rgba(109,179,232,.35)";
        ctx.beginPath(); ctx.arc(cx, by, 5 * cam.zoom, 0, 7); ctx.fill();
        break;
      }
    }
    ctx.restore();

    /* production / research progress bar */
    const showProgress = ["alch", "forge", "talis"].includes(room.type) && room.recipe ||
      (room.type === "lib" && state.techs.researching);
    if (showProgress) {
      let frac = 0, label = "";
      if (room.type === "lib" && state.techs.researching) {
        const t = D.TECHS.find((x) => x.key === state.techs.researching);
        frac = D.clamp(room.progress / t.time, 0, 1);
        label = "研 " + Math.round(frac * 100) + "%";
      } else if (room.type === "alch") {
        const rec = D.RECIPES.find((x) => x.key === room.recipe);
        if (rec) { frac = D.clamp(room.progress / rec.time, 0, 1); label = "炼 " + Math.round(frac * 100) + "%"; }
      } else if (room.type === "forge") {
        const bp = D.BLUEPRINTS.find((x) => x.key === room.recipe);
        if (bp) { frac = D.clamp(room.progress / bp.time, 0, 1); label = "锻 " + Math.round(frac * 100) + "%"; }
      } else if (room.type === "talis") {
        const tp = D.TALISMANS.find((x) => x.key === room.recipe);
        if (tp) { frac = D.clamp(room.progress / tp.time, 0, 1); label = "绘 " + Math.round(frac * 100) + "%"; }
      }
      ctx.fillStyle = "rgba(0,0,0,.55)";
      ctx.fillRect(sx + 4 * cam.zoom, sy + rh - 9 * cam.zoom, rw - 8 * cam.zoom, 5 * cam.zoom);
      ctx.fillStyle = "#f0c060";
      ctx.fillRect(sx + 4 * cam.zoom, sy + rh - 9 * cam.zoom, (rw - 8 * cam.zoom) * frac, 5 * cam.zoom);
      ctx.fillStyle = "#fff"; ctx.font = Math.max(8, 9 * cam.zoom) + "px serif"; ctx.textAlign = "center";
      ctx.fillText(label, cx, sy + rh - 12 * cam.zoom);
    }

    /* level pips */
    if (room.lvl > 1) {
      for (let i = 1; i < room.lvl; i++) {
        ctx.fillStyle = "#f0c060";
        ctx.beginPath(); ctx.arc(sx + 7 * cam.zoom + i * 6 * cam.zoom, sy + rh - 8 * cam.zoom, 2.2 * cam.zoom, 0, 7); ctx.fill();
      }
    }

    /* status badges */
    let badge = null;
    if (room.damaged) badge = { txt: "损", color: "#d9685e", icon: "🔧" };
    const roomAssignCount = room.assigned.length;
    if (!badge && (room.type === "med" || room.type === "alch" || room.type === "forge" || room.type === "talis" || room.type === "garden" || room.type === "mine")) {
      const slots = state.roomSlots(room) || 0;
      if (slots > 0 && roomAssignCount === 0) badge = { txt: "虚", color: "#9db3a6" };
    }
    if (badge) {
      ctx.fillStyle = "rgba(10,17,13,.85)";
      ctx.fillRect(sx + 2, sy + 2, 18 * cam.zoom, 16 * cam.zoom);
      ctx.fillStyle = badge.color;
      ctx.font = Math.max(9, 11 * cam.zoom) + "px serif";
      ctx.textAlign = "center"; ctx.textBaseline = "middle";
      ctx.fillText(badge.txt, sx + 2 + 9 * cam.zoom, sy + 2 + 8 * cam.zoom);
      ctx.textBaseline = "alphabetic";
    }

    /* selection */
    if (ui && ui.selectedRoomId === room.id) {
      ctx.strokeStyle = "#f0c060"; ctx.lineWidth = 2.5;
      ctx.strokeRect(sx - 2, sy - 2, rw + 4, rh + 4);
      ctx.strokeStyle = "rgba(240,192,96,.25)"; ctx.lineWidth = 6;
      ctx.strokeRect(sx - 2, sy - 2, rw + 4, rh + 4);
    }
  }

  /* ── disciple sprites ─────────────────────── */
  function drawDisciple(state, d, wx, wy, timeMs, opts) {
    opts = opts || {};
    const root = D.ROOTS[d.root];
    const meditating = opts.meditating;
    const s = cam.zoom;
    let bob = 0;
    if (opts.moving) bob = Math.sin(opts.phase * 10) * 1.6;
    ctx.save();
    // shadow
    ctx.fillStyle = "rgba(0,0,0,.35)";
    ctx.beginPath(); ctx.ellipse(wx, wy + 13 * s, 7 * s, 2.6 * s, 0, 0, 7); ctx.fill();
    if (meditating || opts.resting) {
      // sitting figure
      ctx.fillStyle = root.robe;
      ctx.beginPath(); ctx.ellipse(wx, wy - 1 * s, 8 * s, 5.5 * s, 0, 0, 7); ctx.fill();
      ctx.fillStyle = "#e8c8a8";
      ctx.beginPath(); ctx.arc(wx, wy - 9 * s, 5 * s, 0, 7); ctx.fill();
      ctx.fillStyle = "#2a2018";
      ctx.beginPath(); ctx.arc(wx, wy - 10.5 * s, 5 * s, Math.PI * 0.9, Math.PI * 2.1); ctx.fill();
      if (opts.resting) {
        // bandage + zzz
        ctx.fillStyle = "#e8e2d8";
        ctx.fillRect(wx - 4 * s, wy - 13 * s, 8 * s, 3 * s);
        ctx.fillStyle = "rgba(230,224,207,.7)";
        ctx.font = Math.max(7, 8 * s) + "px serif"; ctx.textAlign = "left";
        const bob2 = Math.sin(timeMs / 500) * 2;
        ctx.fillText("z", wx + 5 * s, wy - 15 * s + bob2 * s);
        ctx.fillText("z", wx + 9 * s, wy - 19 * s + bob2 * 1.4 * s);
      }
      if (meditating) {
        // aura
        const pulse = 0.5 + Math.sin(timeMs / 600 + (d.id || "x").length) * 0.5;
        ctx.strokeStyle = "rgba(95,201,162," + (0.25 + pulse * 0.35) + ")";
        ctx.lineWidth = 1.5;
        ctx.beginPath(); ctx.arc(wx, wy - 2 * s, (9 + pulse * 3) * s, 0, 7); ctx.stroke();
        ctx.strokeStyle = "rgba(95,201,162," + (0.5 + pulse * 0.4) + ")";
        ctx.lineWidth = 1;
        ctx.beginPath(); ctx.arc(wx, wy - 2 * s, (5 + pulse * 2) * s, 0, 7); ctx.stroke();
      }
    } else {
      const py = wy + bob * s;
      // robe
      ctx.fillStyle = root.robe;
      ctx.beginPath();
      ctx.moveTo(wx - 6 * s, py + 12 * s);
      ctx.lineTo(wx - 8 * s, py - 4 * s);
      ctx.lineTo(wx + 8 * s, py - 4 * s);
      ctx.lineTo(wx + 6 * s, py + 12 * s);
      ctx.closePath(); ctx.fill();
      ctx.fillStyle = root.robeD;
      ctx.fillRect(wx - 6 * s, py + 1 * s, 12 * s, 3 * s);
      // legs hint (walk)
      if (opts.moving) {
        ctx.strokeStyle = root.robeD; ctx.lineWidth = 2.4 * s;
        ctx.beginPath();
        ctx.moveTo(wx - 3 * s, py + 12 * s); ctx.lineTo(wx - 3 * s + Math.sin(opts.phase * 10) * 3 * s, py + 15 * s);
        ctx.moveTo(wx + 3 * s, py + 12 * s); ctx.lineTo(wx + 3 * s - Math.sin(opts.phase * 10) * 3 * s, py + 15 * s);
        ctx.stroke();
      }
      // head
      ctx.fillStyle = "#e8c8a8";
      ctx.beginPath(); ctx.arc(wx, py - 9 * s, 5.5 * s, 0, 7); ctx.fill();
      ctx.fillStyle = "#2a2018";
      ctx.beginPath(); ctx.arc(wx, py - 10.5 * s, 5.5 * s, Math.PI * 0.95, Math.PI * 2.05); ctx.fill();
      if (d.gender === "f") {
        ctx.strokeStyle = "#2a2018"; ctx.lineWidth = 1.4;
        ctx.beginPath(); ctx.moveTo(wx - 4 * s, py - 13 * s); ctx.lineTo(wx - 5 * s, py - 16 * s); ctx.stroke();
      }
      // headband
      ctx.strokeStyle = "#d8b56a"; ctx.lineWidth = 1.6;
      ctx.beginPath(); ctx.moveTo(wx - 5 * s, py - 10.5 * s); ctx.lineTo(wx + 5 * s, py - 10.5 * s); ctx.stroke();
      // wounded
      if (state.wounded(d)) {
        ctx.fillStyle = "rgba(217,104,94,.85)";
        ctx.font = Math.max(8, 9 * s) + "px serif"; ctx.textAlign = "center";
        ctx.fillText("伤", wx, py - 14 * s);
      }
      // realm glow for high tiers
      if (d.step >= 12) {
        ctx.strokeStyle = "rgba(201,164,255,.5)"; ctx.lineWidth = 1.5;
        ctx.beginPath(); ctx.arc(wx, py, 11 * s, 0, 7); ctx.stroke();
      } else if (d.step >= 9) {
        ctx.strokeStyle = "rgba(216,181,106,.4)"; ctx.lineWidth = 1.5;
        ctx.beginPath(); ctx.arc(wx, py, 11 * s, 0, 7); ctx.stroke();
      }
      // name tag
      if (cam.zoom >= 1.05 || opts.forceName) {
        ctx.fillStyle = "rgba(10,17,13,.8)";
        const tw = ctx.measureText(d.name).width + 8 * s;
        ctx.fillRect(wx - tw / 2, wy + 17 * s, tw, 11 * s);
        ctx.fillStyle = "#f3ead2";
        ctx.font = Math.max(8, 9.5 * s) + "px serif"; ctx.textAlign = "center";
        ctx.fillText(d.name, wx, wy + 25.5 * s);
      }
    }
    ctx.restore();
  }

  /* ── wanderer update ──────────────────────── */
  function updateWander(state, dt) {
    const idle = state.disciples.filter((d) => !d.workRoom && !state.busy(d));
    idle.forEach((d) => {
      let w = wanderers[d.id];
      if (!w) { w = wanderers[d.id] = { x: D.rand(1, D.GRID.w - 1) * TILE, y: D.rand(1, D.GRID.h - 1) * TILE, tx: 0, ty: 0, wait: 0, phase: 0, moving: false }; w.tx = w.x; w.ty = w.y; }
      if (w.wait > 0) { w.wait -= dt; w.moving = false; return; }
      if (Math.abs(w.x - w.tx) < 2 && Math.abs(w.y - w.ty) < 2) {
        w.wait = D.rand(1.5, 4);
        w.moving = false;
        const tx = D.randi(1, D.GRID.w - 2), ty = D.randi(1, D.GRID.h - 2);
        if (!state.isRock(tx, ty) && !state.roomAt(tx, ty)) { w.tx = tx * TILE + TILE / 2; w.ty = ty * TILE + TILE / 2; }
      } else {
        const spd = 26;
        const dx = w.tx - w.x, dy = w.ty - w.y;
        const dist = Math.hypot(dx, dy) || 1;
        const step = Math.min(dist, spd * dt);
        w.x += dx / dist * step; w.y += dy / dist * step;
        w.phase += dt * 1.4;
        w.moving = true;
      }
    });
    // remove dead wanderers
    Object.keys(wanderers).forEach((id) => {
      if (!state.disciples.some((d) => d.id === id)) delete wanderers[id];
    });
  }

  /* ── build ghost ──────────────────────────── */
  function drawGhost(state, ui, timeMs) {
    if (!ui || !ui.buildGhost) return;
    const g = ui.buildGhost;
    const def = D.ROOMS[g.type];
    const [w, h] = def.size;
    const [sx, sy] = w2s(g.x * TILE, g.y * TILE);
    const ok = state.canPlace(g.type, g.x, g.y) === "ok";
    ctx.save();
    ctx.globalAlpha = 0.45;
    ctx.fillStyle = ok ? "rgba(95,201,162,.55)" : "rgba(217,104,94,.55)";
    ctx.fillRect(sx, sy, w * TILE * cam.zoom, h * TILE * cam.zoom);
    ctx.strokeStyle = ok ? "#5fc9a2" : "#d9685e";
    ctx.lineWidth = 2;
    ctx.setLineDash([6, 4]);
    ctx.strokeRect(sx, sy, w * TILE * cam.zoom, h * TILE * cam.zoom);
    ctx.setLineDash([]);
    // ghost icon
    ctx.globalAlpha = 0.8;
    ctx.font = Math.round(20 * cam.zoom) + "px serif";
    ctx.textAlign = "center"; ctx.textBaseline = "middle";
    ctx.fillText(def.icon, sx + w * TILE * cam.zoom / 2, sy + h * TILE * cam.zoom / 2);
    ctx.restore();
    void timeMs;
  }

  /* ── main draw ────────────────────────────── */
  function draw(state, ui, timeMs) {
    if (!ctx) return;
    ctx.clearRect(0, 0, W, H);
    // background outside grid
    ctx.fillStyle = "#0f1b16";
    ctx.fillRect(0, 0, W, H);
    drawGround(state, timeMs);
    // rooms (sorted: hall last on top)
    const rooms = state.rooms.slice().sort((a, b) => (a.type === "hall" ? 1 : 0) - (b.type === "hall" ? 1 : 0));
    rooms.forEach((r) => drawRoom(state, r, timeMs, ui));
    // ghost
    drawGhost(state, ui, timeMs);

    // disciples
    const assignedPos = {};
    state.rooms.forEach((r) => {
      r.assigned.forEach((id) => {
        const d = state.getDisciple(id);
        if (!d) return;
        const def = D.ROOMS[r.type];
        const doorX = (r.x + def.size[0] / 2) * TILE;
        const doorY = (r.y + def.size[1] - 0.35) * TILE;
        const idx = r.assigned.indexOf(id);
        const off = (idx - (r.assigned.length - 1) / 2) * 16;
        assignedPos[id] = { x: doorX + off, y: doorY, meditating: r.type === "med" };
      });
    });
    const hall = state.rooms.find((r) => r.type === "hall");
    state.disciples.forEach((d) => {
      if (d.mission) return; // away from the mountain
      if (assignedPos[d.id] && !state.wounded(d)) {
        const p = assignedPos[d.id];
        drawDisciple(state, d, p.x, p.y, timeMs, { meditating: p.meditating, forceName: true });
      } else if (state.wounded(d)) {
        // resting in front of the main hall, nursing wounds
        const hx = hall ? (hall.x + 1.5) * TILE : 8 * TILE;
        const hy = hall ? (hall.y + D.ROOMS.hall.size[1] - 0.4) * TILE : 6 * TILE;
        const off = state.disciples.filter((x) => state.wounded(x) && !x.mission).indexOf(d) * 20;
        drawDisciple(state, d, hx + off, hy, timeMs, { resting: true, forceName: true });
      } else if (!d.workRoom) {
        const w = wanderers[d.id];
        if (w) drawDisciple(state, d, w.x, w.y, timeMs, { moving: w.moving, phase: w.phase });
      }
    });

    // particles
    fx.parts.forEach((p) => {
      ctx.globalAlpha = D.clamp(p.life / 1.4, 0, 1);
      ctx.fillStyle = p.color;
      ctx.beginPath(); ctx.arc(p.x, p.y, p.size * (0.5 + p.t * 2) * cam.zoom, 0, 7); ctx.fill();
      ctx.globalAlpha = 1;
    });
    // floating texts
    fx.texts.forEach((t) => {
      ctx.globalAlpha = D.clamp(t.life / 1.4, 0, 1);
      ctx.fillStyle = t.color;
      ctx.font = (t.big ? "bold 15px " : "bold 12px ") + "serif";
      ctx.textAlign = "center";
      ctx.fillText(t.txt, t.x, t.y);
      ctx.globalAlpha = 1;
    });

    // hover tile
    if (ui && ui.hover) {
      const [hx, hy] = w2s(ui.hover.tx * TILE, ui.hover.ty * TILE);
      ctx.strokeStyle = "rgba(240,192,96,.5)";
      ctx.lineWidth = 1.5;
      ctx.strokeRect(hx, hy, TILE * cam.zoom, TILE * cam.zoom);
    }
    void timeMs;
  }

  D.Render = {
    init, resize, fitView, draw, updateWander, updateFx,
    tileAt, w2s, s2w, spawnText, spawnPart,
    get cam() { return cam; },
    get TILE() { return TILE; },
  };
})();
