package org.cn.xiaofan.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import org.cn.xiaofan.client.NoiseParamsState;
import org.cn.xiaofan.client.RepositionState;
import org.cn.xiaofan.client.SkyGridState;
import org.cn.xiaofan.noise.FarLandsMode;
import org.cn.xiaofan.noise.NoiseGenRegistry;
import org.cn.xiaofan.noise.provider.LatticeNoiseProvider;

/**
 * 距离现象 / 边境之地界面。
 *
 * <h2>两个独立机制（移植自 UltimateScaler，MIT）</h2>
 *
 * <table border="1">
 *   <tr><th>机制</th><th>作用</th><th>代码</th></tr>
 *   <tr>
 *     <td><b>坐标重定位</b></td>
 *     <td>{@code newPos = pos * scale + offset}<br>
 *         把世界坐标映射到任意噪声采样坐标</td>
 *     <td>{@code RepositionState} + {@code OctavePerlinNoiseSamplerMixin}</td>
 *   </tr>
 *   <tr>
 *     <td><b>maintainPrecision</b></td>
 *     <td>改折叠算法，决定该坐标处噪声的形态</td>
 *     <td>{@code FarLandsMode} / {@code OctavePerlinNoiseSamplerMixin}</td>
 *   </tr>
 * </table>
 *
 * <p><b>为什么要两个一起用</b>：距离现象发生在极远处。
 * 用 {@code scale} 把脚下坐标放大到那个量级，噪声才会展现出周期性 / 破碎 / 网格；
 * 而 {@code divisor} 决定这个周期的疏密。
 */
public class FarLandsScreen extends Screen {

	private final Screen parent;
	private boolean suppress;

	/** 最近一次被拒绝的输入对应的提示键（null = 无）。 */
	private String lastRejected;

	// ---- 重定位 ----
	private ButtonWidget repositionButton;
	private ButtonWidget axisYButton;
	private TextFieldWidget scaleXField;
	private TextFieldWidget scaleZField;
	private TextFieldWidget offsetXField;
	private TextFieldWidget offsetZField;

	// ---- maintainPrecision ----
	private ButtonWidget skyGridButton;
	private ButtonWidget forceClampButton;
	private ButtonWidget fringeCascadeButton;
	private ButtonWidget noiseGenButton;
	private ButtonWidget foldButton;

	private ButtonWidget modeButton;
	private TextFieldWidget divisorField;

