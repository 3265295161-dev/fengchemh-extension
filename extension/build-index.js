// Generates index.min.json (the file Tachimanga/Mihon parses from the repo URL).
// Usage: node build-index.js   (run from this directory)
//
// New Mihon/Tachimanga index schema (matches TheNano/copymanga-copy20 and
// keiyoushi index.pb fallback):
//   {name, pkg, apk, lang, code (int), version (string), nsfw, sources[]}
// The 64-bit source id lives in sources[].id as a string (kept verbatim; it is
// larger than JS safe integer range, so handled via BigInt).
const crypto = require('crypto');
const fs = require('fs');

const tpl = JSON.parse(fs.readFileSync('index.template.json', 'utf8'));

// Mihon source id: first 8 bytes of md5("name/lang/versionId") interpreted as a
// big-endian 64-bit signed integer, then sign bit cleared. versionId is the
// source's internal int version (kept stable), separate from the display
// `version` string in the index entry.
const digest = crypto.createHash('md5').update(`${tpl.name}/${tpl.lang}/${tpl.versionId}`).digest();
const id = (digest.readBigInt64BE(0) & 0x7fffffffffffffffn).toString();

if (id !== String(tpl.code)) {
  console.error(`source id mismatch: computed=${id} expected=${tpl.code}`);
  process.exit(1);
}

const sources = `[{"id":${JSON.stringify(id)},"lang":${JSON.stringify(tpl.lang)}`
  + `,"name":${JSON.stringify(tpl.name)},"baseUrl":${JSON.stringify(tpl.baseUrl)}}]`;

const entry =
  `{"name":${JSON.stringify(tpl.name)},"pkg":${JSON.stringify(tpl.pkg)}` +
  `,"apk":${JSON.stringify(tpl.apk)},"lang":${JSON.stringify(tpl.lang)}` +
  `,"code":${tpl.entryCode},"version":${JSON.stringify(tpl.version)},"nsfw":${tpl.nsfw}` +
  `,"sources":${sources}}`;

fs.writeFileSync('index.min.json', `[${entry}]`);
console.log('index.min.json written (new schema); source id =', id);
