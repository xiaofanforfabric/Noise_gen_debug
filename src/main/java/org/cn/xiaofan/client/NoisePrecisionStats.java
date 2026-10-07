package org.cn.xiaofan.client;

/**
 * float 精度降级实验的计数器（放在普通类里，不放 mixin）。
 *
 * <p>Mixin 类里的方法必须是 {@code private}，否则合并进目标类时会污染其 API，
 * Mixin 会抛 {@code InvalidMixinException: contains non-private static method}。
 * 所以计数器和查询接口独立出来，mixin 只用 {@code private} 方法往里写。
 *
 * <p>区块生成是多线程的，这里用 {@code volatile} 做宽松计数：
 * 我们只关心「有没有出现 Inf/NaN」，不要求精确。
 */
public final class NoisePrecisionStats {

	private static volatile long infCount;
	private static volatile long nanCount;
	private static volatile long totalCount;

	private NoisePrecisionStats() {
	}

	/** 记录一次采样。 */
	public static void record(double value) {
		totalCount++;
		if (Double.isInfinite(value)) {
			infCount++;
		} else if (Double.isNaN(value)) {
			nanCount++;
		}
	}

	/** 返回 {@code [Inf, NaN, total]} 快照。 */
	public static long[] snapshot() {
		return new long[]{infCount, nanCount, totalCount};
	}

	/** 清零（GUI 切换开关时调用）。 */
	public static void reset() {
		infCount = 0L;
		nanCount = 0L;
		totalCount = 0L;
	}
}
