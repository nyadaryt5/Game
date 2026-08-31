# Galaxy Sandbox (native Android game)

> **New:** [`GalaxySandbox/`](GalaxySandbox/README.md) is a from-scratch **native Kotlin Android game** —
> a galaxy sandbox combining *Solar Smash* style planet destruction with a *WorldBox* style
> civilisation sandbox on each planet's surface. Custom SurfaceView render engine + Jetpack Compose UI,
> no WebView. See [GalaxySandbox/README.md](GalaxySandbox/README.md) for the full description and build steps.

---

# 仙门掌门 · Sect Master

> 一个受 *Pixel Starships* 启发的 **修真宗门经营游戏**（浏览器原型）。
> A *Pixel Starships*-inspired **xianxia sect management game**, playable in the browser.

Pixel Starships 的核心是「一格一格建造星舰 → 招募船员 → 装备/升级 → 自动战斗攻打他人」。本作把整条循环搬进了仙侠小说世界：

| Pixel Starships | 仙门掌门 |
|---|---|
| 星舰格子布局 | 山门网格：殿阁一座座盖起来 |
| 船员 | 弟子（灵根/悟性/境界/技艺） |
| 资源（矿物/燃气） | 灵石 / 灵草 / 矿石 / 灵丹 / 法器 / 符箓 |
| 房间（引擎/武器/护盾） | 练功房 / 灵药园 / 炼丹房 / 炼器坊 / 护山大阵… |
| 任务与星图 | 区域历练、秘境、攻伐敌宗 |
| AI vs AI 战斗 | 全自动演算战斗 + 动画回放 |

## 🎮 玩法

- **经营**：在 16×12 的山门网格上建造殿阁。大殿提供灵气与各类上限；灵气不足时产能下降。
- **育才**：接引弟子（灵根决定天赋与战斗风格：金/木/水/火/土 + 稀有雷/冰/风/天灵根），指派职司，
  修炼突破境界（炼气九层 → 筑基 → 金丹 → 元婴 → 化神 → 大乘 → 飞升）。大境界突破有失败风险——
  服用筑基丹/结金丹等可提升成功率。
- **生产**：灵药园产灵草 → 炼丹房炼丹药；灵石矿脉产灵石；炼器坊锻造法器（凡品~极品）；
  制符室绘制战斗符箓；藏经阁研究功法（聚灵阵、剑诀、丹道真解…）。
- **征伐**：派弟子出任务（采药/猎兽/护送/秘境），或攻打敌对宗门（黑风寨、赤霞宗、阴煞宗、血魔教、天邪宗）。
  战斗为全自动演算：灵根风格（杀伐/铁壁/回春/控场/疾风）、法器、符箓、护山大阵护盾都会影响胜负，
  并附完整战斗动画回放。
- **守山**：敌对宗门会定期来袭，全宗弟子自动迎战。战败则损失资源、殿阁受损。
- **掌门试炼**：内置 8 步新手任务链引导开荒；随机事件（灵脉发现、游方散修、宗门大比…）带来抉择。

## 🕹️ 操作

- 🖱️ 左键点击建筑/空地：选中管理 / 拖拽平移；滚轮缩放
- 🏯 建筑页选建筑 → 点击空地放置（右键/Esc 取消）
- ⏸ 空格暂停，`1`–`4` 切换倍速
- 进度自动保存（localStorage），议事页可重置存档

## 🚀 运行

纯静态页面，零依赖，任意静态服务器即可：

```bash
cd Game
python3 -m http.server 8080
# 打开 http://localhost:8080
```

## 📱 Android APK

`android/` 目录是一个原生 Android 壳（Java + WebView），把上面这套 HTML5 游戏
打包成可安装的 APK（沉浸式全屏、横屏、支持触屏拖拽/双指缩放、本地存档）。

游戏本体位于 `android/app/src/main/assets/www/`，由 `scripts/sync-android.sh`
从仓库根目录同步过去（改动游戏后记得再跑一次）。

**方式一 · GitHub Actions（无需本地环境）**：把仓库里的 `android/build-apk.yml`
复制为 `.github/workflows/build-apk.yml` 并推送后，推送到 `arena/01a021d3-game`
分支就会自动构建并上传 `sect-master-apk`，到 Actions 页面的该次运行里下载即可；
也可在 Actions 页手动 `Run workflow`。

**方式二 · 本地构建（Android Studio）**：用 Android Studio 打开 `android/`
目录，等它完成 Gradle 同步后，`Build → Build APK(s)`；或命令行：

```bash
cd android
gradle assembleRelease   # 产物在 app/build/outputs/apk/release/
```

> release 版默认用调试密钥签名，可直接安装；上架前请换成自己的 keystore。

## 🧪 测试

引擎（资源/生产/战斗/存档）与浏览器 UI 解耦，可用 Node 无头验证：

```bash
node tests/sim-test.js
```

## 📁 结构

```
index.html        页面骨架
styles.css        水墨青玉风 UI
js/data.js        数值数据：境界/灵根/殿阁/丹方/器谱/功法/地图/事件/任务
js/state.js       核心模拟：资源、生产、修炼、任务、攻防结算、存档
js/battle.js      自动战斗引擎（纯逻辑，可无头测试）+ 动画回放
js/render.js      画布渲染：山门、殿阁、弟子、粒子特效
js/ui.js          侧栏/弹窗/提示等界面层
js/main.js        主循环、输入、事件接线
```

## 🗺️ 路线图

- [x] 音效（WebAudio 合成，含静音开关）
- [x] 离线收益（离线归来按 50% 效率结算，上限 8 小时）
- [x] 氛围动画（流云、落花、昼夜晨昏光影、突破震屏、动态标题）
- [x] 触屏操作（拖拽平移 / 双指缩放 / 点按，适配安卓）
- [ ] 灵兽园（灵兽随行出战）、双修/道侣系统
- [ ] 跨宗门联盟与宗门战（真·PvP）
- [ ] 更多建筑皮肤与主题
