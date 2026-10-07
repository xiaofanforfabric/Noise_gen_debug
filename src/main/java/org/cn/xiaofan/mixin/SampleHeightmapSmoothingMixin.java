package org.cn.xiaofan.mixin;

import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 「取消平滑器」开关 —— 高度图侧。
 *
 * <p>{@code populateNoise} 负责填充方块，{@code sampleHeightmap} 负责高度图与
 * {@code getHeight}。两处各有一份**独立**的插值逻辑，必须同时改，否则
 * 地形和高度图会不一致（F3 显示的高度与实际地形对不上）。
 *
 * <p>{@code sampleHeightmap} 里只有一处 {@code lerp3} 调用，权重来自三个局部变量：
 *
 * <pre>
 * double d = (double) k / horizontalNoiseResolution;  // slot 9,  ordinal 0  (X)
 * double e = (double) l / horizontalNoiseResolution;  // slot 11, ordinal 1  (Z)
 * double t = (double) s / verticalNoiseResolution;    // slot 32, ordinal 10 (Y)
 * </pre>
 *
 * <p>ordinal 由 {@code javap -c} 数 {@code dstore} 顺序得出（0-based，仅 double）。
 */
@Mixin(NoiseChunkGenerator.class)
public class SampleHeightmapSmoothingMixin {

	/** X 方向权重（slot 9 / ordinal 0）。 */
	@ModifyVariable(method = "sampleHeightmap", at = @At("STORE"), ordinal = 0)
	private double noise_gen_debug$zeroHeightWeightX(double weight) {
		return NoiseParamsState.isSmoothingDisabled() ? 0.0D : weight;
	}

	/** Z 方向权重（slot 11 / ordinal 1）。 */
	@ModifyVariable(method = "sampleHeightmap", at = @At("STORE"), ordinal = 1)
	private double noise_gen_debug$zeroHeightWeightZ(double weight) {
		return NoiseParamsState.isSmoothingDisabled() ? 0.0D : weight;
	}

	/** Y 方向权重（slot 32 / ordinal 10）。 */
	@ModifyVariable(method = "sampleHeightmap", at = @At("STORE"), ordinal = 10)
	private double noise_gen_debug$zeroHeightWeightY(double weight) {
		return NoiseParamsState.isSmoothingDisabled() ? 0.0D : weight;
	}
}
