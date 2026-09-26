#!/usr/bin/env python3
# Generates the full set of extension-store files for the fengchemh repo:
#   index.pb      - new-format protobuf store (gzipped, like keiyoushi)
#   index.json    - new-format JSON store
#   repo.json     - legacy meta wrapper with indexV2 -> index.json
#   index.min.json- legacy array (kept for very old clients)
# Run from repo root. Requires: python3, protobuf, grpcio-tools (compiled extension_store_pb2).
import gzip, json, sys, os

sys.path.insert(0, '/tmp')
from google.protobuf import json_format
import extension_store_pb2 as pb

BASE = "https://raw.githubusercontent.com/3265295161-dev/fengchemh-extension/main"
APK = "fengchemh-v1.0.0.apk"
SOURCE_ID = 517481997322915305

store = pb.NetworkExtensionStore(
    name="风车漫画",
    badge_label="风车",
    signing_key="",
    contact=pb.NetworkExtensionStore.Contact(website="https://www.fengchemh.com"),
)
ext = store.extension_list.extensions.add()
ext.name = "风车漫画"
ext.package_name = "eu.kanade.tachiyomi.extension.zh.fengchemh"
ext.resources.apk_url = f"{BASE}/{APK}"
ext.resources.icon_url = ""
ext.extension_lib = "1.6"
ext.version_code = 1
ext.version_name = "1.0.0"
ext.content_warning = pb.NetworkExtensionStore.CONTENT_WARNING_SAFE
src = ext.sources.add()
src.id = SOURCE_ID
src.name = "风车漫画"
src.language = "zh"
src.home_url = "https://www.fengchemh.com"

# 1. index.pb (gzipped, same as keiyoushi serves)
with open('index.pb', 'wb') as f:
    f.write(gzip.compress(store.SerializeToString(), 9))
print('index.pb:', os.path.getsize('index.pb'), 'bytes (gzipped)')

# 2. index.json (new format, JSON - manual dict so int64 stay JSON numbers for kotlinx)
index_json = {
    "name": "风车漫画",
    "badgeLabel": "风车",
    "signingKey": "",
    "contact": {"website": "https://www.fengchemh.com"},
    "extensionList": {
        "extensions": [{
            "name": "风车漫画",
            "packageName": "eu.kanade.tachiyomi.extension.zh.fengchemh",
            "resources": {"apkUrl": f"{BASE}/{APK}", "iconUrl": ""},
            "extensionLib": "1.6",
            "versionCode": 1,
            "versionName": "1.0.0",
            "contentWarning": "CONTENT_WARNING_SAFE",
            "sources": [{"id": SOURCE_ID, "name": "风车漫画", "language": "zh", "homeUrl": "https://www.fengchemh.com"}],
        }],
    },
}
with open('index.json', 'w', encoding='utf-8') as f:
    json.dump(index_json, f, ensure_ascii=False, separators=(',', ':'))
print('index.json written')

# 3. repo.json (legacy wrapper; modern apps follow indexV2 to the new store)
repo = {
    "index_v2": f"{BASE}/index.json",
    "meta": {
        "name": "风车漫画",
        "shortName": "风车",
        "website": "https://www.fengchemh.com",
        "signingKeyFingerprint": "",
    },
}
with open('repo.json', 'w', encoding='utf-8') as f:
    json.dump(repo, f, ensure_ascii=False, separators=(',', ':'))
print('repo.json written')

# 4. index.min.json (legacy array for very old clients)
legacy_entry = {
    "name": "风车漫画",
    "pkg": "eu.kanade.tachiyomi.extension.zh.fengchemh",
    "apk": APK,
    "lang": "zh",
    "code": SOURCE_ID,
    "version": "1.0.0",
    "nsfw": 0,
    "hasReadme": False,
}
with open('index.min.json', 'w', encoding='utf-8') as f:
    f.write(json.dumps([legacy_entry], ensure_ascii=False, separators=(',', ':')))
print('index.min.json written')
