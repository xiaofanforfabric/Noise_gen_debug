package org.cn.xiaofan.client;

/**
 * 按轴 32 位浮点降级工具（放在 {@code client} 包，<b>不能</b>放 {@code mixin} 包）。
 *
 * <h2>为什么不能放 mixin 包</h2>
 *
 * <p>{@code org.cn.xiaofan.mixin.*} 整个包被 {@code noise_gen_debug.mixins.json}
 * 声明为 mixin 包后，Mixin 会接管其中所有类的加载。任何<b>没有</b> {@code @Mixin}
 * 注解的类只要被普通代码引用，就会抛：
 * <pre>
 * IllegalClassLoadError: ... is in a defined mixin package ... and cannot be referenced directly
 * </pre>
 * 所以工具类必须放在别的包。
 *
 * <h2>降级的含义</h2>
 *
 * <p>{@code (double)(float) v} —— 把值降级到 32 位浮点再升回 double。
 * 当 {@code |v| > 3.4e38}（float 上限）时得到 {@code ±Inf}，
 * 这正是基岩版在边境之地会发生的溢出。
 */
public final class FloatDegrade {

	private FloatDegrade() {
	}

	/**
	 * 按轴决定是否降级为 32 位 float。
	 *
	 * @param value 原始（64 位）值
	 * @param axis  0 = X, 1 = Z, 2 = Y
	 * @return 该轴开启 32 位模式时返回 float 精度值，否则原值
	 */
	public static double apply(double value, int axis) {
		boolean on;
		switch (axis) {
			case 0:
				on = NoiseParamsState.isFloat32X();
				break;
			case 1:
				on = NoiseParamsState.isFloat32Z();
				break;
			case 2:
				on = NoiseParamsState.isFloat32Y();
				break;
			default:
				return value;
		}
		if (!on) {
			return value;
		}
		double degraded = (double) (float) value;
		NoisePrecisionStats.record(degraded);
		return degraded;
	}
}
