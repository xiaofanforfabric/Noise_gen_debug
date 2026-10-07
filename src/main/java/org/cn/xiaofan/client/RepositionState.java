package org.cn.xiaofan.client;

/**
 * 坐标重定位（Worldgen Reposition）。
 *
 * <h2>来源</h2>
 *
 * <p>移植自 <a href="https://github.com/INF32768/UltimateScaler">UltimateScaler</a>
 * 的 {@code Util.RepositionDouble}：
 *
 * <pre>
 * newPos = pos * scale + offset
 * </pre>
 *
 * <h2>与 1.21 版的差异（硬约束，必须了解）</h2>
 *
 * <p>UltimateScaler 跑在 1.21，噪声坐标走 {@code DensityFunction} 的 {@code double}，
 * 所以 {@code scale} 能开到 {@code 1e18}。
 *
 * <p><b>1.16.5 不行。</b> 1.16.5 的坐标在 {@code NoiseChunkGenerator.sampleNoise}
 * 里是 {@code int}：
 *
 * <pre>
 * private double sampleNoise(int x, int y, int z, ...) {
 *     maintainPrecision((double) x * horizontalScale * g);   // ← x 先被放大，再转 double
 * }
 * </pre>
 *
 * <p>所有 octave 共享同一个 {@code x}，所以能送到 {@code maintainPrecision} 的
 * 最大坐标被钉死在：
 *
 * <pre>
 * x_max * horizontalScale * g_max
 *   = 2^31 * 0.25 * 1.0
 *   ≈ 5.37e8
 * </pre>
 *
 * <p>（{@code horizontalScale} 由 {@code xzScale} 决定；{@code g} 从 1 递减。
 * 调大 {@code xzScale} 能按比例抬高这个上限 —— 每放大 10 倍，上限也放大 10 倍。）
 *
 * <p>所以本模组的 {@code scale} 实际可用范围是
 * {@code [1e-9, 1e9]}，而不是 {@code 1e18}。<b>超过上限不会崩，只会把
 * 所有方块压到同一个采样点上</b> —— 地形会变成沿 Y 分层的超平坦。
 */
public final class RepositionState {

	private RepositionState() {
	}

	/**
	 * {@code scale} 的可用上限。
	 *
	 * <h2>为什么不是 1e9（上一版的错误）</h2>
	 *
	 * <p>上一版按「{@code sampleNoise} 的 {@code int} 参数上限 ÷ 分辨率」推算出
	 * 5.37e8，据此把 {@code scale} 卡在 1e9。那个推导<b>只对「改 int 参数」
	 * 那套实现成立</b>；改成在 {@code maintainPrecision} 的 {@code double}
	 * 入参上做变换之后，这个上限就不存在了。
	 *
	 * <h2>真正的上限来自折叠算法自身</h2>
	 *
	 * <p>{@code maintainPrecision} 用 {@code lfloor}（内部是
	 * {@code (double)(long) Math.floor(...)}）。当输入超过 {@code 2^63} 时
	 * {@code long} 饱和，折叠失效，方法退化成「原样返回」。
	 * 实测（见下）阈值约 <b>{@code 2^88 ≈ 3.09e26}</b>：
	 *
	 * <pre>
	 * 1e9   → -6.6e6    正常折叠
	 * 1e12  →  1.08e7   正常折叠
	 * 1e18  → -1.02e7   正常折叠
	 * 1e24  →  0        开始退化
	 * 1e30  →  9.99e29  折叠已失效（≈ 原值）
	 * 1e40+ →  原样返回  完全失效
	 * </pre>
	 *
	 * <p>所以「还想让折叠正常工作」的实用区间是 <b>{@code 1e9 ~ 1e20}</b>。
	 *
	 * <h2>上限的真正约束：Perlin 的 {@code NaN} 悬崖（实测 2026-10）</h2>
	 *
	 * <p>一度把上限放到 {@code 1e308}，理由是「天空网格要 Infinity 密度」。
	 * 但那是个<b>双重错误</b>：
	 *
	 * <ol>
	 *   <li>天空网格要的不是 {@code Infinity} 而是<b>极大有限值</b>；</li>
	 *   <li>{@code Infinity} 一旦进入 {@code PerlinNoiseSampler} 会立刻变成 {@code NaN}。
	 * </ol>
	 *
	 * <p>链路（{@code PerlinNoiseSampler.sample}）：
	 *
	 * <pre>
	 * x = Infinity
	 * d = x + originX            = Infinity
	 * i = (int)Math.floor(d)     = Integer.MAX_VALUE
	 * g = d - (double)i          = Infinity        ← 小数部分仍是 Inf
	 * m = perlinFade(g)          = Infinity        ← g^5
	 * lerp3(m, m, m, ...)        = NaN             ← 内部 Inf * 0
	 * </pre>
	 *
	 * <p>而 <b>FLT 的「安全 lerp」也救不了这一步</b> —— 它只处理
	 * {@code delta == 0}，处理不了 {@code Inf * 0}。实测（{@code /tmp/chain/SG.java}）：
	 *
	 * <pre>
	 * Perlin 输入       perlinFade          lerp3 输出
	 * 1e30              6.0e150  (有限)      -Inf   ★ 可用
	 * 1e100             +Inf                NaN    ✗ 死路
	 * 1e300             +Inf                NaN    ✗ 死路
	 * +Infinity         +Inf                NaN    ✗ 死路
	 * </pre>
	 *
	 * <p>由于 {@code perlinFade(t) ≈ t^5}，{@code NaN} 悬崖在
	 * {@code t ≈ 1.7976931348623157e308^(1/5) ≈ 4.5e61}。
	 * 这里的 {@code t} 是 {@code x * horizontalScale * g}，
	 * 代入 {@code horizontalScale ≈ 684}、{@code g = 1}：
	 *
	 * <pre>
	 * x ≈ 1000（原点附近）   ⇒ scale 上限 ≈ 6.6e55
	 * x ≈ 1.25e7（边境之地） ⇒ scale 上限 ≈ 5.2e51
	 * </pre>
	 *
	 * <p>取 <b>{@code 1e30}</b> 作为硬上限：既在「折叠已失效、极值能通过」
	 * 的区间内（{@code &gt; 2^88 ≈ 3.09e26}），又离 {@code NaN} 悬崖很远。
	 * 想要更大时可以改这个常量，代价自负。
	 */
	public static final double MAX_SCALE = 1.0E30D;

