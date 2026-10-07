package org.cn.xiaofan.client.gui;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.cn.xiaofan.client.NoiseParamsState;
import org.cn.xiaofan.client.gui.widget.DoubleSliderWidget;
import org.cn.xiaofan.noise.NoiseParams;
import org.cn.xiaofan.noise.NoiseParams.NoiseLayerMode;

/**
 * 噪声参数编辑界面。
 *
 * <p>布局分三块：
 * <ul>
 *   <li>顶部三个布尔开关（FARLANDS 折叠 / 平滑器 / 世界边界）</li>
 *   <li>中部数值滑块（映射到 {@link NoiseParams} 字段）</li>
 *   <li>底部 完成 / 预设 / 取消</li>
 * </ul>
 *
 * <p>数值滑块直接读写 {@link NoiseParamsState#get()} 返回的实例，改完即生效
 * （创建世界时会被打包成内联 settings 写进 level.dat）。
 */
public class NoiseParamsScreen extends Screen {


	private final Screen parent;

	// --- 布尔开关 ---
	private ButtonWidget foldingButton;
	private ButtonWidget smoothingButton;
	private ButtonWidget borderButton;
	private ButtonWidget limiterButton;
	/** 噪声层模式按钮（默认 / 仅低噪声 / 仅高噪声）。 */
	private ButtonWidget layerModeButton;

	/** 极端缩放保护开关。 */
	private ButtonWidget extremeGuardButton;

	/** 32 位浮点按轴开关：X / Z / Y / 全。 */
	private ButtonWidget floatXButton;
	private ButtonWidget floatZButton;
	private ButtonWidget floatYButton;
	private ButtonWidget floatAllButton;

	/** 数值滑块列表，重置时需要统一刷新位置。 */
	private final List<DoubleSliderWidget> sliders = new ArrayList<>();

	// --- 噪声坐标偏移输入框（按方块坐标输入） ---
	/** 偏移输入框。 */
	private TextFieldWidget offsetXField;
	private TextFieldWidget offsetYField;
	private TextFieldWidget offsetZField;

	/** 最近一次偏移输入被 int 夹紧？用于在状态行提醒用户。 */

	// --- 缩放输入框（支持极端值，如 2^31） ---
	private TextFieldWidget scaleXField;
	private TextFieldWidget scaleYField;

