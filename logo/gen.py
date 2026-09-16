import urllib.parse, urllib.request, os
prompts = {
 "v1": "flat vector mobile app icon, rounded square, teal to deep blue gradient background, glowing white notification bell with three orange sound waves emanating to the right, minimalist, clean, centered, no text, premium design",
 "v2": "flat vector mobile app icon, rounded square, indigo to violet gradient, white speech bubble containing a small orange play triangle with audio waveform bars, minimalist, clean, centered, no text",
 "v3": "flat vector mobile app icon, rounded square, modern green to teal gradient, white megaphone inside speech bubble, sound waves, notification app concept, minimalist, centered, no text, premium",
 "v4": "flat vector mobile app icon, rounded square, warm orange to pink gradient, white question mark conversation bubble with audio waveform replacing the dot, voice reading notifications concept, minimalist, clean, centered, no text",
}
for name, p in prompts.items():
    url = "https://image.pollinations.ai/prompt/" + urllib.parse.quote(p) + f"?width=1024&height=1024&seed={hash(name)%99999}&nologo=true"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Linux; Android 13)"})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            data = r.read()
        open(name + ".png", "wb").write(data)
        print(name, "OK", len(data))
    except Exception as e:
        print(name, "FAIL", e)
