"""Judge captured evidence; installation or source tests cannot substitute for app use."""
from pathlib import Path
import json, sys, xml.etree.ElementTree as ET

root=Path(sys.argv[1]);root.mkdir(parents=True,exist_ok=True)
def read_json(name):
    p=root/name
    return json.loads(p.read_text()) if p.exists() else {}
scope=read_json('scope.json');native=read_json('native-verdict.json')
ui=root/'patched-settings.xml';packages=[];labels=[]
if ui.exists():
    nodes=ET.parse(ui).getroot().iter('node')
    for node in nodes:
        packages.append(node.get('package',''))
        labels.extend([node.get('text',''),node.get('content-desc','')])
settings='com.instagram.android' in packages and any('hushgram' in s.lower() for s in labels)
crash=root/'crashes.txt'
crashed=crash.exists() and 'Process: com.instagram.android' in crash.read_text(errors='replace')
result={
 'scope':'base-only' if scope.get('baseOnly') else 'complete-input-not-established',
 'settingsUiConfirmed':settings,
 'instagramCrashObserved':crashed,
 'nativeControlsProven':native.get('controlsProven',False),
 'allNativeCasesPassed':native.get('featuresPassed',False),
 'signedInFlowsVerified':False,
 'readyForPhone':False,
}
result['outcome']='FAIL' if crashed or not settings or not native.get('featuresPassed') else 'PARTIAL'
result['reason']='Read each result separately. A base-only client and anonymous native cases cannot establish a working signed-in app.'
(root/'judgement.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
raise SystemExit(1 if result['outcome']=='FAIL' else 2)