	public FarLandsScreen(Screen parent) {
		super(new TranslatableText("noise_gen_debug.farlands.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int leftX = this.width / 2 - 155;
		final int rightX = this.width / 2 + 5;
		final int w = 150;
		final int h = 20;

		// ==================== 坐标重定位 ====================
		this.repositionButton = this.addButton(new ButtonWidget(
				leftX, 30, w, h,
				this.noise_gen_debug$repositionText(),
				b -> {
					RepositionState.toggleEnabled();
					this.noise_gen_debug$refreshAll();
				}));

		this.axisYButton = this.addButton(new ButtonWidget(
				rightX, 30, w, h,
				this.noise_gen_debug$axisYText(),
				b -> {
					RepositionState.toggleAffectY();
					this.noise_gen_debug$refreshAll();
				}));

		this.scaleXField = this.noise_gen_debug$makeField(
				leftX, 54, w, "noise_gen_debug.reposition.scaleX", 0, this::noise_gen_debug$onScaleTyped);
		this.scaleZField = this.noise_gen_debug$makeField(
				rightX, 54, w, "noise_gen_debug.reposition.scaleZ", 2, this::noise_gen_debug$onScaleTyped);
		this.offsetXField = this.noise_gen_debug$makeField(
				leftX, 76, w, "noise_gen_debug.reposition.offsetX", 0, this::noise_gen_debug$onOffsetTyped);
		this.offsetZField = this.noise_gen_debug$makeField(
				rightX, 76, w, "noise_gen_debug.reposition.offsetZ", 2, this::noise_gen_debug$onOffsetTyped);

		// 预设
		final int rpY = 100;
		this.addButton(new ButtonWidget(leftX, rpY, w, h,
				new TranslatableText("noise_gen_debug.reposition.preset.reset"),
				b -> this.noise_gen_debug$applyReposition(false, 1.0D, 1.0D, 0.0D, 0.0D)));

		this.addButton(new ButtonWidget(rightX, rpY, w, h,
				new TranslatableText("noise_gen_debug.reposition.preset.far"),
				b -> this.noise_gen_debug$applyReposition(true, 1.0e18D, 1.0e18D, 0.0D, 0.0D)));

		// 在可表示的极限附近，用来观察「分层超平坦」的成因。
		this.addButton(new ButtonWidget(leftX, rpY + 22, w, h,
				new TranslatableText("noise_gen_debug.reposition.preset.saturate"),
				b -> this.noise_gen_debug$applyReposition(true, 1.0e24D, 1.0e24D, 0.0D, 0.0D)));

		// ⚠️ 这个区间的目标不是 Infinity。天空网格要的是「极大<b>有限</b>值」：
		//    1e30 级坐标 → perlinFade ≈ 6e150（有限）→ lerp3 溢出成 ±Inf ≈ 可用；
		//    而真正的 Infinity 一进 Perlin 就被 perlinFade(Inf)=Inf → lerp3 的 Inf*0 打成 NaN。
		//    所以 1e300 是错的（实测 NaN）；上限已在 RepositionState.MAX_SCALE 定在 1e30。
		//    另：perlinFade(t) ≈ t^5，NaN 悬崖在 t ≈ 4.5e61。
		this.addButton(new ButtonWidget(rightX, rpY + 22, w, h,
				new TranslatableText("noise_gen_debug.reposition.preset.extreme"),
				b -> this.noise_gen_debug$applyReposition(true, 1.0e30D, 1.0e30D, 0.0D, 0.0D)));

		// ==================== maintainPrecision ====================
		final int mpY = 132;
		this.modeButton = this.addButton(new ButtonWidget(
				leftX, mpY, w, h,
				this.noise_gen_debug$modeText(),
				b -> {
					NoiseParamsState.setFarLandsMode(
							FarLandsMode.next(NoiseParamsState.getFarLandsMode()));
					this.noise_gen_debug$refreshAll();
				}));

		this.divisorField = new TextFieldWidget(this.textRenderer,
				rightX, mpY, w, 18,
				new TranslatableText("noise_gen_debug.farlands.divisor"));
		this.divisorField.setMaxLength(20);
		this.divisorField.setChangedListener(this::noise_gen_debug$onDivisorTyped);
		this.addButton(this.divisorField);

		// ==================== 天空网格 ====================
		//
		// 机制：Infinity 密度 + NaN 安全插值（详见 MathUtil 的类注释）。
		// 两个开关分工不同，建议都开。
		final int sgY = mpY + 24;
		this.skyGridButton = this.addButton(new ButtonWidget(
				leftX, sgY, w, h,
				this.noise_gen_debug$skyGridText(),
				b -> {
					SkyGridState.toggleEnabled();
					this.noise_gen_debug$refreshAll();
				}));

		this.forceClampButton = this.addButton(new ButtonWidget(
				rightX, sgY, w, h,
				this.noise_gen_debug$forceClampText(),
				b -> {
					SkyGridState.toggleForceClamp();
					this.noise_gen_debug$refreshAll();
				}));

		// 「边缘之地级联」才是网格的真正来源 —— 上面两个开关只影响插值，
		// 不开这个就永远出不来。
		this.fringeCascadeButton = this.addButton(new ButtonWidget(
				leftX, sgY + 22, w, h,
				this.noise_gen_debug$fringeCascadeText(),
				b -> {
					SkyGridState.toggleFringeCascade();
					this.noise_gen_debug$refreshAll();
				}));

		// ==================== 噪声生成器 ====================
		//
		// 换掉整个噪声引擎：选中后 `sampleNoise`（3D 密度的唯一出口）
		// 直接交给它，原版 Perlin 完全不参与。
		// 与上面两个开关正交 —— 这里换的是「用什么噪声」，不是「怎么插值」。
		this.noiseGenButton = this.addButton(new ButtonWidget(
				rightX, sgY + 22, w, h,
				this.noise_gen_debug$noiseGenText(),
				b -> {
					NoiseGenRegistry.cycle();
					this.noise_gen_debug$refreshAll();
				}));

		// 坐标折叠 —— 在整数饱和前把坐标折回来，把「二值墙」换成可控周期的网格。
		// 只对会爆炸的生成器（Value）有意义。foldLength=16 就是 FLT 的天空网格。
		this.foldButton = this.addButton(new ButtonWidget(
				rightX, sgY + 44, w, h,
				this.noise_gen_debug$foldText(),
				b -> {
					LatticeNoiseProvider.toggleFold();
					this.noise_gen_debug$refreshAll();
				}));

		// ==================== 底部 ====================
		final int footerY = this.height - 28;
		this.addButton(new ButtonWidget(leftX, footerY, w, h,
				new TranslatableText("noise_gen_debug.button.reset"),
				b -> {
					RepositionState.reset();
					SkyGridState.reset();
					NoiseGenRegistry.reset();
					LatticeNoiseProvider.setFoldEnabled(false);
					NoiseParamsState.setFarLandsMode(FarLandsMode.DEFAULT);
					NoiseParamsState.setMaintainPrecisionDivisor(FarLandsMode.VANILLA_DIVISOR);
					this.noise_gen_debug$refreshAll();
				}));

		this.addButton(new ButtonWidget(rightX, footerY, w, h,
				new TranslatableText("noise_gen_debug.button.done"),
				b -> this.onClose()));

		this.noise_gen_debug$refreshAll();
	}

	/** 造一个带标签的输入框。 */
	private TextFieldWidget noise_gen_debug$makeField(int x, int y, int w,
			String labelKey, int axis, java.util.function.Consumer<String> listener) {
		TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, 18,
				new TranslatableText(labelKey));
		f.setMaxLength(24);
		f.setChangedListener(listener);
		this.addButton(f);
		return f;
	}

	// ==================== 输入 ====================

	private void noise_gen_debug$onScaleTyped(String text) {
		if (this.suppress) {
			return;
		}
		double v;
		try {
			v = Double.parseDouble(text.trim());
		} catch (NumberFormatException e) {
			return;
		}
		// 只解析一个值就同时写入 X/Z 会出错，所以按控件来源分别处理 —— 见下方 refresh。
		this.noise_gen_debug$parseAndApplyScale();
	}

	private void noise_gen_debug$onOffsetTyped(String text) {
		if (this.suppress) {
			return;
		}
		this.noise_gen_debug$parseAndApplyOffset();
	}

	private void noise_gen_debug$onDivisorTyped(String text) {
		if (this.suppress) {
			return;
		}
		double d;
		try {
			d = Double.parseDouble(text.trim());
		} catch (NumberFormatException e) {
			return;
		}
		if (Double.isNaN(d) || Double.isInfinite(d) || d < 0.0D) {
			return;
		}
		NoiseParamsState.setFarLandsMode(FarLandsMode.CUSTOM);
		NoiseParamsState.setMaintainPrecisionDivisor(d);
		this.noise_gen_debug$refreshButtonsOnly();
	}

	private void noise_gen_debug$parseAndApplyScale() {
		Double sx = noise_gen_debug$parse(this.scaleXField.getText());
		Double sz = noise_gen_debug$parse(this.scaleZField.getText());
		this.lastRejected = null;
		if (sx != null) {
			if (!RepositionState.setScale(0, sx)) {
				this.lastRejected = "noise_gen_debug.reposition.range.scale";
			}
		}
		if (sz != null) {
			if (!RepositionState.setScale(2, sz)) {
				this.lastRejected = "noise_gen_debug.reposition.range.scale";
			}
		}
		// 手输数值即视为「启用」。否则 enabled 这个标志没有入口，
		// 输入的值会静默失效（mixin 里的 noise_gen_debug$reposition 现在会检查它）。
		if (RepositionState.isModified()) {
			RepositionState.setEnabled(true);
		}
		this.noise_gen_debug$refreshButtonsOnly();
	}

	private void noise_gen_debug$parseAndApplyOffset() {
		Double ox = noise_gen_debug$parse(this.offsetXField.getText());
		Double oz = noise_gen_debug$parse(this.offsetZField.getText());
		this.lastRejected = null;
		if (ox != null) {
			if (!RepositionState.setOffset(0, ox)) {
				this.lastRejected = "noise_gen_debug.reposition.range.offset";
			}
		}
		if (oz != null) {
			if (!RepositionState.setOffset(2, oz)) {
				this.lastRejected = "noise_gen_debug.reposition.range.offset";
			}
		}
		// 同上：手输偏移也视为启用。
		if (RepositionState.isModified()) {
			RepositionState.setEnabled(true);
		}
		this.noise_gen_debug$refreshButtonsOnly();
	}

	private static Double noise_gen_debug$parse(String s) {
		try {
			double v = Double.parseDouble(s.trim());
			return (Double.isNaN(v) || Double.isInfinite(v)) ? null : v;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void noise_gen_debug$applyReposition(boolean on, double sx, double sz,
			double ox, double oz) {
		RepositionState.setEnabled(on);
		RepositionState.setScale(0, sx);
		RepositionState.setScale(2, sz);
		RepositionState.setOffset(0, ox);
		RepositionState.setOffset(2, oz);
		this.noise_gen_debug$refreshAll();
	}

	// ==================== 刷新 ====================

	private void noise_gen_debug$refreshAll() {
		this.noise_gen_debug$refreshButtonsOnly();

		boolean prev = this.suppress;
		this.suppress = true;
		try {
			noise_gen_debug$setField(this.scaleXField, RepositionState.getScale(0));
			noise_gen_debug$setField(this.scaleZField, RepositionState.getScale(2));
			noise_gen_debug$setField(this.offsetXField, RepositionState.getOffset(0));
			noise_gen_debug$setField(this.offsetZField, RepositionState.getOffset(2));
			noise_gen_debug$setField(this.divisorField,
					NoiseParamsState.getMaintainPrecisionDivisor());
		} finally {
			this.suppress = prev;
		}
	}

	/**
	 * 写入文本并把光标移到末尾。
	 *
	 * <p>{@code TextFieldWidget} 在文本长于可视宽度时<b>只画光标附近的一小段</b>，
	 * 光标默认在最左 → 你只看到开头一个字符，看起来像「输入被吃掉了」。
	 * 光标停在末尾才能看到完整值。
	 */
	private static void noise_gen_debug$setField(TextFieldWidget f, double v) {
		f.setText(noise_gen_debug$fmt(v));
		f.setCursorToEnd();
	}

	private void noise_gen_debug$refreshButtonsOnly() {
		if (this.repositionButton != null) {
			this.repositionButton.setMessage(this.noise_gen_debug$repositionText());
		}
		if (this.axisYButton != null) {
			this.axisYButton.setMessage(this.noise_gen_debug$axisYText());
		}
		if (this.modeButton != null) {
			this.modeButton.setMessage(this.noise_gen_debug$modeText());
		}
		if (this.skyGridButton != null) {
			this.skyGridButton.setMessage(this.noise_gen_debug$skyGridText());
		}
		if (this.forceClampButton != null) {
			this.forceClampButton.setMessage(this.noise_gen_debug$forceClampText());
		}
		if (this.fringeCascadeButton != null) {
			this.fringeCascadeButton.setMessage(this.noise_gen_debug$fringeCascadeText());
		}
		if (this.noiseGenButton != null) {
			this.noiseGenButton.setMessage(this.noise_gen_debug$noiseGenText());
		}
		if (this.foldButton != null) {
			this.foldButton.setMessage(this.noise_gen_debug$foldText());
		}
	}

	/**
	 * 把数值格式化成输入框里看得见的形式。
	 *
	 * <h2>为什么不用 {@code (long) v}</h2>
	 *
	 * <p>早先这里用 {@code (long) v}，于是 {@code 1e9} 显示成 "1000000000"（10 个字符）。
	 * {@code TextFieldWidget} 在文本长于可视宽度时只画光标附近的一小段，
	 * 光标默认在最左 —— 于是你只看到开头的 "1"，看起来就像「输入被吃掉了」。
	 * 其实模型里存的值是对的。
	 *
	 * <h2>为什么不用 {@code String.format("%g")}</h2>
	 *
	 * <p>{@code %g} 默认只保留 <b>6 位有效数字</b>，会把
	 * {@code 33554432}（= 2^25，maintainPrecision 的除数）写成
	 * "3.35544E7" —— <b>回读变成 33554400，精度丢了</b>。
	 * 对噪声参数来说这是不可接受的。
	 *
	 * <h2>为什么用 {@code BigDecimal}</h2>
	 *
	 * <p>{@code new BigDecimal(v).toString()} 给出的是<b>能精确还原该 double 的
最短十进制</b>
	 * （JLS 的 Double.toString 规范），
	 * 且大数会自动转科学计数法：{@code 1e9} → "1.0E+P9"。
	 * 这里再把 "1.0E+9" 规整成 "1E9"，既短又精确。
	 */
	private static String noise_gen_debug$fmt(double v) {
		if (Double.isNaN(v) || Double.isInfinite(v)) {
			return "0";
		}
		// Double.toString 给出「能精确还原该 double 的最短十进制」，绝不丢精度。
		String s = String.valueOf(v);
		int e = s.indexOf('E');
		String mantissa;
		String exp;
		if (e >= 0) {
			mantissa = s.substring(0, e);
			exp = s.substring(e + 1);
		} else {
			// 没带指数时，判断是否需要自己转成科学计数法。
			// 超过 7 个整数位（如 1000000000）在输入框里太占地方。
			int dot = s.indexOf('.');
			int intDigits = dot >= 0 ? dot : s.length();
			if (intDigits > 7) {
				// 手工转科学计数法：保留足够有效数字以精确往返，故直接用
				// 原始字符串移位，而不是重新计算（避免引入误差）。
				String digits = s.replace("-", "").replace(".", "");
				// 去掉前导零
				int lead = 0;
				while (lead < digits.length() - 1 && digits.charAt(lead) == '0') {
					lead++;
				}
				digits = digits.substring(lead);
				// 去掉尾随零（只在有小数部分时安全）
				if (dot >= 0) {
					while (digits.length() > 1 && digits.endsWith("0")) {
						digits = digits.substring(0, digits.length() - 1);
					}
				}
				int exponent = intDigits - 1 - lead;
				mantissa = digits.substring(0, 1);
				if (digits.length() > 1) {
					mantissa = mantissa + "." + digits.substring(1);
				}
				exp = String.valueOf(exponent);
				return (v < 0 ? "-" : "") + mantissa + "E" + exp;
			}
			// 小数值：<b>不要截断</b>。
			//
			// 曾试图把小数截到 9 位「让显示好看点」，结果 0.9999999814507745
			// 变成 0.999999981 —— xzScale 的默认值正好是这个数，
			// 截断会让回读值与真值不符，直接改变地形。
			//
			// Double.toString 给的是「能精确还原的最短十进制」，本来就该直接用。
			return s;
		}
		// 带指数的：规整 "1.0E9" -> "1E9"
		if (mantissa.contains(".")) {
			mantissa = mantissa.replaceAll("0+$", "");
			if (mantissa.endsWith(".")) {
				mantissa = mantissa.substring(0, mantissa.length() - 1);
			}
		}
		boolean neg = exp.startsWith("-");
		if (neg || exp.startsWith("+")) {
			exp = exp.substring(1);
		}
		while (exp.length() > 1 && exp.charAt(0) == '0') {
			exp = exp.substring(1);
		}
		return mantissa + "E" + (neg ? "-" : "") + exp;
	}

	// ==================== 文案 ====================

	private Text noise_gen_debug$repositionText() {
		return new TranslatableText(RepositionState.isEnabled()
				? "noise_gen_debug.reposition.on"
				: "noise_gen_debug.reposition.off");
	}

	private Text noise_gen_debug$axisYText() {
		return new TranslatableText(RepositionState.isAffectY()
				? "noise_gen_debug.reposition.axisY.on"
				: "noise_gen_debug.reposition.axisY.off");
	}

	private Text noise_gen_debug$skyGridText() {
		return new TranslatableText(SkyGridState.isEnabled()
				? "noise_gen_debug.skygrid.on"
				: "noise_gen_debug.skygrid.off");
	}

	private Text noise_gen_debug$forceClampText() {
		return new TranslatableText(SkyGridState.isForceClamp()
				? "noise_gen_debug.skygrid.force.on"
				: "noise_gen_debug.skygrid.force.off");
	}

	private Text noise_gen_debug$fringeCascadeText() {
		return new TranslatableText(SkyGridState.isFringeCascade()
				? "noise_gen_debug.skygrid.fringe.on"
				: "noise_gen_debug.skygrid.fringe.off");
	}

	private Text noise_gen_debug$modeText() {
		FarLandsMode mode = NoiseParamsState.getFarLandsMode();
		return new TranslatableText("noise_gen_debug.farlands.mode." + mode.name().toLowerCase());
	}

	private Text noise_gen_debug$noiseGenText() {
		return new TranslatableText(NoiseGenRegistry.langKey());
	}

	private Text noise_gen_debug$foldText() {
		if (!LatticeNoiseProvider.isFoldEnabled()) {
			return new TranslatableText("noise_gen_debug.valuefold.off");
		}
		return new TranslatableText("noise_gen_debug.valuefold.on",
				LatticeNoiseProvider.getFoldLength());
	}

	// ==================== 渲染 ====================

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);

		// 把当前映射写成一行看得懂的式子。
		String line = String.format("x' = x * %s + %s      z' = z * %s + %s",
				noise_gen_debug$fmt(RepositionState.getScale(0)),
				noise_gen_debug$fmt(RepositionState.getOffset(0)),
				noise_gen_debug$fmt(RepositionState.getScale(2)),
				noise_gen_debug$fmt(RepositionState.getOffset(2)));
		drawCenteredText(matrices, this.textRenderer, new LiteralText(line),
				this.width / 2, this.height - 58,
				RepositionState.isEnabled() ? 0xFF80FF80 : 0xFFA0A0A0);

		// 超范围提示（红）
		if (this.lastRejected != null) {
			drawCenteredText(matrices, this.textRenderer,
					new TranslatableText(this.lastRejected), this.width / 2,
					this.height - 70, 0xFFFF5555);
		}

		// 饱和警告：这是「越远越变成分层超平坦」的成因
		if (RepositionState.isEnabled() && RepositionState.isScaleSaturating()) {
			drawCenteredText(matrices, this.textRenderer,
					new TranslatableText("noise_gen_debug.reposition.warn.saturate"),
					this.width / 2, this.height - 46, 0xFFFFAA00);
		} else {
			String note = new TranslatableText("noise_gen_debug.farlands.note").getString();
			drawCenteredText(matrices, this.textRenderer, new LiteralText(note),
					this.width / 2, this.height - 46, 0xFF808080);
		}

		super.render(matrices, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		if (this.client != null) {
			this.client.openScreen(this.parent);
		}
	}
}
