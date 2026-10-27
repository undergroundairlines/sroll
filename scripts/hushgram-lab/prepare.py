"""Prepare an offline emulator experiment; never publishes the Instagram APK."""
from pathlib import Path
from urllib.request import urlopen
import subprocess, hashlib, json

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
catalog=json.loads(Path('hushgram/patches-list.json').read_text())
patches=catalog['patches']
names=[p['name'] for p in patches if p['name'] not in ['Install next to Instagram','Change version code']]
assert len(names)==115, 'Released patch catalogue changed'
args=['java','-Xmx6g','-jar',str(lab/'morphe.jar'),'patch','--unsigned','--exclusive','-p',str(lab/'patches.mpp'),'-o',str(lab/'patched.apk'),'-t',str(lab/'temp'),'-r',str(evidence/'patch-result.json')]
for name in names:args+=['-e',name]
args+=[str(lab/'stock.apk')]
with (evidence/'patch.log').open('w') as log:
    subprocess.run(args,stdout=log,stderr=subprocess.STDOUT,check=True)
result=json.loads((evidence/'patch-result.json').read_text())
assert result['success'] and not result['failedPatches']
assert set(result['appliedPatches'])==set(names)
print('Applied',len(names),'released patches; signing is a separate Android SDK step.')

