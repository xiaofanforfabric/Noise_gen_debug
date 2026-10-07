package org.cn.xiaofan.noise;

/**
 * {@code maintainPrecision}（1.18+ 叫 {@code PerlinNoise.wrap}）的折叠模式。
 *
 * <p>参考 <a href="https://github.com/INF32768/UltimateScaler">UltimateScaler</a>
 * 的实现。这个方法决定「噪声坐标在多大距离上开始退化」，因此<b>直接决定边境之地的位置
 * 与形态</b>。
 *
 * <p>原版公式（{@code O = 2^25 = 3.3554432E7}）：
 * <pre>
 * return value - lfloor(value / O + 0.5) * O;
 * </pre>
 * 把坐标折叠回 {@code [-O/2, O/2)}，从而把溢出推到极远处。
 */
public enum FarLandsMode {

	/**
	 * 原版行为：把 {code value} 折叠回 {@code [-2^25/2, 2^25/2)}。
	 * 边境之地出现在约 12,550,821 格。
	 */
	DEFAULT,

	/** 保留 Beta 1.8 之前的算法（直接返回原值，不做折叠）。 */
	BETA,

	/**
	 * 新版算法 —— 对超大值先减去 {@code Long.MAX_VALUE}，避免 double 精度丢失。
	 * 这是 1.14.4+ 的行为，把边境之地推到约 1.8 septillion 格。
	 */
	RELEASE,

	/**
	 * <b>移除折叠</b> —— 不做任何 wrap，直接把原始坐标交给下游。
	 *
	 * <p>这是产生「天空网格」与分阶段地形退化的关键：坐标够大时，
	 * {@code floor(double)->int} 开始饱和，图案进入周期性重复。
	 */
	REMOVED,

	/**
	 * 自定义除数 —— 用 {@link #customDivisor} 替代 {@code 2^25}。
	 *
	 * <p><b>这是最有用的模式</b>：把除数调小（如 512），边境之地就会按比例
	 * 靠近原点，于是能在近处散步时直接观察到墙 / 网格 / 退化。
	 */
	CUSTOM;

/** 原版除数 {@code 2^25}。 */
        public static final double VANILLA_DIVISOR = 3.3554432E7D;

        /** 原版除数的一半 {@code 2^24}，用于 REMOVED / RELEASE 的偏移。 */
        public static final double HALF_DIVISOR = 1.6777216E7D;

        public static FarLandsMode next(FarLandsMode current) {
		FarLandsMode[] all = values();
		return all[(current.ordinal() + 1) % all.length];
	}
}
