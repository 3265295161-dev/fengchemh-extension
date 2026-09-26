// Generates index.min.json (the file Tachimanga/Mihon parses from the repo URL).
// Usage: node build-index.js   (run from this directory)
//
// Note: the source id is a 64-bit value larger than JavaScript's safe integer
// range, so it is handled as a string/BigInt and emitted verbatim into the JSON
// to keep the exact decimal digits Mihon reads as a Long.
const crypto = require('crypto');
const fs = require('fs');

const tpl = JSON.parse(fs.readFileSync('index.template.json', 'utf8'));

// Mihon source id: first 8 bytes of md5("name/lang/versionId") interpreted as a
// big-endian 64-bit signed integer, then sign bit cleared.
const digest = crypto.createHash('md5').update(`${tpl.name}/${tpl.lang}/${tpl.version}`).digest();
const id = (digest.readBigInt64BE(0) & 0x7fffffffffffffffn).toString();

if (id !== String(tpl.code)) {
  console.error(`source id mismatch: computed=${id} expected=${tpl.code}`);
  process.exit(1);
}

const entry =
  `{"name":${JSON.stringify(tpl.name)},"pkg":${JSON.stringify(tpl.pkg)}` +
  `,"apk":${JSON.stringify(tpl.apk)},"lang":${JSON.stringify(tpl.lang)}` +
  `,"code":${id},"version":${tpl.version},"nsfw":${tpl.nsfw},"hasReadme":${tpl.hasReadme}}`;

fs.writeFileSync('index.min.json', `[${entry}]`);
console.log('index.min.json written; source id =', id);