	/**
	 * {@code offset} 的上限。
	 *
	 * <p>offset 是加在「已缩放的采样坐标」上的，所以它和被放大后的量级同阶才有效。
	 * 同样放到 {@code 1e30}。
	 */
	public static final double MAX_OFFSET = 1.0E30D;

	/**
	 * {@code scale} 的下限。
	 *
	 * <p>不设死 0：{@code scale = 0} 会让所有坐标塌成一个点（等价于常数噪声）。
	 * 也不建议太小 —— {@code divisor} 很小时地形会变得极平坦，
	 * 这里只是防止意外输入 0 或负数导致无意义结果。
	 */
	public static final double MIN_SCALE = 1.0E-9D;

	private static boolean enabled;
	private static double[] scale = {1.0D, 1.0D, 1.0D};
	private static double[] offset = {0.0D, 0.0D, 0.0D};
	private static boolean affectY;

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean v) {
		enabled = v;
	}

	public static void toggleEnabled() {
		enabled = !enabled;
	}

	public static boolean isAffectY() {
		return affectY;
	}

	public static void setAffectY(boolean v) {
		affectY = v;
	}

	public static void toggleAffectY() {
		affectY = !affectY;
	}

	public static double getScale(int axis) {
		return valid(axis) ? scale[axis] : 1.0D;
	}

	public static double getOffset(int axis) {
		return valid(axis) ? offset[axis] : 0.0D;
	}

	/**
	 * 设置缩放。
	 *
	 * @return 是否被接受；{@code false} 表示超范围或非法，调用方应提示用户
	 */
	public static boolean setScale(int axis, double v) {
		if (!valid(axis) || !finite(v)) {
			return false;
		}
		if (v != 0.0D && (Math.abs(v) < MIN_SCALE || Math.abs(v) > MAX_SCALE)) {
			return false;
		}
		scale[axis] = v;
		return true;
	}

	/** 设置偏移。返回是否被接受。 */
	public static boolean setOffset(int axis, double v) {
		if (!valid(axis) || !finite(v) || Math.abs(v) > MAX_OFFSET) {
			return false;
		}
		offset[axis] = v;
		return true;
	}

	public static void setAll(double[] s, double[] o) {
		if (s != null && s.length == 3) {
			for (int i = 0; i < 3; i++) {
				setScale(i, s[i]);
			}
		}
		if (o != null && o.length == 3) {
			for (int i = 0; i < 3; i++) {
				setOffset(i, o[i]);
			}
		}
	}

	/**
	 * 判断「scale 已经大到让折叠失效」。
	 *
	 * <p>{@code maintainPrecision} 的 {@code lfloor} 在输入超过 {@code 2^63}
	 * 时饱和，实测折叠在 {@code 2^88 ≈ 3.09e26} 附近完全失效。
	 * 超过这个量级后噪声不再折叠 —— 地形会变成无意义的常数（分层超平坦或全空）。
	 */
	public static boolean isScaleSaturating() {
		final double foldingLimit = 3.0948500982134507E26D; // 2^88
		for (int i = 0; i < 3; i++) {
			if (Math.abs(scale[i]) > foldingLimit) {
				return true;
			}
		}
		return false;
	}

	public static boolean isModified() {
		for (int i = 0; i < 3; i++) {
			if (i == 1 && !affectY) {
				continue;
			}
			if (scale[i] != 1.0D || offset[i] != 0.0D) {
				return true;
			}
		}
		return false;
	}

	public static void reset() {
		enabled = false;
		affectY = false;
		scale = new double[] {1.0D, 1.0D, 1.0D};
		offset = new double[] {0.0D, 0.0D, 0.0D};
	}

	/** axis: 0=X, 1=Y, 2=Z。 */
	private static boolean valid(int axis) {
		return axis >= 0 && axis < 3;
	}

	private static boolean finite(double v) {
		return !Double.isNaN(v) && !Double.isInfinite(v);
	}
}
