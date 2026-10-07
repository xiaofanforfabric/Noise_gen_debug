package org.cn.xiaofan.mixin;

import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link NoiseChunkGenerator} 的私有噪声计算入口，供 F3 调试显示使用。
 *
 * <p>1.16.5 里这些方法和字段都是 {@code private}，无法直接调用；
 * 用 {@link Invoker} / {@link Accessor} 生成桥接，避免反射。
 */
@Mixin(NoiseChunkGenerator.class)
public interface NoiseChunkGeneratorInvoker {

	/**
	 * 三层 Perlin 插值合成的「原始噪声值」。
	 *
	 * <p>返回 {@code clampedLerp(lower/512, upper/512, (interp/10 + 1)/2)}。
	 * 这是 1.16.5 最接近 1.18+ {@code N}（最终密度）的量。
	 */
	@Invoker("sampleNoise")
	double noise_gen_debug$sampleNoise(int x, int y, int z,
			double horizontalScale, double verticalScale,
			double horizontalStretch, double verticalStretch);

	/**
	 * 计算一整列（Y 方向）的密度值，写入 {@code buffer}。
	 *
	 * <p>buffer 长度需为 {@code noiseSizeY + 1}，索引 0 在底部。
	 */
	@Invoker("sampleNoiseColumn")
	void noise_gen_debug$sampleNoiseColumn(double[] buffer, int x, int z);

	/** 垂直方向的噪声单元数（= height / (sizeVertical * 4)）。 */
	@Accessor("noiseSizeY")
	int noise_gen_debug$getNoiseSizeY();

	/** 每个噪声单元对应的方块数（= sizeVertical * 4）。 */
	@Accessor("verticalNoiseResolution")
	int noise_gen_debug$getVerticalNoiseResolution();

	/** 水平方向每个噪声单元对应的方块数（= sizeHorizontal * 4，原版为 4）。 */
	@Accessor("horizontalNoiseResolution")
	int noise_gen_debug$getHorizontalNoiseResolution();
}
