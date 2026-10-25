"""Independent OCCT STEP semantic checks + XDE assembly/name preservation.
Usage: python verify-step-occt.py <fixtures>; requires preinstalled OCP.
"""
import json
from pathlib import Path
import sys
import OCP
from OCP.IFSelect import IFSelect_ReturnStatus
from OCP.STEPCAFControl import STEPCAFControl_Reader
from OCP.TCollection import TCollection_ExtendedString
from OCP.TDataStd import TDataStd_Name
from OCP.TDF import TDF_Label,TDF_LabelSequence
from OCP.TDocStd import TDocStd_Document
from OCP.XCAFDoc import XCAFDoc_DocumentTool

root=Path(sys.argv[1]);manifest=json.loads((root/'manifest.json').read_text());results=[]
def name(label):
    attr=TDataStd_Name()
    return attr.Get().ToExtString() if label.FindAttribute(TDataStd_Name.GetID_s(),attr) else None
def checks(iterator):
    messages=[];iterator.Start()
    while iterator.More():
        check=iterator.Value()
        for i in range(1,check.NbFails()+1):messages.append(('FAIL',check.CFail(i)))
        for i in range(1,check.NbWarnings()+1):messages.append(('WARN',check.CWarning(i)))
        iterator.Next()
    return messages
for case in manifest['cases']:
    reader=STEPCAFControl_Reader();reader.SetNameMode(True)
    assert reader.ReadFile(str(root/case['file']))==IFSelect_ReturnStatus.IFSelect_RetDone,case['file']
    basic=reader.Reader()
    load=checks(basic.WS().ModelCheckList())
    assert not load,(case['file'],load)
    doc=TDocStd_Document(TCollection_ExtendedString('XCAF'))
    assert reader.Transfer(doc),case['file']
    transfer=checks(basic.WS().TransferReader().TransientProcess().CheckList(False))
    assert not transfer,(case['file'],transfer)
    tool=XCAFDoc_DocumentTool.ShapeTool_s(doc.Main());roots=TDF_LabelSequence();tool.GetFreeShapes(roots)
    assert roots.Length()==1,case['file']
    assembly=roots.Value(1)
    assert tool.IsAssembly_s(assembly),case['file']
    assert name(assembly)==case['name'],(name(assembly),case['name'])
    components=TDF_LabelSequence();tool.GetComponents_s(assembly,components)
    assert components.Length()==len(case['members']),case['file']
    names=[]
    for i,expected in enumerate(case['members'],1):
        component=components.Value(i);referred=TDF_Label()
        assert tool.GetReferredShape_s(component,referred),case['file']
        assert name(component)==expected['label'],(case['file'],name(component),expected['label'])
        assert name(referred)==expected['label'],(case['file'],name(referred),expected['label'])
        names.append(name(component))
    results.append({'file':case['file'],'assembly_name':name(assembly),'part_names':names,'load_messages':load,'transfer_messages':transfer})
    print('PASS OCCT',case['file'],len(names),'named parts; no load/transfer warnings')
report={'ocp_version':OCP.__version__,'cases':results,'passed':len(results)}
(root/'occt-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print('PASS',len(results),'OCCT semantic/XDE cases')