	public NoiseParamsScreen(Screen parent) {
		super(new TranslatableText("noise_gen_debug.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.sliders.clear();


		this.noise_gen_debug$initToggles();
		this.noise_gen_debug$initSubScreenButtons();
		this.noise_gen_debug$initOffsetFields();
		this.noise_gen_debug$initSliders();
		this.noise_gen_debug$initFooter();
	}

	// ==================== 噪声坐标偏移输入框 ====================

	/**
	 * 三个坐标偏移输入框。
	 *
	 * <p>用户按<b>方块坐标</b>输入（例如 {@code 12550820}），内部除以 4 转成噪声单元。
	 * 这样出生点 (0,0) 就能生成对应远处的噪声地形，而玩家坐标仍然很小，
	 * 绕开 32 位坐标溢出的问题。
	 *
	 * <p>Y 偏移按方块坐标 1:1 存储（垂直分辨率是 {@code sizeVertical * 4}，
	 * 不做 /4 换算以免误解），实际是否生效取决于噪声采样对 Y 的使用方式。
	 */
	private void noise_gen_debug$initOffsetFields() {
		int fieldW = 72;
		int fieldH = 18;
		int baseX = this.width / 2 + 5;
		// 放在两列开关（最下到 y=106）之下、滑块（y=150 起）之上。
		// 放在最下面：滑块占 150 ~ 282，这里留到 310。
		int y = 310;

		this.offsetXField = this.noise_gen_debug$makeOffsetField(
				baseX, y, fieldW, fieldH, "X", 0);
		this.offsetYField = this.noise_gen_debug$makeOffsetField(
				baseX + fieldW + 4, y, fieldW, fieldH, "Y", 1);
		this.offsetZField = this.noise_gen_debug$makeOffsetField(
				baseX + (fieldW + 4) * 2, y, fieldW, fieldH, "Z", 2);
	}

	private TextFieldWidget noise_gen_debug$makeOffsetField(int x, int y, int w, int h,
			String axis, int axisIndex) {
		TextFieldWidget field = new TextFieldWidget(
				this.textRenderer, x, y, w, h,
				new TranslatableText("noise_gen_debug.offset." + axis));
		// 放宽到 24：要容纳科学计数法（1E9）与小数（1.5e7）。
		field.setMaxLength(24);
		this.noise_gen_debug$writeOffsetField(field, axisIndex);
		field.setChangedListener(text -> this.noise_gen_debug$applyOffset(axisIndex, text));
		this.addButton(field);
		return field;
	}

	/**
	 * 读取当前偏移（存的就是方块坐标）。
	 *
	 * <p>{@code int} 最大 10 位数字，在 72px 宽的框里会被 {@code TextFieldWidget}
	 * 裁掉大半（它只画光标附近一段）。所以大数用科学计数法显示，
	 * 再把光标移到末尾，保证能看到完整值。
	 */
	/**
	 * 把偏移值格式化成输入框里看得见的形式。
	 *
	 * <p>偏移是 {@link BigDecimal}（软件大数），所以可以直接
	 * {@link BigDecimal#toPlainString()} 或 {@link BigDecimal#toString()} ——
	 * 两者都<b>不丢精度</b>，不像 {@code double} 那样在 {@code 2^53} 之后失真。
	 *
	 * <p>用 {@code toString()}：它会在「短」和「精确」之间自动选科学计数法，
	 * 例如 {@code 1E30}、{@code 1.5E+18}。
	 * 超过 16 个字符时输入框只能画出一小段，所以这里保持简短更重要。
	 */
	private static String noise_gen_debug$fmtOffset(BigDecimal v) {
		if (v == null || v.signum() == 0) {
			return "0";
		}
		// 位数不多时用十进制，最直观
		String plain = v.toPlainString();
		if (plain.length() <= 16) {
			return plain;
		}
		// 太长就用科学计数法（BigDecimal.toString 会自动选）
		return v.toString();
	}

	/** 把偏移写进输入框，并把光标移到末尾（否则只看到开头几位）。 */
	private void noise_gen_debug$writeOffsetField(TextFieldWidget field, int axisIndex) {
		NoiseParams p = NoiseParamsState.get();
		BigDecimal v;
		switch (axisIndex) {
			case 0:
				v = p.noiseOffsetX;
				break;
			case 1:
				v = p.noiseOffsetY;
				break;
			case 2:
			default:
				v = p.noiseOffsetZ;
				break;
		}
		field.setText(noise_gen_debug$fmtOffset(v));
		field.setCursorToEnd();
	}

	/**
	 * 解析输入框文本并写回参数。
	 *
	 * <h2>为什么用 {@code Double} 而不是 {@code Integer}</h2>
	 *
	 * <p>早先用 {@code Integer.parseInt}，于是 {@code 1E9}、{@code 2^32}、
	 * {@code 1.5e7} 这类写法会直接抛 {@code NumberFormatException}
	 * 而被静默忽略 —— 你敲进去了、看着像成功，其实模型里还是旧值。
	 *
	 * <p>改用 {@code Double.parseDouble} 后可以接受科学计数法；
	 * 模型里存的仍是 {@code int}，所以写回前会夹到 {@code int} 范围。
	 *
	 * <p>解析失败（如半成品 "1E"）时<b>什么都不做</b>，等下一个字符 ——
	 * 逐字符输入时这是必要的，否则会打断用户。
	 */
	private void noise_gen_debug$applyOffset(int axisIndex, String text) {
		// 用 BigDecimal 解析 —— 直接构造自字符串，不经过 double，
		// 所以 "1E30" 得到的是精确的 10^30 而不是 double 近似值。
		final BigDecimal parsed = org.cn.xiaofan.noise.Reposition.parse(text);
		if (parsed == null) {
			return; // 半成品输入（"1E"、"-"、空串等），等下一个字符
		}

		// <b>不再夹到 int。</b>
		//
		// 早先这里把输入夹到 ±2^31，因为模型字段是 int。
		// 但噪声偏移的作用是「让采样器去查远方的噪声」，
		// 那条路径最终会走到 maintainPrecision 的 double 入参（1e308 量级），
		// 与玩家坐标无关 —— 所以偏移本就没有 int 的理由。
		//
		// 模型字段已改成 double（见 NoiseParams#noiseOffsetX）。
		NoiseParams p = NoiseParamsState.get();
		switch (axisIndex) {
			case 0:
				p.noiseOffsetX = parsed;
				break;
			case 1:
				p.noiseOffsetY = parsed;
				break;
			case 2:
			default:
				p.noiseOffsetZ = parsed;
				break;
		}
	}

	// ==================== 缩放输入框 ====================

	/**
	 * 构造 xzScale / yScale 的输入框。
	 *
	 * <p>用输入框而非滑块，是因为需要支持 {@code 2^31} 这种极端值（验证溢出行为）。
	 * <p><b>警告</b>：{@code NoiseSamplingConfig} 的 Codec 范围是 {@code 0.001 ~ 1000}。
	 * 超出范围的值能生效（构造器不校验），但会导致存档序列化失败、读不回。
	 */
	private TextFieldWidget noise_gen_debug$makeScaleField(int x, int y, int w, int h,
			String labelKey, int which) {
		TextFieldWidget field = new TextFieldWidget(
				this.textRenderer, x, y, w, h, new TranslatableText(labelKey));
		field.setMaxLength(16);
		field.setText(noise_gen_debug$scaleText(which));
		field.setChangedListener(text -> noise_gen_debug$applyScale(which, text));
		this.addButton(field);
		return field;
	}

	private String noise_gen_debug$scaleText(int which) {
		NoiseParams p = NoiseParamsState.get();
		double v = which == 0 ? p.xzScale : p.yScale;
		// NaN / Inf 用字面量表示，便于回读和再次编辑。
		if (Double.isNaN(v)) {
			return "NaN";
		}
		if (Double.isInfinite(v)) {
			return v > 0 ? "Infinity" : "-Infinity";
		}
		// 避免科学计数法，便于直接看/改整数。
		if (v == Math.floor(v) && !Double.isInfinite(v)) {
			return String.valueOf((long) v);
		}
		return String.valueOf(v);
	}

	private void noise_gen_debug$applyScale(int which, String text) {
		double value;
		try {
                    // Double.parseDouble 原生支持 "NaN" / "Infinity" / "-Infinity"，
                    // 这正是我们要拿来观察溢出行为的值，所以不再拦截。
                    value = Double.parseDouble(text.trim());
            } catch (NumberFormatException e) {
                    // 空串、"2^31" 这类非法输入：忽略，保持模型原值。
                    return;
		}

		NoiseParams p = NoiseParamsState.get();
		if (which == 0) {
			p.xzScale = value;
		} else {
			p.yScale = value;
		}
	}

	// ==================== 子界面入口 ====================

	/**
	 * 打开「边境之地 / maintainPrecision」子界面。
	 *
	 * <p>位置放在左侧 y=106 —— 这一行的左半边<b>本来就是空的</b>
	 * （右边是 {@code [X][Z]} 两个 32 位浮点开关，各 72 宽，
	 * 只占 {@code width/2+5 .. width/2+149}）。
	 * 早先这里放的是 2D 预览图，删掉后才腾出空间。
	 */
	private void noise_gen_debug$initSubScreenButtons() {
		this.addButton(new ButtonWidget(
				this.width / 2 - 155, 106, 150, 20,
				new TranslatableText("noise_gen_debug.button.farlands"),
				button -> this.client.openScreen(new FarLandsScreen(this))));
	}

	// ==================== 开关 ====================

	private void noise_gen_debug$initToggles() {
		this.foldingButton = this.addButton(new ButtonWidget(
				this.width / 2 - 155, 18, 150, 20,
				this.foldingButtonText(),
				button -> {
					// 快捷开关：在「原版折叠」和「移除折叠」之间切换。
					// 完整配置（Beta / Release / 自定义除数 / 返回値上限）
					// 在「边境之地设置…」子界面里。
					boolean removing = NoiseParamsState.getFarLandsMode()
									== org.cn.xiaofan.noise.FarLandsMode.REMOVED;
					NoiseParamsState.setFarLandsMode(removing
									? org.cn.xiaofan.noise.FarLandsMode.DEFAULT
									: org.cn.xiaofan.noise.FarLandsMode.REMOVED);
					button.setMessage(this.foldingButtonText());
				}));

		this.smoothingButton = this.addButton(new ButtonWidget(
				this.width / 2 - 155, 40, 150, 20,
				this.smoothingButtonText(),
				button -> {
					NoiseParamsState.setSmoothingDisabled(
							!NoiseParamsState.isSmoothingDisabled());
					button.setMessage(this.smoothingButtonText());
				}));

		this.borderButton = this.addButton(new ButtonWidget(
				this.width / 2 - 155, 62, 150, 20,
				this.borderButtonText(),
				button -> {
					NoiseParamsState.setWorldBorderDisabled(
							!NoiseParamsState.isWorldBorderDisabled());
					button.setMessage(this.borderButtonText());
				}));

		// 限制器开关：把 top/bottom slide 的 size 置 0。
		this.limiterButton = this.addButton(new ButtonWidget(
				this.width / 2 + 5, 18, 150, 20,
				this.limiterButtonText(),
				button -> {
					NoiseParams p = NoiseParamsState.get();
					p.setLimiterDisabled(!p.limiterDisabled);
					button.setMessage(this.limiterButtonText());
					// 限制器被改动后，slide 滑块的值也变了，需要同步显示。
					this.noise_gen_debug$refreshSliders();
				}));

		// 噪声层模式：循环切换 默认 / 仅低噪声 / 仅高噪声。
		this.layerModeButton = this.addButton(new ButtonWidget(
				this.width / 2 + 5, 40, 150, 20,
				this.layerModeButtonText(),
				button -> {
					NoiseParams p = NoiseParamsState.get();
					p.noiseLayerMode = p.noiseLayerMode.next();
					button.setMessage(this.layerModeButtonText());
				}));

		this.extremeGuardButton = this.addButton(new ButtonWidget(
				this.width / 2 + 5, 62, 150, 20,
				this.extremeGuardButtonText(),
				button -> {
					NoiseParamsState.setExtremeDensityGuardEnabled(
							!NoiseParamsState.isExtremeDensityGuardEnabled());
					button.setMessage(this.extremeGuardButtonText());
				}));


		// 32 位浮点的按轴开关：X / Z 一行，Y / 全 一行，宽 72 两列。
		this.floatXButton = this.addButton(new ButtonWidget(
				this.width / 2 + 5, 106, 72, 20,
				this.noise_gen_debug$floatAxisText(0),
				button -> {
					NoiseParamsState.toggleFloat32Axis(0);
					org.cn.xiaofan.client.NoisePrecisionStats.reset();
					this.noise_gen_debug$refreshFloatButtons();
				}));
		this.floatZButton = this.addButton(new ButtonWidget(
				this.width / 2 + 83, 106, 72, 20,
				this.noise_gen_debug$floatAxisText(1),
				button -> {
					NoiseParamsState.toggleFloat32Axis(1);
					org.cn.xiaofan.client.NoisePrecisionStats.reset();
					this.noise_gen_debug$refreshFloatButtons();
				}));

		this.floatYButton = this.addButton(new ButtonWidget(
				this.width / 2 + 5, 128, 72, 20,
				this.noise_gen_debug$floatAxisText(2),
				button -> {
					NoiseParamsState.toggleFloat32Axis(2);
					org.cn.xiaofan.client.NoisePrecisionStats.reset();
					this.noise_gen_debug$refreshFloatButtons();
				}));
		this.floatAllButton = this.addButton(new ButtonWidget(
				this.width / 2 + 83, 128, 72, 20,
				this.noise_gen_debug$floatAllText(),
				button -> {
					NoiseParamsState.toggleFloat32All();
					org.cn.xiaofan.client.NoisePrecisionStats.reset();
					this.noise_gen_debug$refreshFloatButtons();
				}));
	}

	// ==================== 数值滑块 ====================

	private void noise_gen_debug$initSliders() {
		NoiseParams p = NoiseParamsState.get();

		int leftX = this.width / 2 - 155;
		int rightX = this.width / 2 + 5;
		int w = 150;
		int h = 20;
		int y0 = 150;
		int gap = 22;

		// --- 采样 / 平滑（左列） ---
		addSlider(leftX, y0, w, h, "noise_gen_debug.param.xzFactor",
				1.0D, 400.0D, 1, false,
				() -> p.xzFactor, v -> p.xzFactor = v);
		addSlider(leftX, y0 + gap, w, h, "noise_gen_debug.param.yFactor",
				1.0D, 400.0D, 1, false,
				() -> p.yFactor, v -> p.yFactor = v);
		addSlider(leftX, y0 + gap * 2, w, h, "noise_gen_debug.param.sizeHorizontal",
				1.0D, 4.0D, 0, true,
				() -> p.sizeHorizontal, v -> p.sizeHorizontal = (int) v);
		addSlider(leftX, y0 + gap * 3, w, h, "noise_gen_debug.param.sizeVertical",
				1.0D, 4.0D, 0, true,
				() -> p.sizeVertical, v -> p.sizeVertical = (int) v);
		addSlider(leftX, y0 + gap * 4, w, h, "noise_gen_debug.param.height",
				64.0D, 256.0D, 0, true,
				() -> p.height, v -> p.height = (int) v);

		// --- 密度 / 缩放（右列） ---
		addSlider(rightX, y0, w, h, "noise_gen_debug.param.densityFactor",
				0.0D, 4.0D, 3, false,
				() -> p.densityFactor, v -> p.densityFactor = v);
		addSlider(rightX, y0 + gap, w, h, "noise_gen_debug.param.densityOffset",
				-8.0D, 4.0D, 4, false,
				() -> p.densityOffset, v -> p.densityOffset = v);

		// xzScale / yScale 用输入框（滑块范围只有 0.5~2，装不下 2^31 这种极端值）。
		// 注意：NoiseSamplingConfig 的 Codec 限制是 0.001~1000，
		// 超出范围的值能生效（构造器不校验），但会导致存档序列化失败/读不回。
		this.scaleXField = this.noise_gen_debug$makeScaleField(
				rightX, y0 + gap * 2, w, h, "noise_gen_debug.param.xzScale", 0);
		this.scaleYField = this.noise_gen_debug$makeScaleField(
				rightX, y0 + gap * 3, w, h, "noise_gen_debug.param.yScale", 1);

		// --- 限制器 slide（下部） ---
		int ySlide = y0 + gap * 5;
		addSlider(leftX, ySlide, w, h, "noise_gen_debug.param.topSlideTarget",
				-64.0D, 64.0D, 0, true,
				() -> p.topSlideTarget, v -> p.topSlideTarget = (int) v);
		addSlider(rightX, ySlide, w, h, "noise_gen_debug.param.topSlideSize",
				0.0D, 32.0D, 0, true,
				() -> p.topSlideSize, v -> p.topSlideSize = (int) v);
		addSlider(leftX, ySlide + gap, w, h, "noise_gen_debug.param.bottomSlideTarget",
				-64.0D, 64.0D, 0, true,
				() -> p.bottomSlideTarget, v -> p.bottomSlideTarget = (int) v);
		addSlider(rightX, ySlide + gap, w, h, "noise_gen_debug.param.bottomSlideSize",
				0.0D, 32.0D, 0, true,
				() -> p.bottomSlideSize, v -> p.bottomSlideSize = (int) v);
	}

	private void addSlider(int x, int y, int w, int h, String labelKey,
			double min, double max, int decimals, boolean integerOnly,
			DoubleSupplier getter, DoubleConsumer setter) {
		DoubleSliderWidget slider = new DoubleSliderWidget(
				x, y, w, h, labelKey, min, max, decimals, integerOnly, getter, setter);
		this.addButton(slider);
		this.sliders.add(slider);
	}

	// ==================== 底部按钮 ====================

	private void noise_gen_debug$initFooter() {
		int leftX = this.width / 2 - 155;
		int rightX = this.width / 2 + 5;
		int w = 150;

		this.addButton(new ButtonWidget(
				leftX, this.height - 52, w, 20,
				new TranslatableText("noise_gen_debug.button.done"),
				button -> this.onClose()));

		this.addButton(new ButtonWidget(
				rightX, this.height - 52, w, 20,
				new TranslatableText("noise_gen_debug.button.vanilla_preset"),
				button -> {
					NoiseParamsState.reset();
					this.noise_gen_debug$refreshAll();
				}));

		this.addButton(new ButtonWidget(
				leftX, this.height - 28, w, 20,
				new TranslatableText("noise_gen_debug.button.amplified_preset"),
				button -> {
					NoiseParamsState.get().amplified = true;
					this.noise_gen_debug$refreshAll();
				}));

		this.addButton(new ButtonWidget(
				rightX, this.height - 28, w, 20,
				new TranslatableText("noise_gen_debug.button.cancel"),
				button -> this.onClose()));
	}

	/** 参数被外部改动后，刷新所有控件显示。 */
	// ==================== 2D 预览 ====================

	// ==================== 刷新 ====================

	/** 参数被外部改动后，把界面上所有控件的显示同步到模型。 */
	private void noise_gen_debug$refreshAll() {
		this.foldingButton.setMessage(this.foldingButtonText());
		this.smoothingButton.setMessage(this.smoothingButtonText());
		this.borderButton.setMessage(this.borderButtonText());
		this.limiterButton.setMessage(this.limiterButtonText());
		this.layerModeButton.setMessage(this.layerModeButtonText());
		this.extremeGuardButton.setMessage(this.extremeGuardButtonText());
		this.noise_gen_debug$refreshFloatButtons();
		// 偏移输入框也要同步（重置预设会归零）。
		this.noise_gen_debug$writeOffsetField(this.offsetXField, 0);
		this.noise_gen_debug$writeOffsetField(this.offsetYField, 1);
		this.noise_gen_debug$writeOffsetField(this.offsetZField, 2);
		// 缩放输入框同步。
		this.scaleXField.setText(this.noise_gen_debug$scaleText(0));
		this.scaleYField.setText(this.noise_gen_debug$scaleText(1));
		this.noise_gen_debug$refreshSliders();
	}

	/** 只刷新数值滑块（限制器开关会改动 slide 值）。 */
	private void noise_gen_debug$refreshSliders() {
		for (DoubleSliderWidget slider : this.sliders) {
			slider.refreshFromModel();
		}
	}

	// ==================== 文案 ====================

	private Text foldingButtonText() {
		// 只有「移除折叠」算开启；其他模式都显示「关」，避免误导。
		boolean removed = NoiseParamsState.getFarLandsMode()
				== org.cn.xiaofan.noise.FarLandsMode.REMOVED;
		return new TranslatableText(removed
					? "noise_gen_debug.button.folding.off"
					: "noise_gen_debug.button.folding.on");	}
	private Text smoothingButtonText() {
		boolean disabled = NoiseParamsState.isSmoothingDisabled();
		return new TranslatableText(disabled
				? "noise_gen_debug.button.smoothing.off"
				: "noise_gen_debug.button.smoothing.on");
	}

	private Text borderButtonText() {
		boolean disabled = NoiseParamsState.isWorldBorderDisabled();
		return new TranslatableText(disabled
				? "noise_gen_debug.button.border.off"
				: "noise_gen_debug.button.border.on");
	}

	/** 「X: 32」这类按轴文案。 */
	private Text noise_gen_debug$floatAxisText(int axis) {
		boolean on;
		String label;
		switch (axis) {
			case 0:
				on = NoiseParamsState.isFloat32X();
				label = "X";
				break;
			case 1:
				on = NoiseParamsState.isFloat32Z();
				label = "Z";
				break;
			default:
				on = NoiseParamsState.isFloat32Y();
				label = "Y";
				break;
		}
		return new TranslatableText("noise_gen_debug.button.floatprec.axis",
				label, on ? "32" : "64");
	}

	private Text noise_gen_debug$floatAllText() {
		boolean all = NoiseParamsState.getFloatBits32() == 7;
		return new TranslatableText(all
				? "noise_gen_debug.button.floatprec.all32"
				: "noise_gen_debug.button.floatprec.all64");
	}

	private void noise_gen_debug$refreshFloatButtons() {
		this.floatXButton.setMessage(this.noise_gen_debug$floatAxisText(0));
		this.floatZButton.setMessage(this.noise_gen_debug$floatAxisText(1));
		this.floatYButton.setMessage(this.noise_gen_debug$floatAxisText(2));
		this.floatAllButton.setMessage(this.noise_gen_debug$floatAllText());
	}


	private Text extremeGuardButtonText() {
		boolean enabled = NoiseParamsState.isExtremeDensityGuardEnabled();
		return new TranslatableText(enabled
				? "noise_gen_debug.button.extreme_guard.on"
				: "noise_gen_debug.button.extreme_guard.off");
	}

	private Text limiterButtonText() {
		boolean disabled = NoiseParamsState.get().limiterDisabled;
		return new TranslatableText(disabled
				? "noise_gen_debug.button.limiter.off"
				: "noise_gen_debug.button.limiter.on");
	}

	private Text layerModeButtonText() {
		NoiseLayerMode mode = NoiseParamsState.get().noiseLayerMode;
		String key;
		switch (mode) {
			case LOW_ONLY:
				key = "noise_gen_debug.button.layer.low";
				break;
			case HIGH_ONLY:
				key = "noise_gen_debug.button.layer.high";
				break;
			case DEFAULT:
			default:
				key = "noise_gen_debug.button.layer.default";
				break;
		}
		return new TranslatableText(key);
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);

		drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 6, 0xFFFFFF);

		boolean modified = NoiseParamsState.isModified()
				|| NoiseParamsState.getFarLandsMode() != org.cn.xiaofan.noise.FarLandsMode.DEFAULT
				|| NoiseParamsState.isSmoothingDisabled()
				|| NoiseParamsState.isWorldBorderDisabled();
		String status = NoiseParamsText.status(modified);
		drawCenteredText(matrices, this.textRenderer, status, this.width / 2,
				this.height - 68, modified ? 0x80FF80 : 0xA0A0A0);


		super.render(matrices, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		if (this.client != null) {
			this.client.openScreen(this.parent);
		}
	}
}
