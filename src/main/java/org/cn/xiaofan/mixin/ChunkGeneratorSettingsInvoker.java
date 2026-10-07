package org.cn.xiaofan.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.chunk.StructuresConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link ChunkGeneratorSettings} 的私有构造器。
 *
 * <p>构造器是 {@code private}，本模组需要用它把用户参数打包成一份**内联 settings**。
 * 这份内联对象不在注册表里，所以序列化 {@code level.dat} 时
 * {@code RegistryElementCodec.encodeOrId} 会自动内联完整数据 → 天然按存档隔离。
 */
@Mixin(ChunkGeneratorSettings.class)
public interface ChunkGeneratorSettingsInvoker {

	@Invoker("<init>")
	static ChunkGeneratorSettings noise_gen_debug$create(
			StructuresConfig structuresConfig,
			GenerationShapeConfig generationShapeConfig,
			BlockState defaultBlock,
			BlockState defaultFluid,
			int bedrockCeilingY,
			int bedrockFloorY,
			int seaLevel,
			boolean mobGenerationDisabled) {
		throw new AssertionError();
	}
}
