package org.cn.xiaofan.mixin.noise;

import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.cn.xiaofan.client.FloatDegrade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 按轴把 {@code maintainPrecision} 的结果降级为 32 位 float。
 *
 * <p>{@code NoiseChunkGenerator.sampleNoise} 里有三次
 * {@code OctavePerlinNoiseSampler.maintainPrecision} 调用，
 * 分别对应 X / Z / Y 轴（javap 确认的调用顺序）。
 *
 * <p>用三个 {@code @Redirect} 加 {@code ordinal} 精确区分，
 * 每个都只在对应轴开关打开时降级：
 * <pre>
 * (double)(float) value
 * </pre>
 *
 * <p>这样就能做到「只让 X 轴溢出」「X+Z 同时溢出」等组合，
 * 用来观察 Wiki 描述的分段退化链（墙 → 梳状 → 消失 → 实线 → 虚无）。
 */
@Mixin(NoiseChunkGenerator.class)
public class SampleNoiseAxisFloatMixin {

	/** X 轴（sampleNoise 内第 1 次 maintainPrecision）。 */
	@Redirect(method = "sampleNoise",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/util/math/noise/OctavePerlinNoiseSampler;maintainPrecision(D)D",
					ordinal = 0))
	private static double noise_gen_debug$degradeX(double value) {
		return FloatDegrade.apply(value, 0);
	}

	/** Z 轴（第 2 次）。 */
	@Redirect(method = "sampleNoise",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/util/math/noise/OctavePerlinNoiseSampler;maintainPrecision(D)D",
					ordinal = 1))
	private static double noise_gen_debug$degradeZ(double value) {
		return FloatDegrade.apply(value, 1);
	}

	/** Y 轴（第 3 次）。 */
	@Redirect(method = "sampleNoise",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/util/math/noise/OctavePerlinNoiseSampler;maintainPrecision(D)D",
					ordinal = 2))
	private static double noise_gen_debug$degradeY(double value) {
		return FloatDegrade.apply(value, 2);
	}
}
