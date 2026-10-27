"""Download through the public guest form; never accept an unpinned APK."""
from pathlib import Path
from urllib.request import build_opener, HTTPCookieProcessor, Request
from urllib.parse import urlencode
from http.cookiejar import CookieJar
from html.parser import HTMLParser
import hashlib,sys,json
class Form(HTMLParser):
    def __init__(self): super().__init__(); self.inside=False; self.fields={}
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='form' and a.get('id')=='redirect': self.inside=True
        if self.inside and tag=='input' and a.get('name'): self.fields[a['name']]=a.get('value','')
    def handle_endtag(self,tag):
        if tag=='form': self.inside=False
url='https://freesoft.ru/android/instagram/download/248101'
output=Path(sys.argv[1]);output.parent.mkdir(parents=True,exist_ok=True)
session=build_opener(HTTPCookieProcessor(CookieJar()))
with session.open(url,timeout=45) as r: html=r.read().decode('utf-8','replace')
form=Form();form.feed(html)
assert set(form.fields)=={'_token'}, 'Guest download form changed'
request=Request(url,data=urlencode(form.fields).encode(),headers={'Content-Type':'application/x-www-form-urlencoded','Referer':url},method='POST')
hash=hashlib.sha256();total=0
with session.open(request,timeout=120) as response, output.open('wb') as file:
    while True:
        chunk=response.read(1024*1024)
        if not chunk:break
        total+=len(chunk)
        assert total<400*1024*1024, 'Unexpected download size'
        hash.update(chunk);file.write(chunk)
assert hash.hexdigest()=='897b023309e3e2d80a99babe57be0c02f247b58380b2453d4f0682997f9259cd', 'Stock bytes changed; reverify provenance before running'
print(json.dumps({'source':url,'sha256':hash.hexdigest(),'bytes':total,'expectedPackage':'com.instagram.android','expectedVersion':'452.1.0.53.64','expectedVersionCode':385811006}))
