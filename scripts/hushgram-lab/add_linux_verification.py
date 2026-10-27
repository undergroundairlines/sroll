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
print('Added only the publisher-verified Linux aapt2 artifact; dependency verification remains strict.')

