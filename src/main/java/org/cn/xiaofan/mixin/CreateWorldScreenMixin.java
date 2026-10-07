package org.cn.xiaofan.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.TranslatableText;
import org.cn.xiaofan.client.gui.NoiseParamsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在「创建世界」页面挂一个进入噪声参数编辑界面的按钮。
 *
 * <p>注入点：{@code CreateWorldScreen.init()} 的 TAIL —— protected 的非 abstract 方法，
 * 且在窗口尺寸变化时会重跑，按钮位置能自动跟随。
 */
@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {

	/**
	 * Screen.addButton 是 protected，mixin 类不在同一包/继承链上，直接调用编译不过。
	 * 用 @Shadow 声明同签名方法后即可调用（Mixin 会重定向到父类实现）。
	 */
	@Shadow
	protected <T extends ClickableWidget> T addButton(T button) {
		throw new AssertionError();
	}

	@Inject(at = @At("TAIL"), method = "init")
	private void noise_gen_debug$addButton(CallbackInfo ci) {
		CreateWorldScreen self = (CreateWorldScreen) (Object) this;

		// 按钮放左下角，避开原版「游戏模式 / 更多选项」等按钮。
		this.addButton(new ButtonWidget(
				4, self.height - 28, 120, 20,
				new TranslatableText("noise_gen_debug.button.open"),
				button -> MinecraftClient.getInstance().openScreen(new NoiseParamsScreen(self))));
	}
}
