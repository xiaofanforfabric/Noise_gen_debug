package org.cn.xiaofan.mixin.noise;

import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

/**
 * 噪声采样坐标偏移 —— 让出生点直接对应任意远处的噪声。
 *
 * <h2>为什么注入 {@code sampleNoise} 而不是 {@code sampleNoiseColumn}</h2>
 *
 * <p>早先这里注入的是 {@code sampleNoiseColumn([DII)V} 的两个 {@code int} 参数
 * （{@code x}/{@code z}，单位是噪声单元）。那个方案有两个硬伤：
 *
 * <ol>
 *   <li><b>坐标被钉在 {@code int}</b> —— 输入超过 {@code ±2^31} 就无法表达，
 *       而 {@code 1e18} 这种量级恰恰是距离现象需要的</li>
 *   <li>要写死 {@code / 4} 把方块坐标换算成噪声单元，
 *       且是整数除法会截断 —— 改了 {@code sizeHorizontal} 就算错</li>
 * </ol>
 *
 * <p>现在改注入 {@code sampleNoise(int x, int y, int z, ...)} 的
 * <b>{@code x}/{@code z}</b>，在这里它们仍是 {@code int}（找不到 double 入口），
 * 所以本类只负责<b>小范围</b>的整数偏移；<b>大范围偏移</b>由
 * {@code OctavePerlinNoiseSamplerMixin} 在 {@code maintainPrecision} 的
 * {@code double} 入参上完成。
 *
 * <p>也就是说偏移由两层叠加：
 * <pre>
 * 第一层（本类，方块坐标，int）  : x' = x + offset / horizontalResolution
 * 第二层（maintainPrecision，double）: v' = v * scale + offset
 * </pre>
 *
 * <p>单位换算用的是<b>运行时读取的真实分辨率</b>（{@code sizeHorizontal * 4}），
 * 不再是写死的 4 —— 这样改了 {@code sizeHorizontal} 也不会错位。
 *
 * <p>注：这里的除法<b>有意保留截断</b>。噪声单元是离散的，
 * 偏移量必须先对齐到单元边界，否则相邻单元会算出同一个采样点。
 */
@Mixin(NoiseChunkGenerator.class)
public class NoiseOffsetMixin {

	/** 偏移 X 轴（噪声单元坐标）。 */
	@ModifyVariable(method = "sampleNoise(IIIDDDD)D", at = @At("HEAD"),
			argsOnly = true, ordinal = 0)
	private int noise_gen_debug$offsetX(int x) {
		final java.math.BigDecimal offset = NoiseParamsState.get().noiseOffsetX;
		if (offset == null || offset.signum() == 0) {
			return x;
		}
		final int res = this.noise_gen_debug$horizontalResolution();
		// 方块偏移 -> 噪声单元偏移。用 floor 保证负方向也对称。
		final double units = Math.floor(offset.doubleValue() / res);
		final double moved = x + units;
		if (moved > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		if (moved < Integer.MIN_VALUE) {
			return Integer.MIN_VALUE;
		}
		return (int) moved;
	}

	/** 偏移 Z 轴（噪声单元坐标）。 */
	@ModifyVariable(method = "sampleNoise(IIIDDDD)D", at = @At("HEAD"),
			argsOnly = true, ordinal = 2)
	private int noise_gen_debug$offsetZ(int z) {
		final java.math.BigDecimal offset = NoiseParamsState.get().noiseOffsetZ;
		if (offset == null || offset.signum() == 0) {
			return z;
		}
		final int res = this.noise_gen_debug$horizontalResolution();
		final double units = Math.floor(offset.doubleValue() / res);
		final double moved = z + units;
		if (moved > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		if (moved < Integer.MIN_VALUE) {
			return Integer.MIN_VALUE;
		}
		return (int) moved;
	}

	/** 运行时读取水平分辨率（= sizeHorizontal * 4），而不是写死 4。 */
	private int noise_gen_debug$horizontalResolution() {
		final int res = ((org.cn.xiaofan.mixin.NoiseChunkGeneratorInvoker) (Object) this)
				.noise_gen_debug$getHorizontalNoiseResolution();
		return res > 0 ? res : 4;
	}
}
