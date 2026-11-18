"""Package the existing desktop artwork for native launchers. Requires Pillow to regenerate only."""
from pathlib import Path
import hashlib,json,shutil
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'assets/brand/PicHarbor.png'
provenance=json.loads(source.with_name('SOURCE.json').read_text(encoding='utf-8'))
assert hashlib.sha256(source.read_bytes()).hexdigest()==provenance['sha256']
art=Image.open(source).convert('RGBA')
assert art.size==(1024,1024)

def save(path,image):
    path.parent.mkdir(parents=True,exist_ok=True)
    image.save(path)

res=ROOT/'android/app/src/main/res'
save(res/'drawable-nodpi/picharbor_brand.png',art)
for density,pixels in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    save(res/f'mipmap-{density}/ic_launcher.png',art.resize((pixels,pixels),Image.Resampling.LANCZOS))

# Extend only the transparent corners with the source's own horizontal blue gradient.
# iOS supplies its native corner mask and requires an opaque 1024-pixel asset.
background=Image.new('RGB',art.size)
left,right=art.getpixel((0,512)),art.getpixel((1023,512))
pixels=background.load()
for x in range(1024):
    color=tuple(round(left[c]+(right[c]-left[c])*x/1023) for c in range(3))
    for y in range(1024):pixels[x,y]=color
opaque=Image.alpha_composite(background.convert('RGBA'),art).convert('RGB')
save(ROOT/'ios/Resources/Assets.xcassets/AppIcon.appiconset/icon.png',opaque)
brand=ROOT/'ios/Resources/Assets.xcassets/PicHarborBrand.imageset'
save(brand/'icon.png',art)
(brand/'Contents.json').write_text(json.dumps({'images':[{'filename':'icon.png','idiom':'universal'}],'info':{'author':'xcode','version':1}},indent=2)+'\n',encoding='utf-8')
print('Packaged desktop artwork for Android launchers, iOS AppIcon and both About pages.')
