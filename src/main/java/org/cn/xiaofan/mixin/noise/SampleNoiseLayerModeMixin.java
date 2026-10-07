package org.cn.xiaofan.mixin.noise;

import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.cn.xiaofan.client.NoiseParamsState;
import org.cn.xiaofan.client.SkyGridState;
import org.cn.xiaofan.noise.MathUtil;
import org.cn.xiaofan.noise.NoiseParams.NoiseLayerMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 高/低噪声层选择开关。
 *
 * <p>{@code NoiseChunkGenerator.sampleNoise} 末尾只有一次混合：
 * <pre>
 * return MathHelper.clampedLerp(d / 512.0D, e / 512.0D, (f / 10.0D + 1.0D) / 2.0D);
 * </pre>
 * 其中：
 * <ul>
 *   <li>第一参数（{@code d/512}）来自 {@code lowerInterpolatedNoise} —— 基准层（「低噪声」）</li>
 *   <li>第二参数（{@code e/512}）来自 {@code upperInterpolatedNoise} —— 目标层（「高噪声」）</li>
 *   <li>第三参数是插值权重，由 {@code interpolationNoise} 决定</li>
 * </ul>
 *
 * <p>所以只要把这次 {@code clampedLerp} 的返回值按模式替换，就能「只用低噪声」
 * 或「只用高噪声」，而不必去动三层 Perlin 的累加逻辑。
 *
 * <p>经 {@code javap -c} 确认：{@code sampleNoise} 内 {@code clampedLerp} 只出现一次，
 * 因此不需要 {@code ordinal} 区分。
 */
@Mixin(NoiseChunkGenerator.class)
public class SampleNoiseLayerModeMixin {

	/**
	 * 合并处理两件事（同一处不能有两个 {@code @Redirect}）：
	 * <ol>
	 *   <li><b>天空网格</b>：换成 NaN 安全的 {@link MathUtil#clampedLerp}
	 *       —— 放行 {@code Infinity} 密度，且对 {@code delta == 0} 特判</li>
	 *   <li><b>噪声层选择</b>：按 {@link NoiseLayerMode} 恒取低/高层</li>
	 * </ol>
	 *
	 * <p>注意 1.16.5 的 {@code clampedLerp} 参数顺序是
	 * {@code (start, end, delta)} —— {@code delta} 在<b>最后</b>，
	 * 与 {@code lerp(delta, start, end)} 相反。
	 */
	@Redirect(
			method = "sampleNoise",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/util/math/MathHelper;clampedLerp(DDD)D"))
	private static double noise_gen_debug$selectLayer(double low, double high, double weight) {
		// --- 天空网格优先：NaN 安全插值 ---
		if (SkyGridState.isEnabled()) {
			return MathUtil.clampedLerp(low, high, weight);
		}

		// --- 噪声层选择 ---
		NoiseLayerMode mode = NoiseParamsState.get().noiseLayerMode;

		switch (mode) {
			case LOW_ONLY:
				// 恒取基准层（lowerInterpolatedNoise 的结果）
				return low;
			case HIGH_ONLY:
				// 恒取目标层（upperInterpolatedNoise 的结果）
				return high;
			case DEFAULT:
			default:
				return MathHelper.clampedLerp(low, high, weight);
		}
	}
}
