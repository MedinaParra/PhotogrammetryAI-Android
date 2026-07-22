#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

source = Path('PulleyKnowledgeMvp/app/src/main/java/cl/skm/pulleyai/LauncherActivity.java').read_text(encoding='utf-8')
required = [
    'new ScrollView(this)',
    'EXPORTAR ÚLTIMA SESIÓN (ZIP)',
    'EXPORTAR ESTA SESIÓN (ZIP)',
    'SessionPackageExporter.build(LauncherActivity.this, captureStore, sessionId)',
    'Intent.ACTION_CREATE_DOCUMENT',
    'ZIP exportado correctamente',
]
missing = [token for token in required if token not in source]
assert not missing, f'missing accessible export integration: {missing}'
assert source.count('exportSession(') >= 3, 'launcher and per-session export routes must be wired'
print('Accessible session ZIP export gate PASS')
PY
