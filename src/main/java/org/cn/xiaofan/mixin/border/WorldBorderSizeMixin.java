package org.cn.xiaofan.mixin.border;

import java.util.function.Supplier;

import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.dimension.DimensionType;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界创建时把边界尺寸/上限拉到最大。
 *
 * <p>原版每个世界创建时会用默认边界（约 ±3.0E7），即使
 * {@code getMaxWorldBorderRadius()} 放开了，现存世界仍受旧值约束。
 * 这里在构造器 RETURN 时直接把 size 与 maxRadius 拉满，保证能走到 FARLANDS。
 *
 * <p>注意 1.16.5 的方法名是 {@code setMaxRadius(int)}，
 * 而不是新版的 {@code setMaxWorldBorderRadius(int)}。
 *
 * <p>参考 geniiii/FarLands 的实现（MIT）。
 */
@Mixin(World.class)
public abstract class WorldBorderSizeMixin {

	@Shadow
	public abstract WorldBorder getWorldBorder();

	@Inject(method = "<init>", at = @At("RETURN"))
	private void noise_gen_debug$maximizeBorder(MutableWorldProperties properties,
			RegistryKey<World> registryRef,
			DimensionType dimensionType,
			Supplier<Profiler> profiler,
			boolean isClient,
			boolean debugWorld,
			long seed,
			CallbackInfo ci) {
		WorldBorder border = this.getWorldBorder();
		if (border == null || !NoiseParamsState.isWorldBorderDisabled()) {
			return;
		}

		border.setMaxRadius(Integer.MAX_VALUE);
		border.setSize(Double.MAX_VALUE);
	}
}
