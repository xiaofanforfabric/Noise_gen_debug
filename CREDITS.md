# 出处与致谢 / Credits

本项目的实现参考了以下开源工作。**请在使用/分发前阅读 §3 的许可证说明。**

---

## 1. 代码与算法来源

| 项目 | 作者 | 用于本项目的哪部分 | 许可 |
|---|---|---|---|
| [**FarLandsTraveler**](https://github.com/SmallmanSeries/FarLandsTraveler) | SmallmanSeries / Ki The Adventurer / INF32768 | ①`FringeCascade` 的 26 组噪声参数表（来自 `base_3d_noise_far_lands.json`）<br>②`FringeNoise.fold()` 的坐标折叠逻辑（来自 `BlendedNoiseCustomizable`）<br>③`MathUtil` 的 NaN 安全 `clamp` / `lerp`<br>④`SkyGridMixin` 的思路（`Infinity` 密度 + NaN 安全插值） | **LGPL-3.0** ⚠️ |
| [**UltimateScaler**](https://github.com/INF32768/UltimateScaler) | INF32768 | `RepositionState` / `Reposition`（坐标重定位）<br>`FarLandsMode` + `OctavePerlinNoiseSamplerMixin`（`maintainPrecision` 折叠模式） | MIT |
| [**geniiii/FarLands**](https://github.com/geniiii/FarLands) | geniiii | `mixin/border/*`（世界边界力场、空气墙、坐标限制） | MIT |
| [**FastNoiseLite**](https://github.com/Auburn/FastNoiseLite) | Jordan Peck (Auburn) | `LatticeNoiseProvider` 与 `OpenSimplex2Provider` 中的核：Value、ValueCubic、Perlin、2D Simplex、OpenSimplex2 | MIT |
| [**OpenSimplex2**](https://github.com/KdotJPG/OpenSimplex2) | KdotJPG | OpenSimplex 系算法的原始设计（经 FastNoiseLite 移植） | Public domain / 宽松 |

## 2. 文献与资料

- [Minecraft Wiki — Far Lands](https://minecraft.wiki/w/Far_Lands)
- [Minecraft Wiki — Far Lands (Java Edition)](https://minecraft.wiki/w/Far_Lands_(Java_Edition)) — **成因分析的主要依据**（`(int)` 饱和 / 171.103 units per block / selector noise）
- [Minecraft Wiki — Noise generator](https://minecraft.wiki/w/Noise_generator) — 1.18+ 密度函数 JSON 格式
- [Wikipedia — Simplex noise](https://en.wikipedia.org/wiki/Simplex_noise) — 偏斜、单纯形细分、核求和
- FastNoiseLite 的 README / wiki — 噪声类型清单、float/double 选项

## 3. ⚠️ 许可证注意事项（**开源前请务必阅读**）

本项目的原创部分以 **MIT** 发布。但其中有若干文件是**从 LGPL-3.0 项目 FarLandsTraveler 派生**的：

| 文件 | 派生方式 | 应遵循的许可 |
|---|---|---|
| `noise/FringeNoise.java` | `fold()` 是 `BlendedNoiseCustomizable.compute()` 中折叠逻辑的**逐行转写** | **LGPL-3.0** |
| `noise/MathUtil.java` | `clamp` / `lerp` 的 `Double.isInfinite` / `delta == 0` 特判来自其 `MathUtil` | **LGPL-3.0** |
| `noise/FringeCascade.java` | 由 `base_3d_noise_far_lands.json` 生成的参数表（数据） | **LGPL-3.0** ⚠️ |
| `mixin/noise/SkyGridMixin.java`<br>`mixin/noise/FringeCascadeMixin.java` | 思路移植自其 mixin | **LGPL-3.0** |

**三种可选处理方式：**

1. **整体改用 LGPL-3.0** —— 最简单，无冲突（但会传染下游）。
2. **双许可**：根 `LICENSE` 写 MIT，上述文件单独标注 LGPL-3.0，
   并在 README 中明确说明"本仓库含 LGPL-3.0 派生文件"。**（默认采用此方案）**
3. **重写这些部分** —— 去掉对 FLT 代码/数据的依赖（例如自己设计参数表与折叠），
   然后整个仓库可以纯 MIT。工作量最大。

> 📌 纯原创、可安全标 MIT 的部分：`noise/provider/*`（源自 MIT 的 FastNoiseLite）、
> `mixin/border/*`（源自 MIT 的 geniiii/FarLands）、`noise/Reposition*.java`、
> `FarLandsMode.java`（源自 MIT 的 UltimateScaler）、以及本项目新增的
> `NoiseGenRegistry` / `NoiseProvider` / 全部 GUI 与文档。
