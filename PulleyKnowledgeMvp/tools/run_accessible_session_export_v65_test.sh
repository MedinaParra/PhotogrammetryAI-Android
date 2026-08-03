#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

launcher = Path('PulleyKnowledgeMvp/app/src/main/java/cl/skm/pulleyai/LauncherActivity.java').read_text(encoding='utf-8')
required_launcher = [
    'new ScrollView(this)',
    'EXPORTAR ÚLTIMA SESIÓN (ZIP)',
    'EXPORTAR ESTA SESIÓN (ZIP)',
    'SessionPackageExporter.build(',
    'LauncherActivity.this, captureStore, sessionId',
    'Intent.ACTION_CREATE_DOCUMENT',
    'ZIP exportado correctamente',
]
missing = [token for token in required_launcher if token not in launcher]
assert not missing, f'missing accessible export integration: {missing}'
assert launcher.count('exportSession(') >= 3, 'launcher and per-session export routes must be wired'

zip_activity = Path('PulleyKnowledgeMvp/app/src/main/java/cl/skm/pulleyai/ZipReprocessActivity.java')
if zip_activity.exists():
    source = zip_activity.read_text(encoding='utf-8')
    assert 'Intent.ACTION_OPEN_DOCUMENT' in source, 'ZIP input picker must be accessible'
    assert 'Intent.ACTION_CREATE_DOCUMENT' in source, 'diagnostic output must be saveable'

print('Accessible session ZIP export gate PASS')
PY
