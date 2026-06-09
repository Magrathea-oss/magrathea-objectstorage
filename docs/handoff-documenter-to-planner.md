## Handoff: documenter → java-planner

### Completed by documenter
- arc42-logo.png exists at `docs/arc42/images/arc42-logo.png`
- C4 images exist at `docs/c4/images/` (27 PNG files, confirmed by c4model child)

### Requires delegation to java-infra-coder

The `bootstrap-application/` directory is owned by `java-infra-coder`. Please delegate the following operations:

**1. Copy C4 images**
```bash
mkdir -p bootstrap-application/src/main/resources/static/docs/c4/images/
cp docs/c4/images/*.png bootstrap-application/src/main/resources/static/docs/c4/images/
echo "C4 images: $(ls bootstrap-application/src/main/resources/static/docs/c4/images/ | wc -l)"
```

**2. Copy arc42-logo**
```bash
mkdir -p bootstrap-application/src/main/resources/static/docs/arc42/images/
cp docs/arc42/images/arc42-logo.png bootstrap-application/src/main/resources/static/docs/arc42/images/
echo "arc42-logo copied"
```

**3. Regenerate ARC42 JSON**
```bash
node bootstrap-application/src/main/scripts/asciidoc-to-arc42-json.mjs 2>&1
```

**4. Verify images in JSON**
```bash
python3 -c "
import sys,json; d=json.load(open('bootstrap-application/src/main/resources/static/docs/arc42.json'))
img_count = sum(1 for s in d['document']['sections'] for b in s.get('blocks',[]) if b['type'] == 'image')
print(f'Images in ARC42 JSON: {img_count}')
for s in d['document']['sections']:
    for b in s.get('blocks',[]):
        if b['type'] == 'image':
            print(f'  src={b.get(\"src\",\"\")[:80]} alt={b.get(\"alt\",\"\")[:60]}')
"
```

**5. Docker rebuild** — after steps 1-4 succeed, delegate Docker rebuild to java-infra-coder.
