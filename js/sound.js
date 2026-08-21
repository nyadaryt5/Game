/* ═══════════════════════════════════════════════════════
   仙门掌门 · Sect Master — SOUND (WebAudio SFX, zero assets)
   ═══════════════════════════════════════════════════════ */
(function () {
  "use strict";
  const D = (window.G = window.G || {});
  const MUTE_KEY = "sect-master-muted";

  let ctx = null;
  let muted = false;
  try { muted = localStorage.getItem(MUTE_KEY) === "1"; } catch (e) { /* ignore */ }

  function ensure() {
    if (ctx) {
      if (ctx.state === "suspended") { try { ctx.resume(); } catch (e) { } }
      return ctx;
    }
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    try { ctx = new AC(); } catch (e) { return null; }
    return ctx;
  }

  // single synthesized tone; slideTo → descending sweep
  function tone(freq, dur, type, vol, when, slideTo) {
    const c = ensure();
    if (!c) return;
    const t = c.currentTime + (when || 0);
    const o = c.createOscillator();
    const g = c.createGain();
    o.type = type || "sine";
    o.frequency.setValueAtTime(freq, t);
    if (slideTo) o.frequency.exponentialRampToValueAtTime(Math.max(30, slideTo), t + dur);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(vol || 0.1, t + 0.012);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(c.destination);
    o.start(t); o.stop(t + dur + 0.06);
  }

  const SFX = {
    click:      () => tone(700, 0.07, "square", 0.05),
    build:      () => { tone(210, 0.14, "triangle", 0.12); tone(330, 0.18, "triangle", 0.1, 0.09); },
    craft:      () => { tone(540, 0.1, "sine", 0.1); tone(800, 0.16, "sine", 0.1, 0.07); },
    upgrade:    () => { tone(440, 0.1, "triangle", 0.1); tone(660, 0.1, "triangle", 0.1, 0.08); tone(880, 0.18, "triangle", 0.1, 0.16); },
    breakthrough: () => { [523, 659, 784, 1046].forEach((f, i) => tone(f, 0.32, "sine", 0.12, i * 0.11)); },
    fail:       () => tone(200, 0.34, "sawtooth", 0.08, 0, 80),
    recruit:    () => { tone(392, 0.12, "triangle", 0.1); tone(523, 0.16, "triangle", 0.1, 0.09); },
    battle:     () => { tone(180, 0.2, "square", 0.08, 0, 95); tone(150, 0.26, "square", 0.07, 0.11, 75); },
    victory:    () => { [392, 523, 659, 784].forEach((f, i) => tone(f, 0.22, "triangle", 0.11, i * 0.09)); },
    defeat:     () => tone(170, 0.42, "sawtooth", 0.09, 0, 60),
    coin:       () => tone(880, 0.09, "sine", 0.08),
    levelup:    () => { tone(520, 0.09, "triangle", 0.08); tone(720, 0.12, "triangle", 0.08, 0.06); },
  };

  D.Sound = {
    play(name) { if (muted) return; try { (SFX[name] || SFX.click)(); } catch (e) { /* ignore */ } },
    init() { ensure(); },
    get muted() { return muted; },
    toggle() { this.setMuted(!muted); return muted; },
    setMuted(m) {
      muted = !!m;
      try { localStorage.setItem(MUTE_KEY, muted ? "1" : "0"); } catch (e) { /* ignore */ }
    },
    updateBtn() {
      const b = document.getElementById("sound-btn");
      if (b) b.textContent = muted ? "🔇" : "🔊";
    },
  };
})();
