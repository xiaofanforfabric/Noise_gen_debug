package org.cn.xiaofan.mixin.border;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 放开服务端对玩家坐标的校验。
 *
 * <p>原版 {@code ServerPlayNetworkHandler.validatePlayerMove} 用写死的 {@code 3.0E7}
 * 判断坐标是否异常，超过就把玩家踢下线。这里放宽到 double 全范围。
 *
 * <p>参考 geniiii/FarLands 的实现（MIT）。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

	@ModifyConstant(constant = @Constant(doubleValue = 3.0E7D), method = "validatePlayerMove")
	private static double noise_gen_debug$allowFarMove(double original) {
		return NoiseParamsState.isWorldBorderDisabled() ? Double.MAX_VALUE : original;
	}
}
