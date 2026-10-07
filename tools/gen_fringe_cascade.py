#!/usr/bin/env python3
"""从 FarLandsTraveler 的 base_3d_noise_far_lands.json 生成 FringeCascade.java。

用法: python3 tools/gen_fringe_cascade.py <json> > <out.java>
"""
import json, sys

SRC = sys.argv[1] if len(sys.argv) > 1 else \
    '/tmp/flt/src/main/resources/data/farlandstraveler/worldgen/density_function/overworld/base_3d_noise_far_lands.json'

d = json.load(open(SRC))

NODES = []   # [type, a,b,c,d, e,f]
SPECS = []   # dict

def emit(n):
    if isinstance(n, (int, float)):
        NODES.append([0, int(round(float(n) * 1000)), 0, 0, 0, 0, 0])
        return len(NODES) - 1
    t = n.get('type')
    if t == 'minecraft:range_choice':
        b = n['input']
        idx = len(NODES); NODES.append(None)
        inn = emit(n['when_in_range'])
        out = emit(n['when_out_of_range'])
        # range_choice 触发 when_in_range 的条件：input 的值落在 [min, max)。
        # box_select 在盒内返回 1、盒外返回 0（invert=false）。
        #
        # ⚠️ 本文件所有 range_choice 都是 [0, 1)——1 被 max_exclusive 排除！
        #    所以「在盒内」反而走 when_out_of_range。
        #    实测验证：原点处得到 x_scale=0.25（正常），而极性写反会得到 6.25e41。
        inside_value = 0.0 if b.get('invert') else 1.0
        lo = n.get('min_inclusive', float('-inf'))
        hi = n.get('max_exclusive', float('inf'))
        if not (lo <= inside_value < hi):
            inn, out = out, inn
        NODES[idx] = [1, b['origin_x'], b['origin_x'] + b['extend_x'],
                      b['origin_z'], b['origin_z'] + b['extend_z'], inn, out]
        return idx
    if t == 'farlandstraveler:old_blended_noise_customizable':
        SPECS.append({
            'xScale': n.get('x_scale', 1.0), 'yScale': n.get('y_scale', 1.0),
            'zScale': n.get('z_scale', n.get('x_scale', 1.0)),
            'xFactor': n.get('x_factor', 80.0), 'yFactor': n.get('y_factor', 160.0),
            'zFactor': n.get('z_factor', n.get('x_factor', 80.0)),
            'smear': n.get('smear_scale_multiplier', 8.0),
            'overflowable': 1 if n.get('overflowable') else 0,
            'rStart': n.get('repeat_start', -1), 'rLen': n.get('repeat_length', -1),
            'rCount': n.get('repeat_count', -1),
            'xShift': n.get('x_shift', 0.0), 'yShift': n.get('y_shift', 0.0),
            'zShift': n.get('z_shift', 0.0)})
        NODES.append([2, len(SPECS) - 1, 0, 0, 0, 0, 0])
        return len(NODES) - 1
    raise SystemExit('unknown type: %r' % t)

emit(d)

def darr(v):
    return ', '.join(('%.10g' % x) + 'D' for x in v)

o = []
w = o.append
w('package org.cn.xiaofan.noise;')
w('')
w('/**')
w(' * 「边缘之地」基岩噪声级联（Fringe Lands base-3D-noise cascade）。')
w(' *')
w(' * <p><b>本文件由 {@code tools/gen_fringe_cascade.py} 从 FarLandsTraveler 的')
w(' * {@code base_3d_noise_far_lands.json} 自动生成，请勿手改。</b>')
w(' *')
w(' * <h2>结构</h2>')
w(' *')
w(' * <p>原版是一个二叉判定树：{@code range_choice(box_select, when_in_range, when_out_of_range)}。')
w(' * 因为 {@code box_select} 只依赖 (x, z)，整棵树可以退化成对 (x, z) 的纯函数。')
w(' * 这里把树展平成节点表，用循环求值（避免递归深度问题，也便于校验）。')
w(' *')
w(' * <p>节点编码（每节点 7 个 int）：')
w(' * <pre>')
w(' *   type 0（常数）： [0, 值×1000, -, -, -, -, -]')
w(' *   type 1（盒子）： [1, minX, maxX, minZ, maxZ, 在盒内的子节点, 不在盒内的子节点]')
w(' *   type 2（噪声）： [2, 规格下标, -, -, -, -, -]')
w(' * </pre>')
w(' *')
w(' * <p>Y 轴被忽略：本文件里所有盒子的 Y 范围都是')
w(' * {@code [-25101648, +25101649)}，覆盖了 MC 全部可能的方块高度。')
w(' *')
w(' * @see <a href="https://github.com/SmallmanSeries/FarLandsTraveler">FarLandsTraveler</a>')
w(' */')
w('public final class FringeCascade {')
w('')
w('\tprivate FringeCascade() {')
w('\t}')
w('')
w('\t/** 节点总数。 */')
w('\tpublic static final int NODE_COUNT = %d;' % len(NODES))
w('')
w('\t/** 噪声规格数量。 */')
w('\tpublic static final int SPEC_COUNT = %d;' % len(SPECS))
w('')
w('\t/** 根节点下标。 */')
w('\tpublic static final int ROOT = 0;')
w('')
w('\t// type, a, b, c, d, e, f —— 见类注释')
w('\tprivate static final int[][] NODES = {')
for i in range(0, len(NODES), 3):
    chunk = NODES[i:i + 3]
    parts = []
    for nd in chunk:
        parts.append('new int[] {' + ', '.join(str(v) for v in nd) + '}')
    w('\t\t\t' + ', '.join(parts) + (',' if i + 3 < len(NODES) else ''))
