import zipfile
import shutil

src = "base.apk"
out = "unsigned.apk"
shutil.copy(src, out)
with zipfile.ZipFile(out, "a", zipfile.ZIP_DEFLATED) as z:
    z.write("dexout/classes.dex", "classes.dex")
print("classes.dex injected ke unsigned.apk")