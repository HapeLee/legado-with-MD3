import re, os, sys, json

src_dir = 'app/src/main/java'
results = []

for root, dirs, files in os.walk(src_dir):
    for f in files:
        if not f.endswith('.kt'):
            continue
        path = os.path.join(root, f)
        rel = os.path.relpath(path, src_dir)
        try:
            with open(path, 'r', encoding='utf-8') as fh:
                lines = fh.readlines()
        except:
            continue
        for i, line in enumerate(lines, 1):
            stripped = line.strip()
            if not stripped:
                continue
            # Skip pure comment lines
            if stripped.startswith('//') or stripped.startswith('*') or stripped.startswith('/*') or stripped.startswith('/**'):
                continue
            # Check for Chinese chars inside double-quoted strings
            matches = re.finditer(r'"([^"]*[\u4e00-\u9fff][^"]*)"', stripped)
            for m in matches:
                results.append(f'{rel}:{i}: {stripped[:150]}')

for r in results:
    print(r)
