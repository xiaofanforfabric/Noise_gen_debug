#!/usr/bin/env python3
"""Mixin 静态审计：找出必然导致运行时崩溃的成员。
只报真问题：
  1. 混入类里的 static 字段（不含 @Shadow / @Unique） -> 必须 private，
     否则 "contains non-private static field"
  2. 混入类里的 static 方法（不含 @Shadow / @Unique） -> 必须 private，
     否则 "contains non-private static method"
@Shadow / @Unique 成员必须与目标一致，不参与本检查。
"""
import json, io, glob, re, sys, os

root = os.path.dirname(os.path.abspath(__file__))
if not os.path.isdir(os.path.join(root, 'src')):
    root = os.getcwd()

cfg = json.load(io.open(os.path.join(root, 'src/main/resources/noise_gen_debug.mixins.json'), encoding='utf-8'))
pkg = cfg['package'].replace('.', '/')
listed = set()
for k in ('mixins', 'client', 'server'):
    listed.update(cfg.get(k, []))

problems = []

for f in sorted(glob.glob(os.path.join(root, 'src/main/java', pkg, '**/*.java'), recursive=True)):
    src = io.open(f, encoding='utf-8').read()
    if '@Mixin' not in src:
        continue
    # 接口混入类（@Invoker / @Accessor 专用）：接口方法不能 private，跳过
    if re.search(r'@Mixin\([^)]*\)\s*\npublic\s+interface', src):
        continue
    if '@Mixin' not in src:
        # 不在 mixin 包里的普通类不需要检查；在包里的才算越界
        continue

    # 逐行扫描，带上"是否处于 @Shadow/@Unique 注解作用域"的状态
    pending_annot = None
    for i, line in enumerate(src.split('\n'), 1):
        st = line.strip()

        if st.startswith('@Shadow') or st.startswith('@Unique') \
                or st.startswith('@Invoker') or st.startswith('@Accessor'):
            pending_annot = st
            continue
        if st.startswith('@'):
            # 其他注解（@Inject 等）不影响其后的成员可见性判断
            pending_annot = None
            continue
        if st == '' or st.startswith('*') or st.startswith('/*') or st.startswith('//'):
            continue

        is_static = re.match(r'^(?:public|protected|private)?\s*static\s', st) or \
                    re.match(r'^(?:public|protected|private)\s+static\s', st)

        if is_static and pending_annot is None:
            m = re.match(r'^(public|protected)\s', st)
            kind = '方法' if '(' in st else '字段'
            if m:
                problems.append((os.path.relpath(f, root), i, kind, st[:100]))
            elif not st.startswith('private'):
                problems.append((os.path.relpath(f, root), i, kind + '(包级)', st[:100]))
        pending_annot = None

if problems:
    print("❌ 会导致运行时崩溃的 mixin 成员:")
    for p in problems:
        print("   %s:%d  %s  ->  %s" % p)
    sys.exit(1)
else:
    print("✅ mixin 审计通过：无非 private 静态字段/方法")
