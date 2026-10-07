package org.cn.xiaofan.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.cn.xiaofan.noise.NoiseParams;

/**
 * 把 {@link NoiseParams} 渲染成可读文本行，供界面显示。
 *
 * <p>值全部硬编码在这里（不依赖翻译文件），骨架阶段便于直接看数字对不对。
 */
final class NoiseParamsText {

	private NoiseParamsText() {
	}

	static List<String> summary(NoiseParams p) {
		List<String> lines = new ArrayList<>();
		lines.add("height            = " + p.height);
		lines.add("sizeHorizontal    = " + p.sizeHorizontal);
		lines.add("sizeVertical      = " + p.sizeVertical);
		lines.add("densityFactor     = " + p.densityFactor);
		lines.add("densityOffset     = " + p.densityOffset);
		lines.add("simplexSurface    = " + p.simplexSurfaceNoise);
		lines.add("randomDensityOff  = " + p.randomDensityOffset);
		lines.add("islandOverride    = " + p.islandNoiseOverride);
		lines.add("amplified         = " + p.amplified);
		lines.add("xzScale           = " + p.xzScale);
		lines.add("yScale            = " + p.yScale);
		lines.add("xzFactor          = " + p.xzFactor);
		lines.add("yFactor           = " + p.yFactor);
		lines.add("topSlide  (t,s,o) = " + p.topSlideTarget + ", " + p.topSlideSize + ", " + p.topSlideOffset);
		lines.add("botSlide  (t,s,o) = " + p.bottomSlideTarget + ", " + p.bottomSlideSize + ", " + p.bottomSlideOffset);
		return lines;
	}

	static String status(boolean modified) {
		return modified ? "Noise params MODIFIED" : "Noise params = vanilla overworld";
	}
}
