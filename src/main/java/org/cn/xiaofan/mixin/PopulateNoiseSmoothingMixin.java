package org.cn.xiaofan.mixin;

import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 「取消平滑器」开关。
 *
 * <p>原版噪声只在稀疏网格上采样（XZ 每 4 格、Y 每 8 格），然后用插值填满整块。
 * 以 {@code populateNoise} 为例，插值权重是三个 double 局部变量：
 *
 * <pre>
 * double y  = (double) u  / verticalNoiseResolution;    // slot 42, ordinal 8
 * double ag = (double) ad / horizontalNoiseResolution;  // slot 55, ordinal 13
 * double am = (double) aj / horizontalNoiseResolution;  // slot 64, ordinal 16
 * </pre>
 *
 * <p>把这三个权重强制为 {@code 0.0} 后，{@code lerp(w, a, b)} 恒等于 {@code a}，
 * 即每格直接取「网格角点」的值 —— 地形变成 4×8×4 的块状台阶。
 *
 * <p>ordinal 是通过 {@code javap -c} 数方法内 {@code dstore} 指令顺序得出的，
 * 注意是 0-based 且仅统计 double 类型（long 与 double 共用 slot 计数但类型不同，
 * Mixin 的 ordinal 按类型过滤）。
 *
 * <p>{@code sampleHeightmap} 里有另一份同样的插值（用于高度图 / getHeight），
 * 见 {@link SampleHeightmapSmoothingMixin}。
 */
@Mixin(NoiseChunkGenerator.class)
public class PopulateNoiseSmoothingMixin {

	/** Y 方向权重（slot 42 / ordinal 8）。 */
	@ModifyVariable(method = "populateNoise", at = @At("STORE"), ordinal = 8)
	private double noise_gen_debug$zeroWeightsY(double weight) {
		return NoiseParamsState.isSmoothingDisabled() ? 0.0D : weight;
	}

	/** X 方向权重（slot 55 / ordinal 13）。 */
	@ModifyVariable(method = "populateNoise", at = @At("STORE"), ordinal = 13)
	private double noise_gen_debug$zeroWeightsX(double weight) {
		return NoiseParamsState.isSmoothingDisabled() ? 0.0D : weight;
	}

	/** Z 方向权重（slot 64 / ordinal 16）。 */
	@ModifyVariable(method = "populateNoise", at = @At("STORE"), ordinal = 16)
	private double noise_gen_debug$zeroWeightsZ(double weight) {
		return NoiseParamsState.isSmoothingDisabled() ? 0.0D : weight;
	}
}
