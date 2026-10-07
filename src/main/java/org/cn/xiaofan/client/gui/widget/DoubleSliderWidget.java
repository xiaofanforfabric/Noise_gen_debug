package org.cn.xiaofan.client.gui.widget;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

/**
 * 通用 double 滑块。
 *
 * <p>1.16.5 的 {@link SliderWidget} 只存 {@code value ∈ [0,1]}，
 * 实际数值需要自己做线性映射。本类把它封装成"直接读写真实数值"，
 * 并支持自定义显示格式（例如保留几位小数、显示整数）。
 *
 * <p>坐标/标签文字由调用方给出，数值变化时通过 {@code setter} 立刻写回
 * {@link org.cn.xiaofan.noise.NoiseParams}。
 */
public class DoubleSliderWidget extends SliderWidget {

	private final String labelKey;
	private final double min;
	private final double max;
	private final int decimals;
	private final DoubleSupplier getter;
	private final DoubleConsumer setter;
	/** 是否只允许整数（如 height / sizeH / sizeV / slide 值）。 */
	private final boolean integerOnly;

	public DoubleSliderWidget(int x, int y, int width, int height,
			String labelKey,
			double min, double max,
			int decimals,
			boolean integerOnly,
			DoubleSupplier getter,
			DoubleConsumer setter) {
		super(x, y, width, height, new LiteralText(labelKey), toSlider(min, max, getter.getAsDouble()));
		this.labelKey = labelKey;
		this.min = min;
		this.max = max;
		this.decimals = decimals;
		this.integerOnly = integerOnly;
		this.getter = getter;
		this.setter = setter;
		this.updateMessage();
	}

	/** 真实值 → [0,1] 滑块位置。 */
	private static double toSlider(double min, double max, double value) {
		if (max <= min) {
			return 0.0D;
		}
		return (value - min) / (max - min);
	}

	/** [0,1] 滑块位置 → 真实值（含取整与夹取）。 */
	private double toValue() {
		double raw = this.min + this.value * (this.max - this.min);
		if (this.integerOnly) {
			raw = Math.round(raw);
		}
		return Math.max(this.min, Math.min(this.max, raw));
	}

	@Override
	protected void updateMessage() {
		Text valueText = new LiteralText(formatValue(this.toValue()));
		this.setMessage(new TranslatableText(this.labelKey).append(": ").append(valueText));
	}

	private String formatValue(double v) {
		if (this.integerOnly || this.decimals <= 0) {
			return String.valueOf((long) Math.round(v));
		}
		return String.format("%." + this.decimals + "f", v);
	}

	@Override
	protected void applyValue() {
		this.setter.accept(this.toValue());
	}

	/** 外部（如重置）改动参数后，同步滑块位置。 */
	public void refreshFromModel() {
		double target = this.getter.getAsDouble();
		double clamped = Math.max(this.min, Math.min(this.max, target));
		this.value = toSlider(this.min, this.max, clamped);
		this.updateMessage();
	}
}
