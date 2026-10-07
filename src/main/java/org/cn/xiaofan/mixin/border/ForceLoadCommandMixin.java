package org.cn.xiaofan.mixin.border;

import net.minecraft.server.command.ForceLoadCommand;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 放开 {@code /forceload} 的坐标范围（原版卡在 ±30000000）。
 *
 * <p>参考 geniiii/FarLands 的实现（MIT）。
 */
@Mixin(ForceLoadCommand.class)
public abstract class ForceLoadCommandMixin {

	@ModifyConstant(constant = @Constant(intValue = 30000000), method = "executeChange")
	private static int noise_gen_debug$forceloadPositive(int original) {
		return NoiseParamsState.isWorldBorderDisabled() ? Integer.MAX_VALUE : original;
	}

	@ModifyConstant(constant = @Constant(intValue = -30000000), method = "executeChange")
	private static int noise_gen_debug$forceloadNegative(int original) {
		return NoiseParamsState.isWorldBorderDisabled() ? Integer.MIN_VALUE : original;
	}
}