w('\t};')
w('')
names = ['xScale', 'yScale', 'zScale', 'xFactor', 'yFactor', 'zFactor', 'smear',
         'xShift', 'yShift', 'zShift']
for nm in names:
    w('\tprivate static final double[] S_%s = {%s};' % (nm, darr([s[nm] for s in SPECS])))
w('\tprivate static final int[] S_overflowable = {%s};'
  % ', '.join(str(s['overflowable']) for s in SPECS))
for nm in ['rStart', 'rLen', 'rCount']:
    w('\tprivate static final int[] S_%s = {%s};' % (nm, ', '.join(str(s[nm]) for s in SPECS)))
w('')
w('\t/**')
w('\t * 对 (方块X, 方块Z) 求值。')
w('\t *')
w('\t * @return 非负 = 应使用该下标的噪声规格；')
w('\t *         {@link #CONST_NODE} = 该处是常数，取值见 {@link #constValue(int)}')
w('\t */')
w('\tpublic static int resolveNode(int blockX, int blockZ) {')
w('\t\tint n = ROOT;')
w('\t\tfor (int guard = 0; guard < 256; guard++) {')
w('\t\t\tint[] nd = NODES[n];')
w('\t\t\tif (nd[0] == 1) {')
w('\t\t\t\tboolean inside = blockX >= nd[1] && blockX < nd[2]')
w('\t\t\t\t\t\t&& blockZ >= nd[3] && blockZ < nd[4];')
w('\t\t\t\tn = inside ? nd[5] : nd[6];')
w('\t\t\t\tcontinue;')
w('\t\t\t}')
w('\t\t\treturn n;   // 0 = 常数节点，2 = 噪声节点')
w('\t\t}')
w('\t\tthrow new IllegalStateException("FringeCascade: node walk did not terminate");')
w('\t}')
w('')
w('\t/** 节点是否为常数。 */')
w('\tpublic static boolean isConst(int node) {')
w('\t\treturn NODES[node][0] == 0;')
w('\t}')
w('')
w('\t/** 常数节点的取值。 */')
w('\tpublic static double constValue(int node) {')
w('\t\treturn NODES[node][1] / 1000.0D;')
w('\t}')
w('')
w('\t/** 噪声节点对应的规格下标。 */')
w('\tpublic static int specOf(int node) {')
w('\t\treturn NODES[node][1];')
w('\t}')
w('')
for nm in names:
    w('\tpublic static double %s(int spec) {' % nm)
    w('\t\treturn S_%s[spec];' % nm)
    w('\t}')
    w('')
w('\tpublic static boolean overflowable(int spec) {')
w('\t\treturn S_overflowable[spec] != 0;')
w('\t}')
w('')
for nm in ['rStart', 'rLen', 'rCount']:
    w('\tpublic static int %s(int spec) {' % nm)
    w('\t\treturn S_%s[spec];' % nm)
    w('\t}')
    w('')
w('\t/** 该规格是否启用循环（三个 repeat 字段都为正值）。 */')
w('\tpublic static boolean repeating(int spec) {')
w('\t\treturn rStart(spec) > 0 && rLen(spec) > 0 && rCount(spec) > 0;')
w('\t}')
w('}')
open('/mnt/A/noise_gen_debug/src/main/java/org/cn/xiaofan/noise/FringeCascade.java', 'w') \
    .write('\n'.join(o) + '\n')
print('节点数 %d, 规格数 %d' % (len(NODES), len(SPECS)))
print('根节点:', NODES[0])
