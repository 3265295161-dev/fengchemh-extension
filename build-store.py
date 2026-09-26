#!/usr/bin/env python3
# Generates the full set of extension-store files for the fengchemh repo.
#
# Routing strategy (fix for "internal server error" when downloading the APK
# from GitHub raw inside mainland China):
#   - index.pb / index.json : new-format stores; apkUrl points to jsDelivr CDN
#     (commit-pinned, no cache issues) so the APK downloads through a China-
#     friendly host while the index itself can be served from anywhere.
#   - repo.json            : legacy wrapper WITH index_v2 -> fastly index.json,
#     so a modern client adding .../index.min.json gets routed to the new store
#     and downloads the APK from jsDelivr too.
#   - index.min.json       : legacy array (for very old clients that ignore
#     repo.json; apk is then fetched from <base>/apk/<apk>).
# Run from repo root. Requires: python3, grpcio-tools (schema in store-schema/).
import gzip, json, sys, os, subprocess

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'store-schema'))
import extension_store_pb2 as pb

REPO = "3265295161-dev/fengchemh-extension"
APK = "fengchemh-v1.0.1.apk"
SOURCE_ID = 517481997322915305
SIG = "6a8a841d87d870b38af2d14b8fabd67fb6fd6d1954964df4059696a46ee44b0d"

# Commit that already contains the APK + index files (captured BEFORE this
# script commits anything, so the pinned jsDelivr URL stays valid forever).
C = subprocess.check_output(['git', 'rev-parse', 'HEAD']).decode().strip()
print('pinned commit:', C)

FASTLY_INDEX = f"https://fastly.jsdelivr.net/gh/{REPO}@{C}/index.json"
FASTLY_APK = f"https://fastly.jsdelivr.net/gh/{REPO}@{C}/{APK}"
FASTLY_ICON = f"https://fastly.jsdelivr.net/gh/{REPO}@{C}/icon/eu.kanade.tachiyomi.extension.zh.fengchemh.png"


def build_store(apk_url, icon_url=""):
    store = pb.NetworkExtensionStore(
        name="风车漫画",
        badge_label="风车",
        signing_key=SIG,
        contact=pb.NetworkExtensionStore.Contact(website="https://www.fengchemh.com"),
    )
    e = store.extension_list.extensions.add()
    e.name = "风车漫画"
    e.package_name = "eu.kanade.tachiyomi.extension.zh.fengchemh"
    e.resources.apk_url = apk_url
    e.resources.icon_url = icon_url
    e.extension_lib = "1.6"
    e.version_code = 2
    e.version_name = "1.0.1"
    e.content_warning = pb.NetworkExtensionStore.CONTENT_WARNING_SAFE
    s = e.sources.add()
    s.id = SOURCE_ID
    s.name = "风车漫画"
    s.language = "zh"
    s.home_url = "https://www.fengchemh.com"
    return store


# 1. index.pb (new format, protobuf, gzipped; APK via jsDelivr)
with open('index.pb', 'wb') as f:
    f.write(gzip.compress(build_store(FASTLY_APK, FASTLY_ICON).SerializeToString(), 9))
print('index.pb written (apk via fastly)')

# 2. index.json (new format, JSON; APK via jsDelivr)
index_json = {
    "name": "风车漫画",
    "badgeLabel": "风车",
    "signingKey": SIG,
    "contact": {"website": "https://www.fengchemh.com"},
    "extensionList": {
        "extensions": [{
            "name": "风车漫画",
            "packageName": "eu.kanade.tachiyomi.extension.zh.fengchemh",
            "resources": {"apkUrl": FASTLY_APK, "iconUrl": FASTLY_ICON},
            "extensionLib": "1.6",
            "versionCode": 2,
            "versionName": "1.0.1",
            "contentWarning": "CONTENT_WARNING_SAFE",
            "sources": [{"id": SOURCE_ID, "name": "风车漫画", "language": "zh", "homeUrl": "https://www.fengchemh.com"}],
        }],
    },
}
with open('index.json', 'w', encoding='utf-8') as f:
    json.dump(index_json, f, ensure_ascii=False, separators=(',', ':'))
print('index.json written (apk via fastly)')

# 3. repo.json (legacy wrapper; index_v2 routes modern clients to fastly store)
repo = {
    "index_v2": FASTLY_INDEX,
    "meta": {
        "name": "风车漫画",
        "shortName": "风车",
        "website": "https://www.fengchemh.com",
        "signingKeyFingerprint": SIG,
    },
}
with open('repo.json', 'w', encoding='utf-8') as f:
    json.dump(repo, f, ensure_ascii=False, separators=(',', ':'))
print('repo.json written (index_v2 -> fastly)')

# 4. index.min.json (legacy array for very old clients)
legacy_entry = {
    "name": "风车漫画",
    "pkg": "eu.kanade.tachiyomi.extension.zh.fengchemh",
    "apk": APK,
    "lang": "zh",
    "code": SOURCE_ID,
    "version": "1.0.1",
    "nsfw": 0,
    "sources": [{
        "id": SOURCE_ID,
        "lang": "zh",
        "name": "风车漫画",
        "baseUrl": "https://www.fengchemh.com",
    }],
}
with open('index.min.json', 'w', encoding='utf-8') as f:
    f.write(json.dumps([legacy_entry], ensure_ascii=False, separators=(',', ':')))
print('index.min.json written (legacy)')

# 5. alternate stores with other APK hosts
def write_variant(name, apk_url, icon_url=""):
    with open(name, 'wb') as f:
        f.write(gzip.compress(build_store(apk_url, icon_url).SerializeToString(), 9))
    print(name, 'written')

write_variant('index-pages.pb', f"https://3265295161-dev.github.io/fengchemh-extension/{APK}", f"https://3265295161-dev.github.io/fengchemh-extension/icon/eu.kanade.tachiyomi.extension.zh.fengchemh.png")
write_variant('index-ghproxy.pb', f"https://ghproxy.net/https://raw.githubusercontent.com/{REPO}/main/{APK}", f"https://ghproxy.net/https://raw.githubusercontent.com/{REPO}/main/icon/eu.kanade.tachiyomi.extension.zh.fengchemh.png")
write_variant('index-jsdelivr.pb', FASTLY_APK, FASTLY_ICON)
