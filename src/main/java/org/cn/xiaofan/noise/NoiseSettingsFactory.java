package org.cn.xiaofan.noise;

import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import org.cn.xiaofan.mixin.ChunkGeneratorSettingsInvoker;

/**
 * 把 {@link NoiseParams} 打包成一份**内联的** {@link ChunkGeneratorSettings}。
 *
 * <p>做法：以原版 OVERWORLD 预设为模板（保留 structures / 默认方块 / 岩浆 / 海平面等
 * 不打算让用户改的字段），只替换其中的 {@link GenerationShapeConfig}。
 *
 * <p>为什么内联有效：这份对象不在任何注册表里，序列化时
 * {@code RegistryElementCodec.encodeOrId} 找不到 ID，就会把完整数据写进
 * {@code level.dat}，读档时再原样解回来 —— 天然的「按存档持久化」。
 */
public final class NoiseSettingsFactory {

	private NoiseSettingsFactory() {
	}

	/**
	 * 用给定参数构造内联 settings。
	 *
	 * @param params 用户改过的参数
	 * @return 可直接交给 {@code NoiseChunkGenerator} 的内联 settings
	 */
	public static ChunkGeneratorSettings create(NoiseParams params) {
		// 原版 OVERWORLD 预设（getInstance() 就是它）作为模板
		ChunkGeneratorSettings vanilla = ChunkGeneratorSettings.getInstance();

		return ChunkGeneratorSettingsInvoker.noise_gen_debug$create(
				vanilla.getStructuresConfig(),
				params.toShape(),
				vanilla.getDefaultBlock(),
				vanilla.getDefaultFluid(),
				vanilla.getBedrockCeilingY(),
				vanilla.getBedrockFloorY(),
				vanilla.getSeaLevel(),
				false);
	}
}
