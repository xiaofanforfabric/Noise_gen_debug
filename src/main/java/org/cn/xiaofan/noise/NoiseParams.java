package org.cn.xiaofan.noise;

import java.math.BigDecimal;

import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.chunk.NoiseSamplingConfig;
import net.minecraft.world.gen.chunk.SlideConfig;

/**
 * 可编辑的噪声参数集合（1.16.5）。
 *
 * <p>对应原版 {@link GenerationShapeConfig} 的可调字段，外加 {@code amplified} 这个
 * 影响地形放大的开关。默认值取自 {@code ChunkGeneratorSettings} 的 OVERWORLD 预设，
 * 见 {@link #overworld()}。
 *
 * <p>本类只描述「参数」，不负责序列化与生效。生效方式是把参数打包成一份内联的
 * {@code ChunkGeneratorSettings}，再重建 {@code NoiseChunkGenerator}（见后续实现）。
 */
public final class NoiseParams {

	// ---- GenerationShapeConfig ----
	/** 世界高度，原版 256。 */
	public int height = 256;
	/** 水平采样尺寸（1..4），原版 1。越大横向越粗糙。 */
	public int sizeHorizontal = 1;
	/** 垂直采样尺寸（1..4），原版 2。越大纵向越粗糙。 */
	public int sizeVertical = 2;
	/** 密度系数，原版 1.0。 */
	public double densityFactor = 1.0D;
	/** 密度偏移，原版 -0.46875。调大整体地形更高。 */
	public double densityOffset = -0.46875D;
	/** 地表是否用 simplex 噪声，原版 true。 */
	public boolean simplexSurfaceNoise = true;
	/** 是否随机密度偏移，原版 true。 */
	public boolean randomDensityOffset = true;
	/** 岛屿噪声覆盖（末地类地形），原版 false。 */
	public boolean islandNoiseOverride;
	/** 放大化地形，原版 false（amplified 预设为 true）。 */
	public boolean amplified;

	// ---- NoiseSamplingConfig ----
	/** xz 缩放基准，原版 0.9999999814507745。调大＝地形横向拉伸更平缓。 */
	public double xzScale = 0.9999999814507745D;
	/** y 缩放基准，原版 0.9999999814507745。 */
	public double yScale = 0.9999999814507745D;
	/** xz 因子，原版 80.0。调大＝横向起伏频率更高。 */
	public double xzFactor = 80.0D;
	/** y 因子，原版 160.0。调大＝纵向起伏频率更高。 */
	public double yFactor = 160.0D;

	// ---- topSlide ----
	/** 顶部滑动目标高度，原版 -10。 */
	public int topSlideTarget = -10;
	/** 顶部滑动尺寸，原版 3。 */
	public int topSlideSize = 3;
	/** 顶部滑动偏移，原版 0。 */
	public int topSlideOffset;

	// ---- bottomSlide ----
	/** 底部滑动目标高度，原版 -30。 */
	public int bottomSlideTarget = -30;
	/** 底部滑动尺寸，原版 0。 */
	public int bottomSlideSize;
	/** 底部滑动偏移，原版 0。 */
	public int bottomSlideOffset;

	// ==================== 限制器开关 ====================

	/**
	 * 限制器是否被关闭。
	 *
	 * <p>「关闭限制器」= 把 top/bottom slide 的 {@code size} 置 0，
	 * 因为原版 {@code sampleNoiseColumn} 里的生效条件是 {@code if (size > 0)}。
	 * 关闭时会把原本的 slide 参数备份到下面的 {@code *_Backup} 字段，
	 * 以免用户之前调的值被抹掉。
	 */
	public boolean limiterDisabled;

	// 备份（仅 limiterDisabled 时有意义），不参与 equals/序列化。
	private int topSlideTargetBackup;
	private int topSlideSizeBackup;
	private int topSlideOffsetBackup;
	private int bottomSlideTargetBackup;
	private int bottomSlideSizeBackup;
	private int bottomSlideOffsetBackup;

	/**
	 * 设置是否关闭限制器。
	 *
	 * <p>关闭：备份当前 slide 值 → 全部 size 置 0。
	 * 打开：从备份恢复。
	 */
	public void setLimiterDisabled(boolean disabled) {
		if (disabled == this.limiterDisabled) {
			return;
		}

		if (disabled) {
			// 备份 → 关闭
			this.topSlideTargetBackup = this.topSlideTarget;
			this.topSlideSizeBackup = this.topSlideSize;
			this.topSlideOffsetBackup = this.topSlideOffset;
			this.bottomSlideTargetBackup = this.bottomSlideTarget;
			this.bottomSlideSizeBackup = this.bottomSlideSize;
			this.bottomSlideOffsetBackup = this.bottomSlideOffset;

			this.topSlideSize = 0;
			this.bottomSlideSize = 0;
		} else {
			// 从备份恢复
			this.topSlideTarget = this.topSlideTargetBackup;
			this.topSlideSize = this.topSlideSizeBackup;
			this.topSlideOffset = this.topSlideOffsetBackup;
			this.bottomSlideTarget = this.bottomSlideTargetBackup;
			this.bottomSlideSize = this.bottomSlideSizeBackup;
			this.bottomSlideOffset = this.bottomSlideOffsetBackup;
		}

		this.limiterDisabled = disabled;
	}

