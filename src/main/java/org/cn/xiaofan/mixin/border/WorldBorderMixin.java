package org.cn.xiaofan.mixin.border;

import net.minecraft.world.World;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 放开「坐标是否合法」的水平限制。
 *
 * <p>原版 {@code World.isValidHorizontally(int,int)} 把范围卡在 ±30000000。
 * 这里放宽到 int 全范围，使远处坐标被视为合法。
 *
 * <p>参考 geniiii/FarLands 的实现（MIT）。
 */
@Mixin(World.class)
public abstract class WorldBorderMixin {

	@ModifyConstant(constant = @Constant(intValue = -30000000), method = "isValidHorizontally")
	private static int noise_gen_debug$validNegative(int original) {
		return NoiseParamsState.isWorldBorderDisabled() ? Integer.MIN_VALUE : original;
	}

	@ModifyConstant(constant = @Constant(intValue = 30000000), method = "isValidHorizontally")
	private static int noise_gen_debug$validPositive(int original) {
		return NoiseParamsState.isWorldBorderDisabled() ? Integer.MAX_VALUE : original;
	}
}
