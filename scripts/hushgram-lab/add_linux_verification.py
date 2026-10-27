"""Add one missing official Linux build-tool hash, keeping strict verification on."""
from pathlib import Path
from urllib.request import urlopen
import hashlib,xml.etree.ElementTree as ET
url='https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/9.1.0-14792394/aapt2-9.1.0-14792394-linux.jar'
with urlopen(url,timeout=60) as r:data=r.read()
assert hashlib.sha256(data).hexdigest()=='e7ae17af6e4093c771243e82d66462353de87befaac206bfb43e557ac1c34440'
with urlopen(url+'.sha1',timeout=30) as r:published=r.read().decode().strip().split()[0]
assert published==hashlib.sha1(data).hexdigest()
p=Path('gradle/verification-metadata.xml');s=p.read_text()
needle='<component group="com.android.tools.build" name="aapt2" version="9.1.0-14792394">'
assert needle in s
if 'aapt2-9.1.0-14792394-linux.jar' not in s:
    s=s.replace(needle,needle+'\n         <artifact name="aapt2-9.1.0-14792394-linux.jar"><sha256 value="e7ae17af6e4093c771243e82d66462353de87befaac206bfb43e557ac1c34440" origin="Google Maven publisher checksum independently verified for Linux lab"/></artifact>',1)
    ET.fromstring(s);p.write_text(s)
# AGP's test graph also reads this BOM's metadata; upstream pins only 1.86.
parts=[]
for ext,expected in [('module','e875dd54bcaf36f38c0e9442b413e2ffd309ba1cde028c36e65aa0ef8c89158b'),('pom','d9628899e9fcb11a0751e7f2f0d42785ab729d1bb69831b9c63c3b1d05c3357b')]:
    name='bc-jdk18on-bom-1.85.'+ext
    url='https://repo.maven.apache.org/maven2/org/bouncycastle/bc-jdk18on-bom/1.85/'+name
    with urlopen(url,timeout=30) as r: data=r.read()
    with urlopen(url+'.sha1',timeout=30) as r: publisher=r.read().decode().strip()
    assert hashlib.sha256(data).hexdigest()==expected
    assert hashlib.sha1(data).hexdigest()==publisher
    parts.append('<artifact name="'+name+'"><sha256 value="'+expected+'" origin="Maven Central publisher checksum independently verified for lab"/></artifact>')
s=p.read_text()
if '<component group="org.bouncycastle" name="bc-jdk18on-bom" version="1.85">' not in s:
    s=s.replace('</components>','<component group="org.bouncycastle" name="bc-jdk18on-bom" version="1.85">'+''.join(parts)+'</component>\n   </components>',1)
    ET.fromstring(s);p.write_text(s)
print('Added three publisher-verified platform/test metadata entries; dependency verification remains strict.')


