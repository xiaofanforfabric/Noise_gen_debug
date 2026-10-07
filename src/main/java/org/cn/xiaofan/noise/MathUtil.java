/*
 * 许可例外：本文件派生自 FarLandsTraveler
 * (https://github.com/SmallmanSeries/FarLandsTraveler)，该项目以 LGPL-3.0 发布。
 * 因此本文件按 LGPL-3.0-only 发布，【不适用】本仓库根部的 MIT 条款。
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.cn.xiaofan.noise;

/**
 * 数值工具 —— 与 <a href="https://github.com/SmallmanSeries/FarLandsTraveler">FarLandsTraveler</a>
 * 的 {@code common/MathUtil.java} 等价。
 *
 * <h2>为什么需要它：天空网格的成因</h2>
 *
 * <p>天空网格不是「挖洞」，也不是「改折叠算法」，而是 <b>{@code Infinity} 密度
 * 经过插值后的自然结果</b>。
 *
 * <p>但原版的两个数值函数会破坏它：
 *
 * <pre>
 * MathHelper.lerp(0.0, 5.0, +Inf)
 *   = 5.0 + 0.0 * (+Inf - 5.0)
 *   = 0.0 * +Inf
 *   = NaN          ← 原版，直接毁掉
 *
 * MathHelper.clamp(+Inf, -1.0, 1.0) = 1.0   ← 原版，把 Inf 夹没了
 * </pre>
 *
 * <p>所以要让天空网格出现，必须：
 * <ul>
 *   <li><b>放行 {@code Infinity}</b> —— 它是有意义的「极端密度」信号，
 *       不是错误值（{@link #clamp}）</li>
 *   <li><b>对 {@code delta == 0} 特判</b> —— 避免 {@code 0 * Inf = NaN}（{@link #lerp}）</li>
 * </ul>
 *
 * <p>参考实现原文注释：「添加了对 delta == 0 的特殊处理，使得天空网格能够生成」。
 */
public final class MathUtil {

	private MathUtil() {
	}

	/**
	 * 类似 {@code MathHelper.clamp}，但<b>不限制无穷值</b>。
	 *
	 * <p>原版把 {@code +Inf} 夹成 {@code max}，于是「这里密度极大」这个信息丢失，
	 * 天空网格就不会出现。
	 */
	public static double clamp(double value, double min, double max) {
		if (Double.isInfinite(value)) {
			return value;
		}
		return value < min ? min : Math.min(value, max);
	}

	/**
	 * 类似 {@code MathHelper.lerp}，但对 {@code delta == 0} 特判。
	 *
	 * <p>原版 `start + delta * (end - start)`：当 {@code delta == 0} 且
	 * {@code end} 是 {@code ±Inf} 时得到 {@code 0 * Inf = NaN}。
	 *
	 * @param delta 插值权重
	 * @param start 起点（{@code delta == 0} 时的返回值）
	 * @param end   终点
	 */
	public static double lerp(double delta, double start, double end) {
		return delta == 0.0D ? start : start + delta * (end - start);
	}

	/** 三线性插值，逐层调用 NaN 安全的 {@link #lerp}。 */
	public static double lerp3(double delta1, double delta2, double delta3,
			double start1, double end1,
			double start2, double end2,
			double start3, double end3,
			double start4, double end4) {
		return lerp(delta3,
				lerp2(delta1, delta2, start1, end1, start2, end2),
				lerp2(delta1, delta2, start3, end3, start4, end4));
	}

	/** 双线性插值，逐层调用 NaN 安全的 {@link #lerp}。 */
	public static double lerp2(double delta1, double delta2,
			double start1, double end1,
			double start2, double end2) {
		return lerp(delta2, lerp(delta1, start1, end1), lerp(delta1, start2, end2));
	}

	/**
	 * 类似 {@code MathHelper.clampedLerp}，但对 {@code delta == 0} 特判。
	 *
	 * <p><b>注意参数顺序</b>：1.16.5 的 {@code MathHelper.clampedLerp}
	 * 是 {@code (start, end, delta)} —— 与前两个参数顺序<b>不同</b>，
	 * 照抄 {@link #lerp} 的签名会写错。
	 *
	 * <p>原版：{@code delta < 0 -> start; delta > 1 -> end; else lerp(delta,start,end)}。
	 */
	public static double clampedLerp(double start, double end, double delta) {
		if (delta < 0.0D) {
			return start;
		}
		if (delta > 1.0D) {
			return end;
		}
		return lerp(delta, start, end);
	}
}
