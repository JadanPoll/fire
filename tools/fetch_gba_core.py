#!/usr/bin/env python3
"""Stage pinned upstream mGBA Libretro binaries for the independent Fire GBA APK.

Upstream artifact source is LemuroidCores 1.17.0, NOT an unversioned nightly.
The exact Git *blob* IDs and sizes were read from the upstream git tree.
No ROMs, BIOS files or game assets are fetched by this helper.
"""
import argparse
import hashlib
from pathlib import Path
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/"Applications/GBAPlayer/src/main/jniLibs"
UPSTREAM="https://raw.githubusercontent.com/Swordfish90/LemuroidCores/1.17.0/lemuroid_core_mgba/src/main/jniLibs"
CORES={
    "arm64-v8a":(3176776,"8efd9168bbc52fdfe3c909c54009c7156ed143ff"),
    "armeabi-v7a":(1980268,"da5cf69ed87d1ab9e579c673679f4b67e9fd0dec"),
}
LIB="libmgba_libretro_android.so"

def git_blob_digest(data:bytes)->str:
    return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\x00"+data).hexdigest()

def check_file(path:Path,expected_size:int,expected_blob:str)->bool:
    if not path.is_file() or path.stat().st_size!=expected_size:
        return False
    sha1=hashlib.sha1()
    sha1.update(b"blob "+str(expected_size).encode()+b"\x00")
    with path.open("rb") as handle:
        for block in iter(lambda:handle.read(16384),b""):
            sha1.update(block)
    return sha1.hexdigest()==expected_blob

def stage(folder=DEST):
    folder=Path(folder)
    for abi,(size,blob) in CORES.items():
        target=folder/abi/LIB
        if check_file(target,size,blob):
            print("mGBA pinned OK (cached)",abi)
            continue
        target.parent.mkdir(parents=True,exist_ok=True)
        partial=target.with_suffix(".so.part")
        url=f"{UPSTREAM}/{abi}/{LIB}"
        req=urllib.request.Request(url,headers={"User-Agent":"FireGBA-Core-Pinning/1"})
        try:
            read=0
            sha1=hashlib.sha1()
            sha1.update(b"blob "+str(size).encode()+b"\x00")
            with urllib.request.urlopen(req,timeout=45) as source,partial.open("wb") as sink:
                while True:
                    block=source.read(16384)
                    if not block:break
                    read+=len(block)
                    if read>size:raise ValueError("Unexpectedly oversized mGBA core")
                    sink.write(block)
                    sha1.update(block)
            if read!=size or sha1.hexdigest()!=blob:
                raise ValueError("mGBA upstream binary does not match pinned Git blob")
            partial.replace(target)
            print("mGBA pinned OK (downloaded)",abi)
        finally:
            if partial.exists():partial.unlink()
    return 0

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--check-only",action="store_true")
    args=parser.parse_args()
    if args.check_only:
        for abi,(size,blob) in CORES.items():
            path=DEST/abi/LIB
            if not check_file(path,size,blob):raise SystemExit("Missing/invalid native core: "+abi)
        print("Pinned native cores PASS")
    else:
        raise SystemExit(stage())
