package org.cn.xiaofan.client;

/**
 * 天空网格 / 边缘之地开关。
 *
 * <h2>三层机制（按重要性排序）</h2>
 *
 * <ol>
 *   <li>{@link #isFringeCascade()} —— <b>真正决定网格出不出来的那一层</b>。
 *       开启后 {@code NoiseChunkGenerator.sampleNoise} 被整段接管，
 *       按 FarLandsTraveler 的 26 个盒子逐块切换噪声参数，
 *       并在方块坐标上做 {@code repeat} 折叠 ——
 *       {@code repeatLength = 16} ⇒ 每 16 格重复 ⇒ 网格。
 *       见 {@link org.cn.xiaofan.noise.FringeCascade}。</li>
 *   <li>{@link #isEnabled()} —— 对应 FLT 的 {@code ENABLE_SKY_GRID}：
 *       把插值换成 NaN 安全版本（{@link org.cn.xiaofan.noise.MathUtil}）。
 *       它只负责「让极值活着穿过插值」，<b>本身不生产网格</b>。</li>
 *   <li>{@link #isForceClamp()} —— 对应 {@code FORCE_SKY_GRID}：
 *       即使密度在插值前被 clamp 掉，也强行放行无穷值。</li>
 * </ol>
 *
 * <p><b>只开 ②③ 是没用的</b>：没有 ① 喂网格形状，NaN 安全插值什么也做不出来。
 */
public final class SkyGridState {

	private SkyGridState() {
	}

	private static boolean enabled;

	/**
	 * 强制放行无穷值。
	 *
	 * <p>原版在 {@code populateNoise} 里有一句
	 * {@code MathHelper.clamp(an / 200.0D, -1.0D, 1.0D)}，
	 * 会把 {@code +Inf} 夹成 {@code 1.0} —— 那样天空网格就出不来了。
	 *
	 * <p>{@link #enabled} 只管插值；这个开关才能让 Infinity 活着走到插值。
	 * 两个都开效果最完整。
	 */
	private static boolean forceClamp;

	/**
	 * 「边缘之地」级联开关 —— 网格的真正来源。
	 *
	 * <p>开启后 {@code sampleNoise} 会被 {@code FringeCascadeMixin} 整段接管，
	 * 按 26 个硬编码盒子的参数重算混合噪声，并做 16 格周期的坐标折叠。
	 * 关掉时零开销（直接走原版）。
	 */
	private static boolean fringeCascade;

	public static boolean isFringeCascade() {
		return fringeCascade;
	}

	public static void setFringeCascade(boolean v) {
		fringeCascade = v;
	}

	public static void toggleFringeCascade() {
		fringeCascade = !fringeCascade;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean v) {
		enabled = v;
	}

	public static void toggleEnabled() {
		enabled = !enabled;
	}

	public static boolean isForceClamp() {
		return forceClamp;
	}

	public static void setForceClamp(boolean v) {
		forceClamp = v;
	}

	public static void toggleForceClamp() {
		forceClamp = !forceClamp;
	}

	public static void reset() {
		enabled = false;
		forceClamp = false;
		fringeCascade = false;
	}
}
