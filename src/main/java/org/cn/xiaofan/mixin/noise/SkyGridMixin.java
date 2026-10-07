/*
 * 许可例外：本文件派生自 FarLandsTraveler
 * (https://github.com/SmallmanSeries/FarLandsTraveler)，该项目以 LGPL-3.0 发布。
 * 因此本文件按 LGPL-3.0-only 发布，【不适用】本仓库根部的 MIT 条款。
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.cn.xiaofan.mixin.noise;

import org.cn.xiaofan.client.SkyGridState;
import org.cn.xiaofan.noise.MathUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

/**
 * 天空网格 —— 通过放行 {@code Infinity} 密度 + NaN 安全插值来生成。
 *
 * <h2>机制</h2>
 *
 * <p>移植自 <a href="https://github.com/SmallmanSeries/FarLandsTraveler">FarLandsTraveler</a>
 * 的 {@code MixinNoiseInterpolator} + {@code MixinDensityFunctionsClamp}
 * （作者 SmallmanSeries / INF32768 / OslorasKi）。
 *
 * <p>核心认识：<b>天空网格不是「挖洞」，也不是「改噪声折叠算法」</b>，
 * 而是 <b>{@code Infinity} 密度</b>经过插值后的自然产物。
 *
 * <p>但原版两个函数会把它毁掉：
 *
 * <pre>
 * MathHelper.lerp(0.0, 5.0, +Inf) = 5.0 + 0.0 * (Inf - 5.0) = 0 * Inf = NaN
 * MathHelper.clamp(+Inf, -1.0, 1.0) = 1.0        // Inf 被夹没了
 * </pre>
 *
 * <p>所以要做两件事：
 * <ol>
 *   <li>{@link MathUtil#clamp} —— 放行 {@code ±Inf}</li>
 *   <li>{@link MathUtil#lerp} —— {@code delta == 0} 直接返回 {@code start}，避开 {@code 0*Inf}</li>
 * </ol>
 *
 * <h2>注入点（已用 javap -c 逐条确认）</h2>
 *
 * <table border="1">
 *   <tr><th>方法</th><th>调用</th><th>次数</th><th>处理</th></tr>
 *   <tr><td>{@code populateNoise}</td><td>{@code MathHelper.lerp(DDD)D}</td>
 *       <td>7</td><td>本类 {@code @Redirect}（不带 ordinal → 全部命中）</td></tr>
 *   <tr><td>{@code populateNoise}</td><td>{@code MathHelper.clamp(DDD)D}</td>
 *       <td>1</td><td>本类 {@code @Redirect}</td></tr>
 *   <tr><td>{@code sampleHeightmap}</td><td>{@code MathHelper.lerp3(...)D}</td>
 *       <td>1</td><td>本类 {@code @Redirect}</td></tr>
 *   <tr><td>{@code sampleNoise}</td><td>{@code MathHelper.clampedLerp(DDD)D}</td>
 *       <td>1</td><td><b>不在本类</b> —— 已被 {@code SampleNoiseLayerModeMixin}
 *           {@code @Redirect}，那里合并处理（同一处不能有两个 {@code @Redirect}）</td></tr>
 * </table>
 *
 * <h2>为什么 heightmap 也要改</h2>
 *
 * <p>{@code sampleHeightmap} 决定「高度图」（刷怪、寻路等都读它）。
 * 若只改 {@code populateNoise}（填方块），高度图会和真实地形不一致 ——
 * 表现为「地面在脚下但高度图说是空气」。所以两处必须同时改。
 */
@Mixin(NoiseChunkGenerator.class)
public class SkyGridMixin {

	/**
	 * 放行无穷值的 {@code clamp}。
	 *
	 * <p>原版 {@code MathHelper.clamp(an / 200.0D, -1.0D, 1.0D)}
	 * 把 {@code ±Inf} 夹进 {@code [-1, 1]}，天空网格的原料就没了。
	 */
	@Redirect(
			method = "populateNoise",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/util/math/MathHelper;clamp(DDD)D"))
	private static double noise_gen_debug$passInfinityThroughClamp(double value,
			double min, double max) {
		if (SkyGridState.isEnabled() && SkyGridState.isForceClamp()) {
			return MathUtil.clamp(value, min, max);
		}
		return MathHelper.clamp(value, min, max);
	}

	/**
	 * NaN 安全的 {@code lerp}（{@code populateNoise} 里的 7 处插值）。
	 *
	 * <p>不带 {@code ordinal} → 命中该方法内<b>全部</b> 7 处调用。
	 */
	@Redirect(
			method = "populateNoise",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/util/math/MathHelper;lerp(DDD)D"))
	private static double noise_gen_debug$safeLerpPopulate(double delta,
			double start, double end) {
		if (SkyGridState.isEnabled()) {
			return MathUtil.lerp(delta, start, end);
		}
		return MathHelper.lerp(delta, start, end);
	}

	/**
	 * NaN 安全的 {@code lerp3}（{@code sampleHeightmap} 里的唯一一处）。
	 *
	 * <p>保证高度图与真实地形一致。
	 */
	@Redirect(
			method = "sampleHeightmap",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/util/math/MathHelper;lerp3(DDDDDDDDDDD)D"))
	private static double noise_gen_debug$safeLerp3Heightmap(double d1, double d2,
			double d3, double s1, double e1, double s2, double e2, double s3,
			double e3, double s4, double e4) {
		if (SkyGridState.isEnabled()) {
			return MathUtil.lerp3(d1, d2, d3, s1, e1, s2, e2, s3, e3, s4, e4);
		}
		return MathHelper.lerp3(d1, d2, d3, s1, e1, s2, e2, s3, e3, s4, e4);
	}
}
