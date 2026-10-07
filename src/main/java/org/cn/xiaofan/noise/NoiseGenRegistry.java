package org.cn.xiaofan.noise;

import java.util.ArrayList;
import java.util.List;

import org.cn.xiaofan.noise.provider.LatticeNoiseProvider;
import org.cn.xiaofan.noise.provider.OpenSimplex2Provider;

/**
 * 噪声生成器注册表 —— 「换一个噪声引擎来生成地形」的入口。
 *
 * <h2>怎么接进生成管线</h2>
 *
 * <p>{@code NoiseChunkGenerator.sampleNoise} 是 1.16.5 里 3D 密度的唯一出口
 * （1.18+ 的 {@code BlendedNoise.compute} 等价物）。
 * {@code FringeCascadeMixin} 已经在它的 {@code HEAD} 上做了可取消注入，
 * 于是那里顺带查一次本注册表：非 {@code null} 就整段交给该生成器。
 *
 * <p>这样做的代价是零 —— 没选生成器时就是一次 {@code null} 判断，
 * 且不与既有注入点抢 {@code HEAD}。
 *
 * <h2>状态模型</h2>
 *
 * <p>与项目里其它开关一致：静态状态 + GUI 循环切换。
 * {@code activeIndex == -1} 表示「原版」，这也是默认值。
 */
public final class NoiseGenRegistry {

	/** 已注册的生成器，顺序即 GUI 轮换顺序。 */
	private static final List<NoiseProvider> PROVIDERS = new ArrayList<NoiseProvider>();

	/** {@code -1} = 原版；否则指向 {@link #PROVIDERS} 的下标。 */
	private static int activeIndex = -1;

	static {
		// 内置生成器在这里登记 —— 新增一个噪声引擎 = 加一行。
		//
		// 前三个共用同一套梯子与同一个 hash，只有插值核不同 ⇒ 公平对比。
		register(new LatticeNoiseProvider(LatticeNoiseProvider.Kind.VALUE));
		register(new LatticeNoiseProvider(LatticeNoiseProvider.Kind.PERLIN));
		register(new LatticeNoiseProvider(LatticeNoiseProvider.Kind.VALUE_CUBIC));
		register(new LatticeNoiseProvider(LatticeNoiseProvider.Kind.CELLULAR));
		register(new LatticeNoiseProvider(LatticeNoiseProvider.Kind.CELLULAR_VALUE));
		register(new LatticeNoiseProvider(LatticeNoiseProvider.Kind.WARPED_PERLIN));
		register(new LatticeNoiseProvider(LatticeNoiseProvider.Kind.SIMPLEX_2D));
		// 这个是反例：float + 半径截断，永远不会爆炸，只会静默归零。
		register(new OpenSimplex2Provider());
	}

	private NoiseGenRegistry() {
	}

	/** 注册一个生成器。{@code id} 重复的会被忽略（幂等）。 */
	public static void register(NoiseProvider provider) {
		if (provider == null || provider.id() == null) {
			return;
		}
		final int n = PROVIDERS.size();
		for (int i = 0; i < n; i++) {
			if (provider.id().equals(PROVIDERS.get(i).id())) {
				return;
			}
		}
		PROVIDERS.add(provider);
	}

	/** 当前生效的生成器；{@code null} = 走原版。 */
	public static NoiseProvider active() {
		return (activeIndex >= 0 && activeIndex < PROVIDERS.size())
				? PROVIDERS.get(activeIndex)
				: null;
	}

	/** 轮换：原版 → 生成器 ① → 生成器 ② → … → 原版。 */
	public static void cycle() {
		if (PROVIDERS.isEmpty()) {
			activeIndex = -1;
			return;
		}
		activeIndex++;
		if (activeIndex >= PROVIDERS.size()) {
			activeIndex = -1;
		}
	}

	public static void reset() {
		activeIndex = -1;
	}

	public static int size() {
		return PROVIDERS.size();
	}

	/** GUI 用的语言键。 */
	public static String langKey() {
		final NoiseProvider p = active();
		return p == null
				? "noise_gen_debug.noisegen.vanilla"
				: "noise_gen_debug.noisegen." + p.id();
	}
}