	// ==================== 噪声层选择 ====================

	/**
	 * 高/低噪声选择模式。
	 *
	 * <p>1.16.5 的 {@code NoiseChunkGenerator.sampleNoise} 末尾是一次混合：
	 * <pre>
	 * return clampedLerp(d / 512.0, e / 512.0, (f / 10.0 + 1.0) / 2.0);
	 * </pre>
	 * 其中 {@code d} 来自 {@code lowerInterpolatedNoise}（基准/「低」），
	 * {@code e} 来自 {@code upperInterpolatedNoise}（目标/「高」），
	 * {@code f} 是插值权重。所以这三个模式：
	 * <ul>
	 *   <li>{@link #DEFAULT} —— 原版混合</li>
	 *   <li>{@link #LOW_ONLY} —— 恒取 {@code d/512}，只看低噪声层</li>
	 *   <li>{@link #HIGH_ONLY} —— 恒取 {@code e/512}，只看高噪声层</li>
	 * </ul>
	 *
	 * <p>注意：这是**纯调试用途**，会产出**无法正常游玩**的地形。
	 */
	public enum NoiseLayerMode {
		DEFAULT,
		LOW_ONLY,
		HIGH_ONLY;

		/** 循环切换到下一个模式。 */
		public NoiseLayerMode next() {
			NoiseLayerMode[] all = values();
			return all[(this.ordinal() + 1) % all.length];
		}
	}

	/** 当前噪声层选择模式。 */
	public NoiseLayerMode noiseLayerMode = NoiseLayerMode.DEFAULT;

	// ==================== 噪声坐标偏移 ====================

	/**
	 * 噪声采样坐标偏移（**方块坐标**，GUI 直接输入的值）。
	 *
	 * <p>作用：把「世界坐标 → 噪声采样」的映射整体平移，
	 * 于是**出生点 (0,0) 就能生成任意远处的噪声地形**，而玩家自身的坐标仍很小。
	 *
	 * <p><b>注意：这与玩家位置无关。</b>
	 * {@code BlockPos} 是 {@code int} 所以玩家走不到极远处，
	 * 但这里改的是「采样器去查哪里的噪声」—— 那条路径上坐标最后会变成
	 * {@code double}，因此偏移本身可以用很大的数。
	 *
	 * <h2>为什么是 {@code double} 而不是 {@code int}</h2>
	 *
	 * <p>早先这三个字段是 {@code int}，于是偏移被钉在 {@code ±2^31}，
	 * 输不进 {@code 1e18} 这种量级。而它们是**方块坐标的位移量**，
	 * 语义上本就不该有 int 限制 —— 限制只存在于「把它加到 int 坐标上」那一步。
	 *
	 * <p>所以运行时把大范围变换移到 {@code maintainPrecision(double)} 那一层，
	 * 那里坐标是 {@code double}（1e308 量级）。
	 */
	public BigDecimal noiseOffsetX = BigDecimal.ZERO;
	public BigDecimal noiseOffsetY = BigDecimal.ZERO;
	public BigDecimal noiseOffsetZ = BigDecimal.ZERO;

	/** 深拷贝。 */
	public NoiseParams copy() {
		NoiseParams p = new NoiseParams();
		p.height = this.height;
		p.sizeHorizontal = this.sizeHorizontal;
		p.sizeVertical = this.sizeVertical;
		p.densityFactor = this.densityFactor;
		p.densityOffset = this.densityOffset;
		p.simplexSurfaceNoise = this.simplexSurfaceNoise;
		p.randomDensityOffset = this.randomDensityOffset;
		p.islandNoiseOverride = this.islandNoiseOverride;
		p.amplified = this.amplified;
		p.xzScale = this.xzScale;
		p.yScale = this.yScale;
		p.xzFactor = this.xzFactor;
		p.yFactor = this.yFactor;
		p.topSlideTarget = this.topSlideTarget;
		p.topSlideSize = this.topSlideSize;
		p.topSlideOffset = this.topSlideOffset;
		p.bottomSlideTarget = this.bottomSlideTarget;
		p.bottomSlideSize = this.bottomSlideSize;
		p.bottomSlideOffset = this.bottomSlideOffset;
		// 拷贝开关状态与备份，避免拷完后开关失效。
		p.limiterDisabled = this.limiterDisabled;
		p.noiseLayerMode = this.noiseLayerMode;
		p.noiseOffsetX = this.noiseOffsetX;
		p.noiseOffsetY = this.noiseOffsetY;
		p.noiseOffsetZ = this.noiseOffsetZ;
		p.topSlideTargetBackup = this.topSlideTargetBackup;
		p.topSlideSizeBackup = this.topSlideSizeBackup;
		p.topSlideOffsetBackup = this.topSlideOffsetBackup;
		p.bottomSlideTargetBackup = this.bottomSlideTargetBackup;
		p.bottomSlideSizeBackup = this.bottomSlideSizeBackup;
		p.bottomSlideOffsetBackup = this.bottomSlideOffsetBackup;
		return p;
	}

