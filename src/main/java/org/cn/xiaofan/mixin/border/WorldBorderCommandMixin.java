package org.cn.xiaofan.mixin.border;

import net.minecraft.server.command.WorldBorderCommand;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 放开 {@code /worldborder set} 的参数上限。
 *
 * <p>原版在命令注册时把半径参数卡在 ±6.0E7（float），执行时又卡 6.0E7（double）。
 * 这里全部放宽到对应类型的最大值。
 *
 * <p>参考 geniiii/FarLands 的实现（MIT）。
 */
@Mixin(WorldBorderCommand.class)
public abstract class WorldBorderCommandMixin {

	@ModifyConstant(constant = @Constant(floatValue = -6.0E7F), method = "register")
	private static float noise_gen_debug$radiusNegative(float original) {
		return NoiseParamsState.isWorldBorderDisabled() ? -Float.MAX_VALUE : original;
	}

	@ModifyConstant(constant = @Constant(floatValue = 6.0E7F), method = "register")
	private static float noise_gen_debug$radiusPositive(float original) {
		return NoiseParamsState.isWorldBorderDisabled() ? Float.MAX_VALUE : original;
	}

	@ModifyConstant(constant = @Constant(doubleValue = 6.0E7D), method = "executeSet")
	private static double noise_gen_debug$setRadius(double original) {
		return NoiseParamsState.isWorldBorderDisabled() ? Double.MAX_VALUE : original;
	}
}
