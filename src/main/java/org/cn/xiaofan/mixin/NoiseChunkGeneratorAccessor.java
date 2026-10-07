package org.cn.xiaofan.mixin;

import java.util.function.Supplier;

import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code NoiseChunkGenerator.settings} 字段。
 *
 * <p>该字段在源码里是 {@code protected final Supplier<ChunkGeneratorSettings>}，
 * 用 {@link Accessor} 生成 getter，避免反射（性能好且不会被混淆破坏）。
 *
 * <p>单人存档下 F3 需要读取真实生效的 settings，这是唯一干净的入口。
 */
@Mixin(NoiseChunkGenerator.class)
public interface NoiseChunkGeneratorAccessor {

	@Accessor("settings")
	Supplier<ChunkGeneratorSettings> noise_gen_debug$getSettings();
}
