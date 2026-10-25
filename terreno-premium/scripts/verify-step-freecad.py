"""Independent real FreeCAD/OCCT shape checks, not text-only STEP validation.
Usage: PYTHONPATH=<FreeCAD lib path> python3 verify-step-freecad.py <fixtures>
FreeCAD must already be installed; this script installs nothing.
"""
import json
import math
from pathlib import Path
import sys

import FreeCAD  # Must precede Part: initialize the application's native runtime.
import Part

root=Path(sys.argv[1])
manifest=json.loads((root/'manifest.json').read_text())
results=[]
def near(actual,expected,absolute=2e-5,relative=2e-8):
    assert math.isclose(actual,expected,rel_tol=relative,abs_tol=absolute),(actual,expected)
for case in manifest['cases']:
    shape=Part.read(str(root/case['file']))
    assert not shape.isNull(),case['file']
    assert shape.isValid() and shape.isClosed(),case['file']
    assert len(shape.Solids)==len(case['members']),(case['file'],len(shape.Solids))
    for solid,expected in zip(shape.Solids,case['members']):
        assert solid.ShapeType=='Solid' and solid.isClosed() and solid.isValid(),case['file']
        assert len(solid.Shells)==1 and len(solid.Faces)==expected['faces'],case['file']
        assert solid.Volume>0,case['file']
        near(solid.Volume,expected['volume'])
        bb=solid.BoundBox
        bounds=[bb.XMin,bb.YMin,bb.ZMin,bb.XMax,bb.YMax,bb.ZMax]
        for actual,want in zip(bounds,expected['bounds']):near(actual,want,relative=0)
        for actual,want in zip(solid.CenterOfMass,expected['centroid']):near(actual,want,relative=0)
        assert all(type(face.Surface).__name__=='Plane' for face in solid.Faces),case['file']
        assert solid.isInside(FreeCAD.Vector(*expected['inside']),1e-7,False),case['file']
        assert not solid.isInside(FreeCAD.Vector(*expected['outside']),1e-7,False),case['file']
    results.append({'file':case['file'],'solids':len(shape.Solids),'valid':True,'closed':True,'volume_mm3':shape.Volume})
    print('PASS FreeCAD',case['file'],len(shape.Solids),'closed valid solids')
report={'freecad_version':FreeCAD.Version(),'occt_version':Part.OCC_VERSION,'cases':results,'passed':len(results)}
(root/'freecad-report.json').write_text(json.dumps(report,indent=2))
print('PASS',len(results),'FreeCAD cases: solid count, validity, closure, volume, bounds, centroid, analytic planes, material/void points')
