#!/usr/bin/env python3
"""Fire AppOS v1: discovery/validation ONLY, never import or execute an app.

Matches the intent of Manifold AppOS/Runtime/catalog.py while adapting entry
to Android's actual package manager and Leanback launcher contract.
Manifest declarations are metadata, NOT an Android permission grant.
"""
from __future__ import annotations
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ANDROID_NS="{http://schemas.android.com/apk/res/android}"
CAPABILITIES=frozenset({"signal.synthetic","input.dpad","display.canvas"})
VALID_CATEGORIES=frozenset({"Audio & Signals","Interaction & Play","Development",
                             "Camera & Vision","Hardware"})

class ContractError(ValueError):
    pass

def require(condition,message):
    if not condition:
        raise ContractError(message)

def check_recipe(recipe):
    require(type(recipe) is dict and set(recipe)==
            {"version","waveform","frequency","gain","smoothing"},
            "recipe requires exactly 5 fields")
    require(type(recipe["version"]) is int and recipe["version"]==1,
            "unsupported recipe version")
    require(recipe["waveform"] in ("sine","triangle","square"),"invalid waveform")
    for key,minimum,maximum in (("frequency",.05,4.0),("gain",.1,3.0),
                                 ("smoothing",.05,1.0)):
        value=recipe[key]
        require(type(value) in (int,float) and minimum<=value<=maximum,
                f"{key} out of range")
    return recipe

def validate_manifest(data,location,root):
    require(type(data) is dict and set(data)==
            {"schema_version","id","name","version","entry","capabilities",
             "storage","ui","build"},"unexpected manifest structure")
    require(type(data["schema_version"]) is int and data["schema_version"]==1,
            "unsupported manifest version")
    for key,pattern in (("id",r"[a-z][a-z0-9]*(?:[.][a-z0-9]+){1,6}"),
                        ("name",r"[^\x00-\x1f]{1,64}"),
                        ("version",r"[0-9]+[.][0-9]+[.][0-9]+")):
        require(type(data[key]) is str and
                re.fullmatch(pattern,data[key]) is not None,f"invalid {key}")
    entry=data["entry"]
    require(type(entry) is dict and set(entry)=={"type","package","category"},
            "invalid Android entry")
    require(entry["type"]=="android-tv" and
            entry["category"]=="android.intent.category.LEANBACK_LAUNCHER" and
            type(entry["package"]) is str and
            re.fullmatch(r"dev[.]jadan[.]fire[.][a-z][a-z0-9.]*",entry["package"]),
            "invalid Android package/launcher")
    caps=data["capabilities"]
    require(type(caps) is list and len(caps)==len(set(map(str,caps))) and
            all(type(c) is str and c in CAPABILITIES for c in caps),
            "unknown/duplicate capability")
    require(data["storage"]=={"scope":"app"},"invalid storage scope")
    require(type(data["ui"]) is dict and
            data["ui"].get("integration")=="managed-navigation" and
            data["ui"].get("category") in VALID_CATEGORIES and
            len(data["ui"])==2,"invalid UI contract")
    build=data["build"]
    require(type(build) is dict and set(build)=={"project","asset"},
            "invalid build contract")
    module_name=location.name.lower()
    require(build["project"]==":"+module_name,"Gradle project mismatch")
    require(type(build["asset"]) is str and
            re.fullmatch(r"firetv-[a-z0-9-]{1,48}[.]apk",build["asset"]),
            "invalid release asset")
    require(location.is_dir() and location.parent==root/"Applications",
            "untrusted application path")
    require((location/"build.gradle").is_file(),"Gradle module missing")
    android_manifest=location/"src/main/AndroidManifest.xml"
    require(android_manifest.is_file(),"Android manifest missing")
    xml=ET.parse(android_manifest).getroot()
    application=xml.find("application")
    require(application is not None and
            application.attrib.get(ANDROID_NS+"label"),"application label missing")
    launcher=False
    for activity in application.findall("activity"):
        if activity.attrib.get(ANDROID_NS+"exported")!="true":continue
        for filt in activity.findall("intent-filter"):
            actions={e.attrib.get(ANDROID_NS+"name") for e in filt.findall("action")}
            categories={e.attrib.get(ANDROID_NS+"name") for e in filt.findall("category")}
            if ("android.intent.action.MAIN" in actions and
                entry["category"] in categories):
                launcher=True
    require(launcher,"no exported Android TV launcher")
    gradle=(location/"build.gradle").read_text(encoding="utf-8")
    require(("applicationId '"+entry["package"]+"'") in gradle or
            ('applicationId "'+entry["package"]+'"') in gradle,
            "APK applicationId mismatch")
    # No arbitrary build/process commands are run during discovery.
    return {"id":data["id"],"name":data["name"],"version":data["version"],
            "package":entry["package"],"capabilities":caps,
            "project":build["project"],"asset":build["asset"],
            "chapter":data["ui"]["category"],
            "manifest":str((location/"appos.json").relative_to(root))}

def discover(root=ROOT):
    root=Path(root).resolve()
    application_root=root/"Applications"
    require(application_root.is_dir(),"Applications directory missing")
    discovered=[]
    seen_ids=set();seen_packages=set();seen_assets=set()
    for location in sorted(application_root.iterdir()):
        if (not location.is_dir() or location.is_symlink() or
                not (location/"appos.json").is_file()):
            continue
        data=json.loads((location/"appos.json").read_text(encoding="utf-8"))
        item=validate_manifest(data,location,root)
        require(item["id"] not in seen_ids and
                item["package"] not in seen_packages and
                item["asset"] not in seen_assets,"duplicate AppOS identity or asset")
        seen_ids.add(item["id"]);seen_packages.add(item["package"])
        seen_assets.add(item["asset"])
        discovered.append(item)
    require(discovered,"no AppOS apps found")
    require(len(discovered)<=12,"too many apps for the verified catalog")
    return discovered

def check(root=ROOT):
    root=Path(root).resolve()
    found=discover(root)
    catalog=json.loads((root/"catalog.json").read_text(encoding="utf-8"))
    require(catalog.get("schema")==1 and type(catalog.get("apps")) is list,
            "catalog schema mismatch")
    for app in found:
        rows=[a for a in catalog["apps"] if a.get("package")==app["package"]]
        require(len(rows)==1 and rows[0].get("asset")==app["asset"],
                "catalog missing or mismatching "+app["id"])
    recipe_path=root/"Applications/SignalStudio/src/main/assets/example.recipe.json"
    if recipe_path.is_file():
        check_recipe(json.loads(recipe_path.read_text(encoding="utf-8")))
    return found

def main():
    try:
        rows=check()
        if "--json" in sys.argv:
            print(json.dumps({"schema_version":1,"applications":rows},
                             indent=2,sort_keys=True))
        else:
            for app in rows:
                print(f"AppOS VERIFIED {app['id']} -> {app['package']} [{app['asset']}]")
        return 0
    except (ContractError,OSError,ET.ParseError,json.JSONDecodeError) as error:
        print(f"AppOS contract FAIL: {error}",file=sys.stderr)
        return 1

if __name__=="__main__":
    raise SystemExit(main())
