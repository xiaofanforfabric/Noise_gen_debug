package org.cn.xiaofan.client;

import org.cn.xiaofan.noise.NoiseParams;

/**
 * 客户端侧的「待应用噪声参数」暂存区。
 *
 * <p>创建世界页面（{@code CreateWorldScreen}）上点开参数编辑界面后，编辑结果先放这里；
 * 真正写进 generator 的时机在创建存档时（后续实现）。
 */
public final class NoiseParamsState {

	/** 当前待应用的参数。默认 = 原版 OVERWORLD 预设。 */
	private static NoiseParams pending = NoiseParams.overworld();

	/**
	 * 是否禁用「坐标精度折叠」（FARLANDS 保护）。
	 *
	 * <p>原版 {@code OctavePerlinNoiseSampler.maintainPrecision} 会把坐标对
	 * {@code 2^25 = 33554432} 取模折算，从而把噪声的周期性异常推到极远处。
	 * 关掉它，噪声在 {@code 2^25} 量级就会开始重复／退化，形成类似 FARLANDS 的地形。
	 *
	 * <p>这是纯调试开关，不属于存档 generator 参数，所以不入序列化。
	 */
	private static boolean disablePrecisionFolding;

	/** maintainPrecision 的折叠模式（控制边境之地位置/形态）。 */
	private static org.cn.xiaofan.noise.FarLandsMode farLandsMode =
			org.cn.xiaofan.noise.FarLandsMode.DEFAULT;

	/** CUSTOM 模式下的除数。默认 2^25。 */
	private static double maintainPrecisionDivisor = 3.3554432E7D;

	/** 是否限制 maintainPrecision 的返回值上限。 */
	private static boolean limitReturnValue;

	/** 返回值上限的指数（10^n），默认 7。 */
	private static int maxNoiseLogarithmValue = 7;

	/**
	 * 是否禁用「平滑器」。
	 *
	 * <p>原版噪声只在稀疏网格上采样（XZ 每 4 格、Y 每 8 格），再用三线性插值
	 * （{@code MathHelper.lerp3} / 嵌套 {@code lerp}）填满整块，这就是"平滑"。
	 * 关掉平滑 = 把插值权重强制为 0，等价于每格直接取最近的网格角点值，
	 * 地形会变成 4×8×4 的块状台阶。
	 *
	 * <p>同样是纯调试开关，不入序列化。
	 */
	private static boolean disableSmoothing;

	/**
	 * 是否解除世界边界限制。
	 *
	 * <p>原版把坐标卡在 ±30000000、世界边界半径卡在 29999984，导致根本走不到 FARLANDS。
	 * 打开后把相关限制拉到 int/double/Float 最大值。
	 *
	 * <p>注意：世界边界尺寸在 {@code World} 构造时写入，所以这个开关只对
	 * **之后新建/加载的世界** 生效。
	 */
	private static boolean worldBorderDisabled;
	/** 极端缩放保护：把 sampleNoise 的 NaN/Inf 替换为负无穷，避免整片崩坏。 */
	private static boolean extremeDensityGuard = true;
	/** 按轴 32 位浮点模拟（复现基岩版 perlinFade 溢出）。 */
	/**
	 * 按轴启用 32 位浮点模拟的位掩码。
	 *
	 * <p>bit0 = X, bit1 = Z, bit2 = Y（顺序对应 {@code sampleNoise} 里的三次
	 * {@code maintainPrecision} 调用）。0 = 全部 64 位（原版）。
	 */
	private static int floatBits32 = 0;

	private NoiseParamsState() {
	}

	public static NoiseParams get() {
		return pending;
	}

	public static void set(NoiseParams params) {
		pending = params == null ? NoiseParams.overworld() : params;
	}

	public static void reset() {
		pending = NoiseParams.overworld();
	}

	/** 是否偏离了原版预设。 */
	public static boolean isModified() {
		return pending.differsFromOverworld();
	}

	public static boolean isPrecisionFoldingDisabled() {
		return disablePrecisionFolding;
	}

	public static org.cn.xiaofan.noise.FarLandsMode getFarLandsMode() {
		return farLandsMode;
	}

	public static void setFarLandsMode(org.cn.xiaofan.noise.FarLandsMode mode) {
		farLandsMode = mode == null ? org.cn.xiaofan.noise.FarLandsMode.DEFAULT : mode;
	}

	public static double getMaintainPrecisionDivisor() {
		return maintainPrecisionDivisor;
	}

	public static void setMaintainPrecisionDivisor(double divisor) {
		maintainPrecisionDivisor = divisor;
	}

	public static boolean isLimitReturnValue() {
		return limitReturnValue;
	}

	public static void setLimitReturnValue(boolean limit) {
		limitReturnValue = limit;
	}

	public static int getMaxNoiseLogarithmValue() {
		return maxNoiseLogarithmValue;
	}

	public static void setMaxNoiseLogarithmValue(int n) {
		maxNoiseLogarithmValue = Math.max(1, Math.min(15, n));
	}

	public static void setPrecisionFoldingDisabled(boolean disabled) {
		disablePrecisionFolding = disabled;
	}

	public static boolean isSmoothingDisabled() {
		return disableSmoothing;
	}

	public static void setSmoothingDisabled(boolean disabled) {
		disableSmoothing = disabled;
	}

	public static boolean isWorldBorderDisabled() {
		return worldBorderDisabled;
	}

	public static void setWorldBorderDisabled(boolean disabled) {
		worldBorderDisabled = disabled;
	}

	/** 极端缩放时是否把 NaN / Inf 密度夹成虚空（默认开）。 */
	public static boolean isExtremeDensityGuardEnabled() {
		return extremeDensityGuard;
	}

	public static void setExtremeDensityGuardEnabled(boolean enabled) {
		extremeDensityGuard = enabled;
	}

	/** 浮点精度开关：true = 32 位（基岩版模拟），false = 64 位（原版）。 */
	/** bit0 = X 轴启用 32 位。 */
	public static boolean isFloat32X() {
		return (floatBits32 & 1) != 0;
	}

	/** bit1 = Z 轴启用 32 位。 */
	public static boolean isFloat32Z() {
		return (floatBits32 & 2) != 0;
	}

	/** bit2 = Y 轴启用 32 位。 */
	public static boolean isFloat32Y() {
		return (floatBits32 & 4) != 0;
	}

	/** 任意轴启用 32 位。 */
	public static boolean isFloatPrecisionEnabled() {
		return floatBits32 != 0;
	}

	public static int getFloatBits32() {
		return floatBits32;
	}

	public static void setFloatBits32(int bits) {
		floatBits32 = bits & 7;
	}

	/** 切换某一轴（0 = X, 1 = Z, 2 = Y）。 */
	public static void toggleFloat32Axis(int axis) {
		floatBits32 ^= (1 << axis);
	}

	/** 全开 / 全关。 */
	public static void toggleFloat32All() {
		floatBits32 = (floatBits32 == 7) ? 0 : 7;
	}
}