	/** 与 OVERWORLD 预设是否存在差异（有差异才需要内联 settings）。 */
	public boolean differsFromOverworld() {
		// 限制器开关本身就是一处差异。
		if (this.limiterDisabled) {
			return true;
		}

		// 非默认的噪声层模式也是差异。
		if (this.noiseLayerMode != NoiseLayerMode.DEFAULT) {
			return true;
		}

		// 任何坐标偏移也是差异。
		if (isNonZero(this.noiseOffsetX) || isNonZero(this.noiseOffsetY)
				|| isNonZero(this.noiseOffsetZ)) {
			return true;
		}

		NoiseParams d = overworld();
		return this.height != d.height
				|| this.sizeHorizontal != d.sizeHorizontal
				|| this.sizeVertical != d.sizeVertical
				|| Double.compare(this.densityFactor, d.densityFactor) != 0
				|| Double.compare(this.densityOffset, d.densityOffset) != 0
				|| this.simplexSurfaceNoise != d.simplexSurfaceNoise
				|| this.randomDensityOffset != d.randomDensityOffset
				|| this.islandNoiseOverride != d.islandNoiseOverride
				|| this.amplified != d.amplified
				|| Double.compare(this.xzScale, d.xzScale) != 0
				|| Double.compare(this.yScale, d.yScale) != 0
				|| Double.compare(this.xzFactor, d.xzFactor) != 0
				|| Double.compare(this.yFactor, d.yFactor) != 0
				|| this.topSlideTarget != d.topSlideTarget
				|| this.topSlideSize != d.topSlideSize
				|| this.topSlideOffset != d.topSlideOffset
				|| this.bottomSlideTarget != d.bottomSlideTarget
				|| this.bottomSlideSize != d.bottomSlideSize
				|| this.bottomSlideOffset != d.bottomSlideOffset;
	}

	/** 构造原版参数类（后续生效逻辑使用）。 */
	public NoiseSamplingConfig toSampling() {
		return new NoiseSamplingConfig(this.xzScale, this.yScale, this.xzFactor, this.yFactor);
	}

	/** 构造原版顶部滑动配置。 */
	public SlideConfig toTopSlide() {
		return new SlideConfig(this.topSlideTarget, this.topSlideSize, this.topSlideOffset);
	}

	/** 构造原版底部滑动配置。 */
	public SlideConfig toBottomSlide() {
		return new SlideConfig(this.bottomSlideTarget, this.bottomSlideSize, this.bottomSlideOffset);
	}

	/**
	 * 返回原版 OVERWORLD 预设的参数快照。
	 *
	 * <p>数值全部取自 1.16.5 `ChunkGeneratorSettings.getInstance()`（源码实测）：
	 * <pre>
	 * height=256, sampling=(xzScale=0.9999999814507745, yScale=0.9999999814507745,
	 *                       xzFactor=80, yFactor=160),
	 * topSlide=(-10, 3, 0), bottomSlide=(-30, 0, 0),
	 * sizeHorizontal=1, sizeVertical=2,
	 * densityFactor=1.0, densityOffset=-0.46875,
	 * simplexSurfaceNoise=true, randomDensityOffset=true,
	 * islandNoiseOverride=false, amplified=false
	 * </pre>
	 */
	public static NoiseParams overworld() {
		NoiseParams p = new NoiseParams();
		p.height = 256;
		p.sizeHorizontal = 1;
		p.sizeVertical = 2;
		p.densityFactor = 1.0D;
		p.densityOffset = -0.46875D;
		p.simplexSurfaceNoise = true;
		p.randomDensityOffset = true;
		p.islandNoiseOverride = false;
		p.amplified = false;
		p.xzScale = 0.9999999814507745D;
		p.yScale = 0.9999999814507745D;
		p.xzFactor = 80.0D;
		p.yFactor = 160.0D;
		p.topSlideTarget = -10;
		p.topSlideSize = 3;
		p.topSlideOffset = 0;
		p.bottomSlideTarget = -30;
		p.bottomSlideSize = 0;
		p.bottomSlideOffset = 0;
		return p;
	}

	/** 偏移是否非零（null 视为 0）。 */
	private static boolean isNonZero(BigDecimal v) {
		return v != null && v.signum() != 0;
	}

	/** 构造原版形状配置。 */
	public GenerationShapeConfig toShape() {
		return new GenerationShapeConfig(
				this.height,
				this.toSampling(),
				this.toTopSlide(),
				this.toBottomSlide(),
				this.sizeHorizontal,
				this.sizeVertical,
				this.densityFactor,
				this.densityOffset,
				this.simplexSurfaceNoise,
				this.randomDensityOffset,
				this.islandNoiseOverride,
				this.amplified);
	}
}
