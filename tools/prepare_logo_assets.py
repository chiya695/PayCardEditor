"""Build lossless, offline logo assets; source files are never changed.
Requires Pillow and @resvg/resvg-js (only when importing SVG).
"""
import argparse, csv, hashlib, io, json, subprocess
from pathlib import Path
from PIL import Image

p = argparse.ArgumentParser()
p.add_argument('--source', type=Path, required=True)
p.add_argument('--resvg', type=Path, required=True, help='absolute path to @resvg/resvg-js')
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
out = root / 'app/src/main/assets/logos'
out.mkdir(parents=True, exist_ok=True)
rows = list(csv.DictReader((a.source / '标识对应清单.csv').open(encoding='utf-8-sig')))
catalog, provenance = [], []
for row in rows:
    rel = row['原始文件']
    source = a.source / rel.replace('\\', '/')
    data = source.read_bytes()
    identity = hashlib.sha256(rel.encode()).hexdigest()[:16]
    if source.suffix.lower() == '.svg':
        script = "const {Resvg}=require(process.argv[1]);const fs=require('fs');process.stdout.write(new Resvg(fs.readFileSync(process.argv[2]),{fitTo:{mode:'width',value:2048}}).render().asPng());"
        data = subprocess.check_output(['node.exe', '-e', script, str(a.resvg), str(source)])
    image = Image.open(io.BytesIO(data)).convert('RGBA')
    image.save(out / (identity + '.webp'), format='WEBP', lossless=True, exact=True, method=6)
    check = Image.open(out / (identity + '.webp')).convert('RGBA')
    assert check.size == image.size and check.tobytes() == image.tobytes()
    kind = row['类型']
    if kind == '待确认': category = '待确认'
    elif '街机' in kind: category = '街机卡'
    elif '身份' in kind: category = '其他标识'
    elif rel.startswith('交通卡'): category = '交通卡'
    else: category = '金融'
    group = ('海外金融' if rel.startswith('不常见') else ('银行' if '银行' in kind else '支付品牌与网络')) if category == '金融' else kind
    name = source.stem
    note = '原图有不透明背景，不会自动去底。' if image.getextrema()[3][0] == 255 else ''
    if kind == '待确认': note = '名称尚未确认，按原图展示。' + note
    catalog.append(dict(id=identity, name=name, category=category, group=group, path='logos/'+identity+'.webp', note=note, usage='logo'))
    provenance.append(dict(id=identity, source=rel, sha256=hashlib.sha256(source.read_bytes()).hexdigest(), width=image.width, height=image.height, outputSha256=hashlib.sha256((out/(identity+'.webp')).read_bytes()).hexdigest()))
assert len(catalog) == 101 and len({r['id'] for r in catalog}) == len(catalog)
catalog.sort(key=lambda r: (r['category'], r['group'], r['name']))
(out/'catalog.json').write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding='utf8')
(root/'tools/logo-provenance.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2), encoding='utf8')
print(json.dumps({'count':len(catalog),'assetBytes':sum(f.stat().st_size for f in out.iterdir()),'categories':{c:sum(r['category']==c for r in catalog) for c in sorted({r['category'] for r in catalog})}},ensure_ascii=False))
