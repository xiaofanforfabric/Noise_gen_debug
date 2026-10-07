package org.cn.xiaofan.mixin;

import java.util.OptionalInt;
import java.util.function.Supplier;

import com.mojang.serialization.Lifecycle;

import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.cn.xiaofan.client.NoiseParamsState;
import org.cn.xiaofan.noise.NoiseParams;
import org.cn.xiaofan.noise.NoiseSettingsFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 创建世界时，把用户改过的噪声参数注入到 overworld 的 generator。
 *
 * <p>注入点：{@code CreateWorldScreen.createLevel()} 里
 * <pre>
 * GeneratorOptions generatorOptions = this.moreOptionsDialog.getGeneratorOptions(this.hardcore);
 * </pre>
 * 用 {@code @ModifyVariable} 拦下这个局部变量（ordinal=0）。
 *
 * <p>关键：新的 {@link ChunkGeneratorSettings} **不在注册表里**，
 * 序列化 {@code level.dat} 时 {@code RegistryElementCodec} 找不到 ID
 * 就会内联完整数据 → 天然的按存档持久化，读档时原版 codec 自动还原。
 */
@Mixin(net.minecraft.client.gui.screen.world.CreateWorldScreen.class)
public class CreateWorldScreenCreateLevelMixin {

	@ModifyVariable(method = "createLevel", at = @At("STORE"), ordinal = 0)
	private GeneratorOptions noise_gen_debug$applyNoiseParams(GeneratorOptions original) {
		// 没改过就不动，保持完全原版行为（含 amplified / 调试世界等判定）。
		if (original == null || !NoiseParamsState.isModified()) {
			return original;
		}

		return this.noise_gen_debug$withCustomOverworld(original, NoiseParamsState.get());
	}

	/** 就地替换 overworld 的 generator，其余维度原样保留。 */
	private GeneratorOptions noise_gen_debug$withCustomOverworld(GeneratorOptions original, NoiseParams params) {
		SimpleRegistry<DimensionOptions> dims = original.getDimensions();
		DimensionOptions overworld = dims.get(DimensionOptions.OVERWORLD);

		if (overworld == null) {
			return original;
		}

		ChunkGenerator oldGenerator = overworld.getChunkGenerator();
		if (!(oldGenerator instanceof NoiseChunkGenerator)) {
			// 调试世界 / 超平坦等，不动。
			return original;
		}

		NoiseChunkGenerator oldNoise = (NoiseChunkGenerator) oldGenerator;
		BiomeSource biomeSource = oldNoise.getBiomeSource();
		long seed = original.getSeed();

		// 内联 settings：不在注册表里 → 会被写进 level.dat
		ChunkGeneratorSettings inline = NoiseSettingsFactory.create(params);
		Supplier<ChunkGeneratorSettings> supplier = () -> inline;

		NoiseChunkGenerator custom = new NoiseChunkGenerator(biomeSource, seed, supplier);

		Supplier<DimensionType> typeSupplier = overworld.getDimensionTypeSupplier();
		DimensionOptions replaced = new DimensionOptions(typeSupplier, custom);

		// 用 SimpleRegistry.replace 就地覆盖 OVERWORLD 条目，保留 rawId 与 lifecycle。
		OptionalInt rawId = OptionalInt.of(dims.getRawId(overworld));
		Lifecycle lifecycle = dims.getEntryLifecycle(overworld);
		dims.replace(rawId, DimensionOptions.OVERWORLD, replaced, lifecycle);

		return original;
	}
}
