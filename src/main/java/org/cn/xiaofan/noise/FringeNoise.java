/*
 * 许可例外：本文件派生自 FarLandsTraveler
 * (https://github.com/SmallmanSeries/FarLandsTraveler)，该项目以 LGPL-3.0 发布。
 * 因此本文件按 LGPL-3.0-only 发布，【不适用】本仓库根部的 MIT 条款。
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.cn.xiaofan.noise;

import net.minecraft.util.math.MathHelper;

/**
 * 「边缘之地」级联用的两个纯函数：<b>循环折叠</b> 与 <b>折叠抑制</b>。
 *
 * <h2>来源</h2>
 *
 * <p>移植自 FarLandsTraveler
 * {@code common/worldgen/densityfunctions/BlendedNoiseCustomizable.java}。
 *
 * <h2>① 循环折叠 = 天空网格的来源</h2>
 *
 * <p>FLT 的 {@code compute} 在调用原版混合噪声之前，先把方块坐标按
 * {@code repeat_*} 参数折叠：
 *
 * <pre>
 * int sectionLength = repeatLength * repeatCount;
 * int x = Math.abs(blockX);
 * if (x &gt;= repeatStart) {
 *     int a = x - repeatStart;
 *     x = a % repeatLength + repeatStart + (a / sectionLength) * sectionLength;
 * }
 * x *= blockX &lt; 0 ? -1 : 1;
 * </pre>
 *
 * <p>{@code repeatLength = 16} ⇒ 噪声每 16 格重复一次 ⇒ <b>16 格周期的天空网格</b>。
 *
 * <h2>② 折叠抑制 = 「可溢出」</h2>
 *
 * <p>FLT 用 {@code @Redirect} 把 {@code PerlinNoise.wrap} 换成恒等函数，
 * 让坐标能溢出成边境之地。1.16.5 的对应物就是
 * {@code OctavePerlinNoiseSampler.maintainPrecision}，
 * 所以这里直接复刻一份原版公式并允许跳过 —— 而不去调用那个静态方法，
 * 免得被 {@code OctavePerlinNoiseSamplerMixin} 里的「坐标重定位」再次改写。
 */
public final class FringeNoise {

	/** 原版 {@code maintainPrecision} 的除数（2^25）。 */
	public static final double VANILLA_DIVISOR = 3.3554432E7D;

	private FringeNoise() {
	}

	/**
	 * 按 FLT 的 {@code repeat_*} 规则折叠一个方块坐标。
	 *
	 * @param blockCoord   方块坐标（可为负）
	 * @param repeatStart  循环起点；{@code <= 0} 表示不循环
	 * @param repeatLength 循环节长度；{@code <= 0} 表示不循环
	 * @param repeatCount  循环节数量；{@code <= 0} 表示不循环
	 * @param inclusive    {@code true} 用 {@code >=} 比较，{@code false} 用 {@code >}
	 * @return 折叠后的方块坐标
	 */
	public static int fold(int blockCoord, int repeatStart, int repeatLength,
			int repeatCount, boolean inclusive) {
		if (repeatStart <= 0 || repeatLength <= 0 || repeatCount <= 0) {
			return blockCoord;
		}
		final int sectionLength = repeatLength * repeatCount;
		int v = Math.abs(blockCoord);
		final boolean trigger = inclusive ? v >= repeatStart : v > repeatStart;
		if (trigger) {
			final int a = v - repeatStart;
			v = a % repeatLength + repeatStart + (a / sectionLength) * sectionLength;
		}
		return blockCoord < 0 ? -v : v;
	}

	/**
	 * 原版 {@code OctavePerlinNoiseSampler.maintainPrecision} 的复刻，但可跳过折叠。
	 *
	 * <p>原版：
	 * {@code value - (double)MathHelper.lfloor(value / 3.3554432E7 + 0.5) * 3.3554432E7}
	 *
	 * <p>{@code overflowable = true} 时原样返回，坐标便能在后续乘法里溢出，
	 * 从而产生边境之地那种极端噪声值。
	 *
	 * @param value        采样坐标
	 * @param overflowable 是否允许溢出
	 * @return 折叠后（或原样）的坐标
	 */
	public static double wrap(double value, boolean overflowable) {
		if (overflowable) {
			return value;
		}
		return value - (double) MathHelper.lfloor(value / VANILLA_DIVISOR + 0.5D)
				* VANILLA_DIVISOR;
	}
}
