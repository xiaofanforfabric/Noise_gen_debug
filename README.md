# noise_gen_debug

> 一个把 Minecraft 1.16.5 的地形噪声管线**完全拆开、逐层替换、逐层测量**的实验模组。

[English](#english) | **中文**

---

## 这是什么

这不是一个玩法模组，而是一台**地形噪声显微镜**。

它把 `NoiseChunkGenerator` 的各个关口（坐标 → 折叠 → 八度叠加 → 插值 → clamping → 方块选择）
全部暴露出可切换的开关，并内置了 **9 个可插拔的噪声生成器**，让你能在同一个世界、同一个种子下，
把每种噪声在**极远处的崩坏形态**直接摆在眼前对比。

做它的起因是一个很朴素的问题：

> **「边境之地到底是怎么来的？」**

网上被翻烂的答案大多是"Perlin 噪声精度不足"。但这个说法**既不准确也不完整**。
本模组的目标是把这件事**测清楚**——而且过程中确实推翻了不少流传的说法（包括我自己写错的）。

---

## 核心结论（实测得出，非推测）

### 1️⃣ 边境之地的 bug 本体是**一个字符**

```java
int    i         = (int) Math.floor(x);   // ← Java 规范：超出范围时【饱和】到 2^31−1
double remainder = x - i;                 // ← 但这一行【假设】i 是精确的
```

- `i` 在 `|x| ≥ 2³¹` 时**卡死不动**
- 而 `x` 继续增长 → **`remainder` 无界增长**
- → 喂进五次插值 `t³(6t²−15t+10)` → **$10^{11} \sim 10^{103}$ 的天文数字**
- → 下游 `clamp(an/200, −1, 1)` 把它压成 **±1**
- → **地形只读到一个符号** → 于是成为「墙」

**验证**：同一套噪声，只把 `(int)` 换成 `Math.floor`，爆炸**彻底消失**。

$$blockX_{overflow} = \frac{2^{31}}{684.412 / \underbrace{hres}_{4}} = \frac{2^{31}}{171.103} = 12{,}550{,}824$$

**⇒ `12,550,824` 就是这么来的**（不是"浮点精度不够"，是 **32 位整数饱和**）。

### 2️⃣ 折叠（`maintainPrecision`）是 **Mojang 的补丁**，不是成因

```java
return value - lfloor(value / 3.3554432E7 + 0.5) * 3.3554432E7;   // = modulo 2²⁵
```

它是**为了防止**上述溢出，把坐标折回 `[-2²⁴, 2²⁴)`，从而把边境之地推到 1.8 万亿亿格外。
本模组的 `FarLandsMode` 可以把这个补丁**关掉 / 改除数**，用来在近处观察。

### 3️⃣ 统一的溢出规律

> **溢出 = 任何「送进 `(int)` 强转、且随坐标无界增长」的量达到 `2³¹`。**

| 噪声族 | 被强转的量 | 边界形状 |
|---|---|---|
| 格子插值类（Value / Perlin / ValueCubic） | 单轴坐标 $n_x$ | 沿轴**直线**（`x = 12,550,824`） |
| OpenSimplex2 | **旋转后分量** $\dfrac{2(n_x+n_z)-n_y}{3}$ | **45° 直线**（`x+z = 18,826,300`） |

**⇒ 位置不同，机理完全相同。**

### 4️⃣ 远处不止「分层」一种结局 —— **实测有 6 种**

| 生成器 | 远处结局 | 机理 |
|---|---|---|
| Value / Perlin / ValueCubic | **水平分层** | 有界格点值 × 无界多项式 → 只剩符号，沿饱和轴不变 |
| Worley（距离） | **完全均匀** | 距离恒 ≥ 0 → 爆炸后恒为正 → 符号恒定 |
| Worley（细胞值） | **免疫 · 块状** | 返回**有界哈希值**（不含距离）→ 根本不爆 |
| 域变形 Perlin | **NaN · 虚空** | 爆炸值被加到坐标上 → `quintic(Inf)=Inf` → `Inf−Inf` |
| Simplex 2D | **免疫 · 柱状** | ①只用 x/z ⇒ 与 Y 无关；②**半径截断**把坏八度**清零**而非炸开 |
| OpenSimplex2 | **菱形溢出** | float 相消静默 → 再远则在旋转分量上撞 2³¹ → NaN/Inf |

**⇒ 「换噪声换不出新长相」这句话只对前半族成立。** 有界输出与半径截断这两条路，**能把结构原样保住**。

### 5️⃣ 角部的「反比例轮廓」有闭式解

两个轴都越界后，量级是**两个轴的余数相乘**：

$$|v| \;\propto\; \left(d_x \cdot d_z\right)^5 \qquad (d = |x| - 12{,}550{,}824)$$

实测 $log_{10}|v| - 5\log_{10}d_x - 5\log_{10}d_z = \text{const}$（六位有效数字恒定）。
**⇒ 等量级线就是 $d_x \cdot d_z = \text{const}$，即双曲线 / 反比例函数。**
幂次 5 来自插值权重 $t^5$（两个轴各贡献一个）。

---

## 功能一览

| # | 功能 | 说明 |
|---|---|---|
| 1 | **坐标重定位** | `pos' = pos × scale + offset`，把脚下坐标映射到任意采样坐标 |
| 2 | **maintainPrecision 折叠模式** | `DEFAULT / BETA / RELEASE / REMOVED / CUSTOM`（自定义除数可把边境之地按比例拉近） |
| 3 | **天空网格** | `Infinity` 密度 + NaN 安全插值 |
| 4 | **边缘之地级联** | 移植 FarLandsTraveler 的 26 个参数盒子 + `repeat` 折叠 |
| 5 | **坐标折叠（噪声层）** | 在饱和前把坐标折回 → 把「隧道」换成**周期可控的格阵**（16 格 = 天空网格） |
| 6 | **9 个可插拔噪声生成器** | 见上表；`NoiseProvider` 接口 + 注册表，加一个引擎 = 加一行 |
| 7 | **噪声层选择** | 默认 / 只用低噪声 / 只用高噪声 |
| 8 | **取消平滑器** | 把 4×8×4 插值权重归零 → 块状台阶 |
| 9 | **世界边界移除** | 世界边界力场、空气墙、坐标限制 |
| 10 | **噪声参数 HUD** | F3 里显示当前噪声路由与精度统计 |

---

## 安装与构建

```bash
git clone <this-repo>
cd noise_gen_debug
./gradlew build --no-daemon --console=plain
# 产物: build/libs/noise_gen_debug-1.0.0.jar
```

| | |
|---|---|
| Minecraft | **1.16.5** |
| 加载器 | Fabric Loader ≥ 0.19.5 |
| 依赖 | Fabric API 0.42.0+1.16 |
| Yarn | 1.16.5+build.10 |

> ⚠️ 源码使用 `options.release = 8`，**只能用 Java 8 语法**（不能用 `var` / `List.of` / records）。

## 使用

界面入口（**没有游戏内快捷键**）：

```
主菜单 → 单人游戏 → 创建新的世界
   └─ 左下角  [噪声参数...]
        └─ 左侧 y=106  [边境之地设置...]
             └─ ★ 距离现象 / 边境之地
```

在那一页里可以切换**噪声生成器**（循环 9 个）、**坐标折叠**、各种插值开关。
想看距离现象就把坐标重定位的 `scale` 拉大，或者直接 `tp` 到 **12,550,824 格**。

日志里搜 `[NGD-NoiseGen]` 可以看到「第几个八度失效了」。

---

## 出处与致谢

本项目站在这些工作的肩上（均为宽松许可，详见 [CREDITS.md](CREDITS.md)）：

| 项目 | 用途 | 许可 |
|---|---|---|
| [**FarLandsTraveler**](https://github.com/SmallmanSeries/FarLandsTraveler) — SmallmanSeries 等 | 边缘之地级联、天空网格、NaN 安全插值 | LGPL-3.0（**仅作参考，未复制代码**） |
| [**UltimateScaler**](https://github.com/INF32768/UltimateScaler) — INF32768 | 坐标重定位、`maintainPrecision` 折叠模式 | MIT |
| [**geniiii/FarLands**](https://github.com/geniiii/FarLands) | 世界边界移除 | MIT |
| [**FastNoiseLite**](https://github.com/Auburn/FastNoiseLite) — Auburn | Perlin / Value / ValueCubic / 2D Simplex / OpenSimplex2 的核 | MIT |
| [**OpenSimplex2**](https://github.com/KdotJPG/OpenSimplex2) — KdotJPG | OpenSimplex 系算法设计 | 宽松 |

**成因分析的主要文献**：
[Minecraft Wiki — Far Lands](https://minecraft.wiki/w/Far_Lands) ·
[Far Lands (Java Edition)](https://minecraft.wiki/w/Far_Lands_(Java_Edition)) ·
[Wikipedia — Simplex noise](https://en.wikipedia.org/wiki/Simplex_noise)

## 许可证

**MIT** — 见 [LICENSE](LICENSE)。

> ⚠️ **例外**：下面这 5 个文件派生自 **LGPL-3.0** 的 FarLandsTraveler，
> 因此按 **LGPL-3.0-only** 发布，**不适用** MIT 条款：
>
> `noise/FringeNoise.java` · `noise/MathUtil.java` · `noise/FringeCascade.java`
> · `mixin/noise/SkyGridMixin.java` · `mixin/noise/FringeCascadeMixin.java`
>
> 详见 [CREDITS.md](CREDITS.md) §3。

---

## English

**noise_gen_debug** is a terrain-noise *microscope* for Minecraft 1.16.5 (Fabric).
It exposes every stage of `NoiseChunkGenerator` as a toggle and ships
**9 pluggable noise generators**, so you can compare how each one collapses at
extreme coordinates — same world, same seed.

Key findings (all measured, not guessed):

1. **The Far Lands bug is literally one token**: `x - (int) Math.floor(x)`.
   The `(int)` **saturates** at 2³¹ while `x` keeps growing, so the remainder grows
   unboundedly and the quintic interpolant returns astronomical values that the
   downstream `clamp(±1)` reduces to a mere **sign** → walls.
   Hence `12,550,824 = 2³¹ / 171.103`.
2. **`maintainPrecision` is Mojang's *patch*, not the cause.**
3. **Unified rule**: overflow happens wherever an unbounded quantity fed into an
   `(int)` cast reaches 2³¹ — lattice kernels hit it on a single axis (straight
   line), OpenSimplex2 on its rotated component (`x+z = 18,826,300`, a 45° line).
4. **There is more than one far-lands outcome** — 6 distinct ones are implemented
   and measured: layered / uniform / immune-blocky / immune-columnar / NaN-void /
   diamond overflow.
5. The corner "inverse-proportional" contour is closed-form:
   `|v| ∝ (dx·dz)^5`, i.e. hyperbolic level sets.

Licensed under **MIT**. Third-party credits in [CREDITS.md](CREDITS.md).
