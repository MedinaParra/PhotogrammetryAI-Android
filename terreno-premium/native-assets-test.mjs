// Packaging contract: every local JS/CSS/font reference must be reachable through
// the native explicit allowlist. This is not an Android/WebView runtime test.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.dirname(fileURLToPath(import.meta.url));
const native=fs.readFileSync(path.join(root,'android/java/org/terreno/piloto/MainActivity.java'),'utf8');
const policy=native.slice(native.indexOf('private WebResourceResponse localAsset('),native.indexOf('private WebResourceResponse blocked('));
const allowed=new Map();
for(const match of policy.matchAll(/((?:\s*case "[^"]+":)+)\s*mime="([^"]+)"; break;/g)) {
  for(const name of match[1].matchAll(/case "([^"]+)":/g)) allowed.set(name[1],match[2]);
}
const seen=new Set();
function visit(name){
  if(seen.has(name)) return;
  seen.add(name);
  assert(allowed.has(name),`Native interceptor blocks referenced asset: ${name}`);
  const full=path.join(root,'dist',name);
  assert(fs.statSync(full).isFile(),`Missing packaged asset: ${name}`);
  const ext=path.extname(name);
  const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.woff2':'font/woff2'}[ext];
  assert.equal(allowed.get(name),mime,`Wrong MIME: ${name}`);
  if(ext==='.woff2') return;
  const content=fs.readFileSync(full,'utf8');
  let refs=[];
  if(ext==='.html') refs=[...content.matchAll(/(?:src|href)=["']([^"'#]+)["']/g)].map(m=>m[1]);
  if(ext==='.js') refs=[...content.matchAll(/\b(?:from\s*|import\s*\(\s*|import\s*)['"]([^'"]+)['"]/g)].map(m=>m[1]);
  if(ext==='.css') refs=[...content.matchAll(/url\(\s*['"]?([^'"\s)]+)['"]?\s*\)/g)].map(m=>m[1]);
  for(const ref of refs){
    if(ref.startsWith('data:')) continue;
    assert(!/^(?:[a-z]+:|\/\/)/i.test(ref),`Remote asset is not allowed: ${name}`);
    const resolved=path.posix.normalize(path.posix.join(path.posix.dirname(name),ref));
    assert(!resolved.startsWith('../')&&!path.posix.isAbsolute(resolved),'Asset path escapes dist');
    visit(resolved);
  }
}
visit('index.html');
for(const asset of allowed.keys()) assert(fs.existsSync(path.join(root,'dist',asset)),`Stale allowlist: ${asset}`);
assert(seen.has('premium-viewer.js'));
assert(seen.has('vendor/three/three.core.js'));
assert(seen.has('vendor/inter/InterVariable.woff2'));
assert(policy.includes("font-src 'self'"));
assert(policy.includes("connect-src 'none'"));
assert(policy.includes('getEncodedPath()'));
assert(policy.includes('uri.getQuery() != null'));
assert(policy.includes('uri.getFragment() != null'));
assert(policy.includes('"font/woff2".equals(mime) ? null : "UTF-8"'));
assert(native.includes('"GET".equals(request.getMethod())'));
const manifest=fs.readFileSync(path.join(root,'android/AndroidManifest.xml'),'utf8');
assert(!/<uses-permission\b/.test(manifest),'Offline app unexpectedly requests permissions');
assert(manifest.includes('package="org.terreno.piloto"'));
assert(manifest.includes('android:screenOrientation="sensorLandscape"'));
console.log(`Native packaging contract: ${seen.size} reachable assets, MIME/CSP/offline guards verified. WebView runtime remains unverified.`);
