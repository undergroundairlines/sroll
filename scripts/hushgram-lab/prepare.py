"""Prepare an offline emulator experiment; never publishes the Instagram APK."""
from pathlib import Path
from urllib.request import urlopen
import subprocess, hashlib, json
from zipfile import ZipFile, ZIP_STORED

lab=Path('lab');lab.mkdir(exist_ok=True)
evidence=Path('evidence');evidence.mkdir(exist_ok=True)
files={
 'morphe.jar':('https://github.com/MorpheApp/morphe-desktop/releases/download/v1.19.1/morphe-desktop-1.19.1-all.jar','a51472252cd63b2eac635dbb5a154ff918bfc15268da89679feb3d760230c573'),
 'patches.mpp':('https://github.com/SysAdminDoc/HushGram/releases/download/v0.0.16/patches-0.0.16.mpp','51b81009ef870dba06cf0feb0af329ee52b5753a7290da6a26839748a9e4a270'),
}
for name,(url,digest) in files.items():
    with urlopen(url,timeout=120) as r: data=r.read()
    assert hashlib.sha256(data).hexdigest()==digest,name+' changed'
    (lab/name).write_bytes(data)
# The available Meta-signed input lacks its density split. This is an explicit
# BASE-ONLY experiment, not a complete distributable client or a stock baseline.
# Use the pinned upstream's ordinary merger; never invent split resources.
with ZipFile(lab/'base-only.apkm','w',compression=ZIP_STORED) as archive:
    archive.write(lab/'stock.apk','base.apk')
subprocess.run(['java','-Xmx6g','-cp',str(lab/'morphe.jar'),'hushgram/scripts/MergeSplits.java',str(lab/'base-only.apkm'),str(lab/'base-only-merged.apk')],check=True)
(evidence/'scope.json').write_text(json.dumps({'baseOnly':True,'missing':'vendor density split','merge':'unmodified pinned upstream MergeSplits.java and Morphe merger','notEstablished':['complete-client installation','signed-in Home/messages/Stories','physical swipe enforcement','Nothing OS compatibility']}))
catalog=json.loads(Path('hushgram/patches-list.json').read_text())
patches=catalog['patches']
names=[p['name'] for p in patches if p['name'] not in ['Install next to Instagram','Change version code']]
assert len(names)==115, 'Released patch catalogue changed'
args=['java','-Xmx6g','-jar',str(lab/'morphe.jar'),'patch','--unsigned','--exclusive','-p',str(lab/'patches.mpp'),'-o',str(lab/'patched.apk'),'-t',str(lab/'temp'),'-r',str(evidence/'patch-result.json')]
for name in names:args+=['-e',name]
args+=[str(lab/'base-only-merged.apk')]
with (evidence/'patch.log').open('w') as log:
    completed=subprocess.run(args,stdout=log,stderr=subprocess.STDOUT)
if completed.returncode:
    print((evidence/'patch.log').read_text()[-10000:])
    raise SystemExit(completed.returncode)
result=json.loads((evidence/'patch-result.json').read_text())
assert result['success'] and not result['failedPatches']
assert set(result['appliedPatches'])==set(names)
print('Applied',len(names),'released patches; signing is a separate Android SDK step.')
