package org.cn.xiaofan.noise;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 软件大数坐标变换（Worldgen Reposition）。
 *
 * <h2>来源</h2>
 *
 * <p>照搬 <a href="https://github.com/INF32768/UltimateScaler">UltimateScaler</a>
 * 的 {@code Util.RepositionBigDecimal}（MIT）：
 *
 * <pre>
 * newPos = pos * scale + offset
 * </pre>
 *
 * <h2>为什么用 {@link BigDecimal} 而不是 {@code double}</h2>
 *
 * <p>{@code double} 的尾数只有 53 位，能精确表示的整数上限是 {@code 2^53}
 * （≈ 9.007e15）。超过之后相邻整数无法区分：
 *
 * <pre>
 * 1e15      -> v+1 != v   可区分
 * 9.007e15  -> v+1 == v   开始不可区分
 * 1e18      -> v+1 == v
 * 1e300     -> v+1 == v
 * </pre>
 *
 * <p>也就是说用 {@code double} 时，{@code offset} 输 {@code 1e18}
 * 和 {@code 1e18 + 1} 会得到<b>同一个采样点</b> —— 精度在运算前就丢了。
 * {@link BigDecimal} 没有这个限制，运算过程完全精确。
 *
 * <h2>但终点仍是 {@code double}</h2>
 *
 * <p>噪声采样器 {@code PerlinNoiseSampler.sample(double,double,double,...)}
 * <b>只有 {@code double} 重载</b>（javap 确认），没有 {@code BigDecimal} 版本。
 * 所以无论中间算得多精确，最后必须 {@link BigDecimal#doubleValue()}。
 *
 * <p>UltimateScaler 也是这么做的 —— 它所有 offset mixin 最后都写
 * {@code RepositionBigDecimal(...).doubleValue()}。
 * 唯一的例外是 {@code MixinEndIslandDensityFunction}，那里需要
 * <b>精确的整数除法取模</b>（{@code toBigInteger().divide(...).remainder(...)}），
 * 那是 {@code double} 根本算不出来的。
 *
 * <h2>结论：软件大数买到了什么</h2>
 *
 * <table border="1">
 *   <tr><th>环节</th><th>类型</th><th>精度</th></tr>
 *   <tr><td>输入 / 运算</td><td>{@link BigDecimal}</td><td>无限</td></tr>
 *   <tr><td>交给采样器</td><td>{@code double}</td><td>2^53</td></tr>
 * </table>
 *
 * <p>所以 {@code scale} 可以开到 {@code 1e300} 而中间不丢精度，
 * 但「让方块 x 去采 1e300 处噪声」这件事本身受限于 {@code double} ——
 * {@code 1e300} 附近所有方块仍会落到同一个点。
 */
public final class Reposition {

	private Reposition() {
	}

	/**
	 * 运算用精度。
	 *
	 * <p>34 位十进制（{@link MathContext#DECIMAL128} 的精度）远超
	 * {@code double} 的 17 位有效数字，足以保证转成 {@code double} 时不丢任何
	 * {@code double} 能表示的精度。
	 *
	 * <p>不用 {@code UNLIMITED}：{@code 1e100 * 1e100} 之类会产生天文位数的中间结果，
	 * 既慢又无意义（最终反正要压回 {@code double}）。
	 */
	private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);

	/**
	 * 施加一次坐标变换：{@code pos * scale + offset}。
	 *
	 * @param pos    原始坐标
	 * @param scale  缩放（{@link BigDecimal}，可极大）
	 * @param offset 偏移（{@link BigDecimal}，可极大）
	 * @return 变换后的坐标，<b>已转成 {@code double}</b>（采样器的终点类型）
	 */
	public static double apply(double pos, BigDecimal scale, BigDecimal offset) {
		if (isIdentity(scale, offset)) {
			return pos;
		}
		return applyExact(pos, scale, offset).doubleValue();
	}

	/**
	 * 与 {@link #apply} 相同，但保留 {@link BigDecimal} 结果。
	 *
	 * <p>需要「精确的整数除法/取模」时用这个 —— 先算精确值，
	 * 再用 {@link BigDecimal#toBigInteger()} 做整数运算，
	 * 避免中间那一步 {@code double} 转换把精度毁掉。
	 */
	public static BigDecimal applyExact(double pos, BigDecimal scale, BigDecimal offset) {
		return BigDecimal.valueOf(pos).multiply(scale, MC).add(offset, MC);
	}

	/** 是否恒等变换（{@code scale == 1 && offset == 0}）。 */
	public static boolean isIdentity(BigDecimal scale, BigDecimal offset) {
		return scale.compareTo(BigDecimal.ONE) == 0
				&& offset.signum() == 0;
	}

	/**
	 * 解析用户输入的数值。
	 *
	 * <p>用 {@code BigDecimal(String)} 而非 {@code BigDecimal.valueOf(double)} ——
	 * 后者会先经过 {@code double}，把用户输入的精度先毁一半。
	 * 比如输入 {@code 1e30}，{@code valueOf(1e30)} 只能得到 {@code double} 近似值，
	 * 而 {@code new BigDecimal("1E30")} 是精确的 {@code 10^30}。
	 *
	 * @return 解析结果；{@code null} 表示输入非法
	 */
	public static BigDecimal parse(String text) {
		if (text == null) {
			return null;
		}
		String t = text.trim();
		if (t.isEmpty()) {
			return null;
		}
		try {
			// 接受 "1e30" / "1E30" / "1.5E+18" 等写法。
			return new BigDecimal(t, MC);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
