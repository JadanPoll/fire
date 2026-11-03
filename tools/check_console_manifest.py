#!/usr/bin/env python3
"""Cheap static regression check for the existing Fire TV channel routing.
Requires only Python's stdlib; never launches or modifies a device.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
ANDROID="{http://schemas.android.com/apk/res/android}"
def attr(name): return ANDROID+name

def activity_filters(path):
    tree=ET.parse(ROOT/path)
    data={}
    for activity in tree.getroot().findall(".//activity"):
        filters=[]
        for intent in activity.findall("intent-filter"):
            actions={x.get(attr("name")) for x in intent.findall("action")}
            categories={x.get(attr("name")) for x in intent.findall("category")}
            types={x.get(attr("mimeType")) for x in intent.findall("data")}
            filters.append((actions,categories,types))
        data[activity.get(attr("name"))]=(activity,filters)
    return data

app=activity_filters("app/src/main/AndroidManifest.xml")
browser=activity_filters("browser/src/main/AndroidManifest.xml")
leanback="android.intent.category.LEANBACK_LAUNCHER"
launcher="android.intent.category.LAUNCHER"
main="android.intent.action.MAIN"
send="android.intent.action.SEND"

assert ".ConsoleActivity" in app, "Console launcher not declared"
assert ".MainActivity" in app, "Dev Bridge developer tools must remain"
assert ".VoiceProbeActivity" in app, "voice troubleshooting must remain"
assert ".CodexDeskActivity" in app
assert ".CatalogActivity" in app
assert ".InputLabActivity" in app
assert ".AppLibraryActivity" in app
assert ".GameShelfActivity" in app
console,filters=app[".ConsoleActivity"]
assert console.get(attr("exported"))=="true"
assert any(main in acts and leanback in cats for acts,cats,_ in filters)
assert any(main in acts and launcher in cats for acts,cats,_ in filters)
all_home=[name for name,(_,items) in app.items()
          if any(main in acts and (launcher in cats or leanback in cats)
                 for acts,cats,_ in items)]
assert all_home==[".ConsoleActivity"], f"Multiple or missing TV launchers: {all_home}"
shelf,shelf_filters=app[".GameShelfActivity"]
assert shelf.get(attr("exported"))=="true"
assert any(send in acts and "text/plain" in mime
           for acts,cats,mime in shelf_filters)
assert ".BrowserActivity" in browser
browser_main,browser_filters=browser[".BrowserActivity"]
assert browser_main.get(attr("exported"))=="true"
assert any(main in acts and leanback in cats for acts,cats,_ in browser_filters)
for name in ["tv_banner.xml","tv_icon.xml"]:
    ET.parse(ROOT/"app/src/main/res/drawable"/name)
print("Fire Console route contract PASS: one home, preserved old tools,"
      " explicit user-confirmed Playroom share, independent Fire Browser.")
