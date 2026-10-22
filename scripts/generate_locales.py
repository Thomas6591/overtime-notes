"""Generate the offline browser dictionary from the canonical locale JSON."""
import json
from pathlib import Path
def generate(root):
 assets=Path(root)/'app/src/main/assets'
 data=json.loads((assets/'locales.json').read_text())
 assert set(data['zh'])==set(data['en']), 'Translation keys must match'
 (assets/'translations.js').write_text('window.TRANSLATIONS = '+json.dumps(data,ensure_ascii=False,separators=(',',':'))+';\n')
if __name__=='__main__':generate(Path(__file__).resolve().parents[1])
