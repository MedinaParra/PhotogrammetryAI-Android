/** Real Chromium/IndexedDB QA of bundled Android web assets, NOT Android or native-bridge QA.
 * CI: install Playwright + its Chromium, then node scripts/browser-qa.mjs.
 * Optional: PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs CHROMIUM_PATH=/path/to/chromium
 * QA_OUTPUT_DIR controls diagnostic output. All images/data are SYNTHETIC fixtures.
 * The Playwright test dependency is not included in the APK. No remote assets or server are used.
 */
import assert from 'node:assert/strict';
import { readFile, mkdir, mkdtemp, rm, writeFile, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { isAbsolute, join, resolve, extname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const playwrightModule = process.env.PLAYWRIGHT_MODULE || 'playwright';
const moduleSpecifier = isAbsolute(playwrightModule) || playwrightModule.startsWith('.')
  ? pathToFileURL(resolve(playwrightModule)).href : playwrightModule;
let chromium, csp;
const origin = 'https://appassets.androidplatform.net';
const home = `${origin}/assets/index.html`;
const assets = new Map();
const mime = {'.js':'text/javascript', '.css':'text/css', '.html':'text/html', '.woff2':'font/woff2', '.svg':'image/svg+xml', '.txt':'text/plain'};
async function indexAssets(directory, prefix='') {
  for (const entry of await readdir(directory, {withFileTypes:true})) {
    const name = prefix + entry.name;
    if (entry.isDirectory()) await indexAssets(join(directory, entry.name), name + '/');
    else if (entry.isFile()) assets.set(name, mime[extname(name)] || 'application/octet-stream');
  }
}
await indexAssets(join(root, 'dist'));
const work = process.env.QA_OUTPUT_DIR ? resolve(process.env.QA_OUTPUT_DIR) : await mkdtemp(join(tmpdir(), 'terreno-browser-qa-'));
await mkdir(work, {recursive:true});
// Keep Chromium profile out of uploaded screenshots/reports; this run uses synthetic data only.
const profile = await mkdtemp(join(tmpdir(), 'terreno-qa-profile-'));
const screenshot = join(work, 'mobile.png');
const modalScreenshot = join(work, 'mobile-form.png');
const report = { scope: 'Desktop Chromium with emulated mobile viewport; real IndexedDB. Native Android bridge is a stub. Not a real Android device, camera, field-photo, engineering, or general visual recognition test.', fixtures: 'Synthetic generated images and dimensions; no user photos or projects. New multi-touch tests dispatch synthetic DOM PointerEvents, not hardware touches.', instrumentation: 'QA-only read-only camera observer appended to intercepted app.js response; shipped assets are unchanged.', viewport: '390x844', tests: [], errors: [], consoleErrors: [], blockedRequests: [], screenshot, modalScreenshot, startedAt: new Date().toISOString() };
let context;
let page;
let dialogs = [];
let legacyQa=false;
let dialogAnswers = [];

async function launch() {
  context = await chromium.launchPersistentContext(profile, {
    ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}), headless: process.env.BROWSER_QA_HEADED !== '1', chromiumSandbox: true,
    ignoreDefaultArgs: ['--enable-unsafe-swiftshader', '--unsafely-disable-devtools-self-xss-warnings', '--disable-ipc-flooding-protection'],
    viewport: { width: 390, height: 844 }, deviceScaleFactor: 1,
    isMobile: true, hasTouch: true, serviceWorkers: 'block',
    args: ['--disable-dev-shm-usage', '--disable-background-networking', ...(process.env.BROWSER_QA_HEADED === '1' ? ['--use-gl=angle', '--use-angle=gl'] : [])],
  });
  await context.route('**/*', async route => {
    const url = new URL(route.request().url());
    const name = url.pathname.replace(/^\/assets\//, '');
    if (url.origin === origin && url.pathname === `/assets/${name}` && assets.has(name)) {
      let body = await readFile(join(root, 'dist', name));
      if (name === 'app.js') {
        // QA ONLY: append a read-only observer to this intercepted HTTP response.
        // It copies camera state, exposes no mutator, and never changes shipped files.
        body = body.toString('utf8') + `\nObject.defineProperty(window, '__terrenoQaReadView', {value: () => ({...viewer.getCameraState(), mode:viewer.mode, selectedId:viewer.selectedId, pointerCount:viewer.pointers.size}), writable:false, configurable:false});\n`;
      }
      return route.fulfill({ status: 200, contentType: assets.get(name),
        headers: { 'Content-Security-Policy': csp, 'X-Content-Type-Options': 'nosniff', 'Cache-Control': 'no-store' },
        body });
    }
    report.blockedRequests.push(route.request().url());
    return route.abort('blockedbyclient');
  });
  await context.addInitScript(() => {
    window.__exports = [];
    window.__nativeCalls = [];
    window.TerrenoAndroid = {
      exportJson(name, json) { window.__exports.push({ name, json }); },
      setFieldMode(enabled) { window.__nativeCalls.push({method:'setFieldMode', enabled}); },
      openDndSettings() { window.__nativeCalls.push({method:'openDndSettings'}); },
    };
  });
  page = context.pages()[0] || await context.newPage();
  page.setDefaultTimeout(10000);
  page.on('pageerror', error => report.errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') report.consoleErrors.push(message.text()); });
  page.on('dialog', async dialog => {
    dialogs.push({ type: dialog.type(), message: dialog.message() });
    const answer = dialogAnswers.length ? dialogAnswers.shift() : true;
    if (answer) await dialog.accept(); else await dialog.dismiss();
  });
  await page.goto(home, { waitUntil: 'networkidle' });
  await page.waitForFunction(() => document.querySelector('#saveState')?.textContent === 'Guardado en este dispositivo');
  if(legacyQa)await expandedFunctionalHarness();
}

// Existing functional regression coverage retains its expanded form layout in a QA-only harness.
// Production scene layout is tested first, unmodified, at landscape and portrait sizes.
async function expandedFunctionalHarness(){
  await page.setViewportSize({width:390,height:844});
  await page.evaluate(()=>{document.body.classList.remove('scene-mode');for(const id of ['sceneHeader','sceneFooter','sceneNotice','sceneMenu','sceneWheelPanel','sceneInspectorClose'])document.getElementById(id).style.display='none';});
}

async function savedProjects() {
  return page.evaluate(async () => {
    const core = await import('./core.js');
    if (core.storageInfo().mode !== 'indexeddb') throw new Error('Expected real IndexedDB');
    return core.loadProjects();
  });
}
async function activeProject() {
  const active = await page.evaluate(() => localStorage.getItem('terreno-active-project'));
  return (await savedProjects()).find(project => project.id === active);
}
async function waitProject(predicate) {
  const deadline = Date.now() + 10000;
  do {
    const project = await activeProject();
    if (predicate(project)) return project;
    await new Promise(resolve => setTimeout(resolve, 50));
  } while (Date.now() < deadline);
  throw new Error('Timed out waiting for expected persisted project');
}
async function step(name, action) {
  report.currentTest = name;
  await action(); report.tests.push(name); console.log(`PASS ${name}`);
  delete report.currentTest;
}
async function closeModal() {
  if (await page.locator('#modal').evaluate(element => element.open)) await page.locator('#closeModal').click();
}



async function afterPaint() {
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}
async function cameraState() {
  return page.evaluate(() => window.__terrenoQaReadView());
}
function closeNumber(actual, expected, label, tolerance = 1e-9) {
  assert(Number.isFinite(actual) && Number.isFinite(expected) && Math.abs(actual - expected) <= tolerance, `${label}: ${actual} != ${expected}`);
}
function sameCameraInvariants(actual, expected) {
  for (const key of ['pitch', 'zoomFactor', 'scale', 'baseScale']) closeNumber(actual[key], expected[key], key);
  assert.deepEqual(actual.center, expected.center, 'Azimuth must preserve the camera pivot');
}
async function selectOrientation(preset) {
  await page.locator('#orientationBtn').click();
  await page.locator(`[data-orientation="${preset}"]`).click();
  await afterPaint();
  assert.equal(await page.locator('#modal').evaluate(element => element.open), false);
  const expected = {iso:[Math.PI/4,Math.PI/5],front:[0,0],top:[0,Math.PI/2],side:[Math.PI/2,0]}[preset];
  const camera = await cameraState();
  closeNumber(camera.yaw, expected[0], `Orientation ${preset} yaw`);
  closeNumber(camera.pitch, expected[1], `Orientation ${preset} pitch`);
}
async function syntheticTwoFingerGesture(kind) {
  const canvas = page.locator('#canvas'); await canvas.scrollIntoViewIfNeeded();
  await canvas.evaluate((element, kind) => {
    const box = element.getBoundingClientRect(), y = box.top + box.height * .52;
    const points = [{id:701,x:box.left+box.width*.36,y}, {id:702,x:box.left+box.width*.64,y}];
    const send = (type, p) => element.dispatchEvent(new PointerEvent(type, {bubbles:true, cancelable:true, pointerId:p.id, pointerType:'touch', isPrimary:p.id===701, clientX:p.x, clientY:p.y, button:0, buttons:type==='pointerup'?0:1}));
    for (const p of points) send('pointerdown', p);
    if (kind === 'pan') {
      for (const p of points) { p.x += 27; p.y += 16; send('pointermove', p); }
    } else if (kind === 'pinch') {
      points[0].x -= 32; points[1].x += 32;
      for (const p of points) send('pointermove', p);
    } else if (kind !== 'tap') throw new Error('Unknown QA gesture');
    for (const p of points) send('pointerup', p);
  }, kind);
  await afterPaint();
}

// Exact synthetic raster recipes used by the pure pixel tests. These are NOT
// field photos; generating PNG then choosing it exercises actual decode and UI.
async function syntheticPng(kind) {
  const encoded = await page.evaluate(kind => {
    const width = kind === 'section' ? 128 : 240, height = kind === 'section' ? 128 : 220;
    const data = new Uint8ClampedArray(width * height * 4);
    for (let i = 0; i < data.length; i += 4) data.set([242, 242, 242, 255], i);
    const pixel = (x, y, value = 30) => { const i = (y * width + x) * 4; data[i] = data[i + 1] = data[i + 2] = value; };
    if (kind === 'section') {
      for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
        const dx = (x + .5 - 64) / 128, dy = (y + .5 - 64) / 128;
        if (Math.abs(dx) < .28 && Math.abs(dy) < .22 && !(Math.abs(dx) < .20 && Math.abs(dy) < .14)) pixel(x, y);
      }
    } else if (kind === 'frame' || kind === 'portal') {
      const points = [[-.31, -.34], [.31, -.34], [.31, .34], [-.31, .34]].map(([x, y]) => [width / 2 + x * width, height / 2 + y * height]);
      const pairs = [[0, 1], [1, 2], [3, 0], ...(kind === 'frame' ? [[2, 3]] : [])];
      for (const [a, b] of pairs) {
        const [x1, y1] = points[a], [x2, y2] = points[b], dx = x2 - x1, dy = y2 - y1, length2 = dx * dx + dy * dy;
        for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
          const t = Math.max(0, Math.min(1, ((x - x1) * dx + (y - y1) * dy) / length2));
          if (Math.hypot(x - x1 - t * dx, y - y1 - t * dy) <= 2.5) pixel(x, y);
        }
      }
    } else if (kind !== 'blank') throw new Error('Unknown synthetic image');
    const canvas = document.createElement('canvas'); canvas.width = width; canvas.height = height;
    canvas.getContext('2d').putImageData(new ImageData(data, width, height), 0, 0);
    return canvas.toDataURL('image/png').split(',')[1];
  }, kind);
  return Buffer.from(encoded, 'base64');
}
async function chooseAnalysisPhoto(mode, kind) {
  const buffer = await syntheticPng(kind);
  await page.locator('#startPhoto').click();
  await page.locator('#photoAnalysisKind').selectOption(mode);
  const chooserPromise = page.waitForEvent('filechooser');
  await page.locator('#sectionPick').click();
  await (await chooserPromise).setFiles({ name: `qa-SINTETICO-${kind}.png`, mimeType: 'image/png', buffer });
  await page.locator('#sectionCropForm').waitFor({ state: 'visible' });
}
async function analyzeChosenPhoto() {
  await page.locator('#sectionConsent').check();
  await page.locator('#analyzeSection').click();
  await page.waitForFunction(() => document.querySelector('#sectionCandidates')?.textContent.trim().length > 0);
}

try {
  ({ chromium } = await import(moduleSpecifier));
  const nativeSource = await readFile(join(root, 'android/java/org/terreno/piloto/MainActivity.java'), 'utf8');
  csp = nativeSource.match(/headers\.put\("Content-Security-Policy", "([^"]+)"\)/)?.[1];
  assert(csp, 'Read the exact CSP from MainActivity');
  report.browser = 'Playwright Chromium';
  report.sandbox = true;
  await launch();
  report.browserVersion = context.browser()?.version() || 'persistent Chromium';
  await step('Secure local asset origin, synthetic example, real IndexedDB, no page errors', async () => {
    assert.equal(await page.evaluate(() => isSecureContext), true);
    const projects = await savedProjects();
    assert.equal(projects.length, 1); assert.equal(projects[0].example, true);
    assert.deepEqual(report.errors, []);
  });


  await step('PRODUCTION landscape: full-screen model, family seed and no saturated panels',async()=>{
    await page.setViewportSize({width:844,height:390});await afterPaint();
    const dims=await page.locator('#canvas').boundingBox();assert(dims.width>=840&&dims.height>=386,JSON.stringify(dims));
    assert.equal(await page.locator('.inspector').isVisible(),false);assert.equal(await page.locator('.piecespanel').isVisible(),false);
    assert.match(await page.locator('#sceneNotice').textContent(),/Ejemplo ajustable/);
    const p=await activeProject();assert.equal(p.features.length,2);assert(p.members.every(m=>m.status==='estimated'));
    await page.screenshot({path:join(work,'scene-landscape.png')});
  });
  await step('PRODUCTION wheel: open, navigate, hide and Back without modifying geometry',async()=>{
    const before=await activeProject();await page.locator('#sceneWheel').focus();await page.keyboard.press('ArrowRight');
    assert.equal(await page.locator('#sceneMenu').isVisible(),true);
    await page.screenshot({path:join(work,'scene-menu.png')});
    for(let i=0;i<5;i++)await page.keyboard.press('ArrowRight');assert.equal(await page.locator('#sceneMenu').isVisible(),false);
    await page.locator('#sceneAccept').click();assert.equal(await page.locator('#sceneMenu').isVisible(),true);
    assert.equal(await page.evaluate(()=>window.terrenoHandleBack()),true);assert.equal(await page.locator('#sceneMenu').isVisible(),false);
    assert.deepEqual(await activeProject(),before);
  });
  await step('PRODUCTION family form cancel/repeat preserves current data; no false STEP',async()=>{
    const before=await activeProject();
    for(let i=0;i<2;i++){await page.locator('#sceneAccept').click();await page.locator('[data-scene-action=family]').click();await page.locator('#familySaddles').click();await page.locator('#familyCancel').click();}
    assert.deepEqual(await activeProject(),before);
    await page.locator('#sceneProjects').click();await page.locator('#sceneAnalysis').click();assert.match(await page.locator('#modalBody').innerText(),/todavía no está integrado/);await closeModal();
  });
  await step('PRODUCTION camera wheel and cube preserve model and camera inclination',async()=>{
    const before=await activeProject(),cam=await cameraState();await page.locator('#sceneWheelMode').click();await page.locator('#sceneWheel').focus();await page.keyboard.press('ArrowRight');const after=await cameraState();assert(Math.abs(after.yaw-cam.yaw)>0.01);assert.equal(after.pitch,cam.pitch);assert.deepEqual(after.center,cam.center);assert.equal(after.zoomFactor,cam.zoomFactor);assert.deepEqual(await activeProject(),before);await page.locator('#sceneWheelMode').click();
    await page.locator('#sceneOrientation').click();await page.locator('[data-orientation=iso]').click();assert.equal(await page.locator('#modal').evaluate(x=>x.open),false);
  });
  await step('PRODUCTION portrait fallback and interrupted menu have no overflow',async()=>{
    await page.setViewportSize({width:390,height:844});await afterPaint();assert((await page.evaluate(()=>document.documentElement.scrollWidth))<=391);
    await page.locator('#sceneAccept').click();await page.evaluate(()=>window.dispatchEvent(new Event('blur')));assert.equal(await page.locator('#sceneMenu').isVisible(),false);await page.screenshot({path:join(work,'scene-portrait.png')});
  });
  report.sceneLayout='Production shell verified before QA-only expanded functional harness; scene-*.png are real production-shell captures. mobile*.png below are legacy expanded-layout functional regression captures, not production UI.';
  legacyQa=true;await expandedFunctionalHarness();

  await step('Create empty project via visible mobile form', async () => {
    await page.locator('#newProject').click();
    await page.locator('#newName').fill('QA Android · datos sintéticos');
    await page.locator('#newStructure').fill('Atril de prueba');
    await page.locator('#projectForm button[type=submit]').click();
    const p = await waitProject(p => p?.name === 'QA Android · datos sintéticos');
    assert.equal(p.example, false); assert.equal(p.members.length, 0); assert.equal(p.nodes.length, 0);
  });

  await step('Create measured member, geometry and section through real form', async () => {
    await page.locator('#addMember').click();
    await page.locator('#label').fill('Viga QA 01');
    await page.locator('#bx').fill('2000'); await page.locator('#by').fill('0'); await page.locator('#bz').fill('0');
    await page.locator('#status').selectOption('measured');
    await page.locator('#lengthMeasured').fill('2000'); await page.locator('#note').fill('Fixture sintético, no medición real.');
    await page.locator('#saveMember').click();
    const p = await waitProject(p => p?.members.length === 1);
    assert.equal(p.nodes.length, 2); assert.equal(p.members[0].lengthMeasured, 2000);
    assert.equal(p.members[0].status, 'measured');
    assert.equal(p.nodes.find(n => n.id === p.members[0].endNode).x, 2000);
  });

  await step('Dirty form Back: cancel keeps form, confirm discards, persisted data unchanged', async () => {
    await page.locator('#editPiece').click(); await page.locator('#label').fill('Cambio no guardado');
    await page.screenshot({ path: modalScreenshot });
    dialogAnswers.push(false);
    assert.equal(await page.evaluate(() => window.terrenoHandleBack()), true);
    assert.equal(await page.locator('#modal').evaluate(element => element.open), true);
    dialogAnswers.push(true);
    assert.equal(await page.evaluate(() => window.terrenoHandleBack()), true);
    assert.equal(await page.locator('#modal').evaluate(element => element.open), false);
    assert.equal((await activeProject()).members[0].label, 'Viga QA 01');
    assert.equal(await page.evaluate(() => window.terrenoHandleBack()), false);
  });

  await step('Synthetic photo: real file input, decode, JPEG resize, IndexedDB persistence', async () => {
    const png = await page.evaluate(() => {
      const canvas = document.createElement('canvas'); canvas.width = 2400; canvas.height = 1200;
      const c = canvas.getContext('2d'); c.fillStyle = '#164e47'; c.fillRect(0, 0, 2400, 1200);
      c.fillStyle = '#d99828'; c.fillRect(180, 220, 2040, 760);
      c.fillStyle = '#ffffff'; c.font = 'bold 100px sans-serif'; c.fillText('QA SINTETICO', 600, 670);
      return canvas.toDataURL('image/png').split(',')[1];
    });
    const chooserPromise = page.waitForEvent('filechooser'); await page.locator('#pickPhoto').click();
    const chooser = await chooserPromise;
    await chooser.setFiles({ name: 'qa-sintetico.png', mimeType: 'image/png', buffer: Buffer.from(png, 'base64') });
    const p = await waitProject(p => p?.members[0]?.photos.length === 1);
    assert(p.members[0].photos[0].dataUrl.startsWith('data:image/jpeg;base64,'));
    await page.locator('.photogrid img').waitFor();
    const imageSize = await page.locator('.photogrid img').evaluate(async img => { await img.decode(); return [img.naturalWidth, img.naturalHeight]; });
    assert.deepEqual(imageSize, [1600, 800]);
  });

  await step('Native export stub receives full JSON with photos; cancellation is not success', async () => {
    await page.locator('#mobileBackup').click(); await page.locator('#doExport').click();
    const exp = await page.evaluate(() => window.__exports.at(-1));
    assert(exp.name.endsWith('.json')); assert.equal(JSON.parse(exp.json).project.members[0].photos.length, 1);
    assert.match(await page.locator('#toast').textContent(), /Todavía no está guardado/);
    await page.evaluate(() => window.terrenoNativeExportResult(false, 'Respaldo cancelado: no se guardó un archivo nuevo.'));
    assert.match(await page.locator('#toast').textContent(), /cancelado/);
    assert.equal((await activeProject()).members[0].photos.length, 1);
  });

  let originalBackup;
  await step('Export without photos excludes images without changing saved project', async () => {
    originalBackup = await page.evaluate(() => window.__exports[0].json);
    await page.locator('#includePhotos').uncheck(); await page.locator('#doExport').click();
    const exp = await page.evaluate(() => window.__exports.at(-1));
    assert.match(exp.name, /-sin-fotos\.json$/); assert.equal(JSON.parse(exp.json).project.members[0].photos.length, 0);
    assert.equal((await activeProject()).members[0].photos.length, 1);
    await page.evaluate(() => window.terrenoNativeExportResult(true, 'Respaldo JSON guardado.'));
    assert.equal(await page.locator('#toast').textContent(), 'Respaldo JSON guardado.');
  });

  let originalId;
  await step('File import validates JSON and creates copy with photo, keeping original', async () => {
    originalId = (await activeProject()).id;
    const chooserPromise = page.waitForEvent('filechooser'); await page.locator('#doImport').click();
    await (await chooserPromise).setFiles({ name: 'qa-respaldo.json', mimeType: 'application/json', buffer: Buffer.from(originalBackup) });
    const imported = await waitProject(p => p?.id !== originalId && p?.name.endsWith(' · importado'));
    assert.equal(imported.members[0].photos.length, 1); assert.equal(imported.members[0].lengthMeasured, 2000);
    assert((await savedProjects()).some(p => p.id === originalId));
    assert(dialogs.some(d => d.type === 'confirm' && d.message.includes('Importar una copia')));
  });

  await step('Invalid JSON import rejected without changing persisted active project', async () => {
    const before = await activeProject(); await page.locator('#mobileBackup').click();
    await page.locator('#importFile').setInputFiles({ name: 'broken.json', mimeType: 'application/json', buffer: Buffer.from('{broken') });
    await page.waitForFunction(() => !document.querySelector('#importError').hidden);
    assert.match(await page.locator('#importError').textContent(), /JSON/);
    assert.deepEqual(await activeProject(), before); await closeModal();
  });

  let saved;
  await step('Reload keeps active project, geometry and image in real IndexedDB', async () => {
    saved = await activeProject(); await page.reload({ waitUntil: 'networkidle' });
    await page.waitForFunction(() => document.querySelector('#saveState').textContent === 'Guardado en este dispositivo');
    assert.deepEqual(await activeProject(), saved);
    await expandedFunctionalHarness();
    await page.locator('#piecesList [data-member]').first().click(); // Selection is explicit in the new UI.
    await page.locator('.photogrid img').evaluate(img => img.decode());
  });

  await step('Close/restart Chromium using same persistent profile retains exact data', async () => {
    await context.close(); context = null; await launch();
    assert.deepEqual(await activeProject(), saved);
    assert.equal((await savedProjects()).length, 3);
  });

  await step('Photo-first cancellation creates no project, geometry or photo', async () => {
    const before = await savedProjects();
    await chooseAnalysisPhoto('structure', 'frame');
    await closeModal();
    assert.equal(await page.locator('#modal').evaluate(element => element.open), false);
    assert.deepEqual(await savedProjects(), before);
  });

  await step('Photo analysis requires conditions confirmation and abstains on blank synthetic image', async () => {
    const before = await savedProjects();
    await chooseAnalysisPhoto('structure', 'blank');
    await page.locator('#analyzeSection').click();
    await page.locator('#sectionError:not([hidden])').waitFor();
    assert.match(await page.locator('#sectionError').textContent(), /Confirma/);
    assert.equal((await page.locator('#sectionCandidates').textContent()).trim(), '');
    await analyzeChosenPhoto();
    assert.match(await page.locator('#sectionCandidates').textContent(), /No identificable/i);
    assert.deepEqual(await savedProjects(), before);
    await closeModal();
  });

  await step('Section photo uses real pixels, proposes family but leaves dimensions blank; cancel is non-mutating', async () => {
    const before = await savedProjects();
    await chooseAnalysisPhoto('section', 'section');
    await analyzeChosenPhoto();
    const candidates = page.locator('[data-section-candidate]');
    assert.equal(await candidates.count(), 1);
    assert.match(await candidates.first().textContent(), /rectangular/i);
    assert.match(await page.locator('#sectionCandidates').textContent(), /no una probabilidad/);
    await candidates.first().click();
    assert.equal(await page.locator('#profileType').inputValue(), 'rhs');
    for (const field of ['height', 'width', 'tw', 'tf', 'ax', 'ay', 'az', 'bx', 'by', 'bz']) assert.equal(await page.locator(`#${field}`).inputValue(), '', field);
    assert.equal(await page.locator('#confirmPhotoDimensions').isChecked(), false);
    await page.locator('#saveMember').click();
    assert.deepEqual(await savedProjects(), before, 'Missing dimensions cannot create a member');
    dialogAnswers.push(true); await page.locator('#cancelEdit').click();
    assert.equal(await page.locator('#modal').evaluate(element => element.open), false);
    assert.deepEqual(await savedProjects(), before);
  });

  let graphBaseline, graphSaved;
  await step('Structure photo produces an editable graph from synthetic pixels, never saved before confirmation', async () => {
    graphBaseline = await savedProjects();
    await chooseAnalysisPhoto('structure', 'frame');
    await analyzeChosenPhoto();
    const candidates = page.locator('[data-structure-candidate]');
    assert.equal(await candidates.count(), 1);
    assert.match(await candidates.first().textContent(), /Bastidor/i);
    await candidates.first().click();
    assert.equal(await page.locator('.graph-node-row').count(), 4);
    assert.equal(await page.locator('.graph-edge-row').count(), 4);
    await page.locator('#graphContinue').click();
    assert.match(await page.locator('#graphError').textContent(), /Confirma/);
    assert.equal(await page.locator('#templateForm').count(), 0);
    assert.deepEqual(await savedProjects(), graphBaseline);

    // Repeated/edit interactions must invalidate confirmation and preserve edits.
    await page.locator('#graphAccepted').check();
    await page.locator('#graphX-g1').fill('20');
    assert.equal(await page.locator('#graphAccepted').isChecked(), false);
    await page.locator('#graphEdgeA').selectOption('g1');
    await page.locator('#graphEdgeB').selectOption('g1');
    await page.locator('#addGraphEdge').click();
    assert.match(await page.locator('#graphError').textContent(), /distintos/);
    assert.equal(await page.locator('.graph-edge-row').count(), 4);
    await page.locator('[data-remove-graph-edge="0"]').click();
    assert.equal(await page.locator('.graph-edge-row').count(), 3);
    await page.locator('#graphEdgeA').selectOption('g1');
    await page.locator('#graphEdgeB').selectOption('g2');
    await page.locator('#addGraphEdge').click();
    assert.equal(await page.locator('.graph-edge-row').count(), 4);
    assert.equal(await page.locator('#graphX-g1').inputValue(), '20.0');
    assert.equal(await page.locator('#graphAccepted').isChecked(), false);
    await page.locator('#sectionPreview').screenshot({ path: join(work, 'synthetic-photo-reviewed-graph.png') });
    await page.locator('#graphAccepted').check();
    await page.locator('#graphContinue').click();
    await page.locator('#templateForm').waitFor();
    assert.equal(await page.locator('#templateType').inputValue(), 'bastidor');
    assert.equal(await page.locator('#templateType').isDisabled(), true);
    for (const field of ['templateWidth', 'templateHeight', 'templateDepth', 'templateSectionH', 'templateSectionW', 'templateTw', 'templateTf']) assert.equal(await page.locator(`#${field}`).inputValue(), '', field);
    assert.deepEqual(await savedProjects(), graphBaseline, 'Reviewed image graph is only an uncommitted draft');
  });

  await step('Photo graph requires manual dimensions and explicit extrusion consent before creating 3D project', async () => {
    await page.locator('#templateName').fill('QA FOTO SINTETICA · grafo revisado');
    await page.locator('#templateUnit').selectOption('mm');
    await page.locator('#templateProfile').selectOption('rhs');
    for (const [field, value] of Object.entries({templateWidth:'2000', templateHeight:'1500', templateDepth:'500', templateSectionH:'80', templateSectionW:'60', templateTw:'4', templateTf:'4'})) await page.locator(`#${field}`).fill(value);
    await page.locator('#saveTemplate').click();
    assert.deepEqual(await savedProjects(), graphBaseline, 'Missing review checkbox must not save');
    await page.locator('#templateConfirmed').check();
    await page.locator('#saveTemplate').click();
    await page.locator('#templateError:not([hidden])').waitFor();
    assert.match(await page.locator('#templateError').textContent(), /manual|MANUAL/);
    assert.deepEqual(await savedProjects(), graphBaseline, 'Depth must not silently create unseen geometry');
    await page.locator('#confirmExtrusion').check();
    await page.screenshot({ path: join(work, 'synthetic-photo-confirm-dimensions.png') });
    await page.locator('#saveTemplate').click();
    graphSaved = await waitProject(p => p?.name === 'QA FOTO SINTETICA · grafo revisado');
    assert.equal(await page.locator('#modal').evaluate(element => element.open), false);
    assert.equal((await savedProjects()).length, 4); // example + manual + imported + photo graph
    assert.equal(graphSaved.example, false);
    assert.equal(graphSaved.nodes.length, 8); assert.equal(graphSaved.members.length, 12);
    assert(graphSaved.members.every(m => m.status === 'estimated' && m.lengthMeasured === null));
    assert.equal(graphSaved.members.reduce((sum, m) => sum + m.photos.length, 0), 1);
    assert.equal(graphSaved.members[0].photos[0].name, 'qa-SINTETICO-frame.png');
    assert(graphSaved.members[0].photos[0].dataUrl.startsWith('data:image/jpeg;base64,'));
    assert.equal(graphSaved.photoProposal.origin, 'local-pixel-lines-2d-reviewed');
    assert.equal(graphSaved.photoProposal.graphEdited, true);
    assert.equal(graphSaved.photoProposal.reviewedGraph.nodes[0].x, .2);
    assert.equal(graphSaved.photoProposal.reviewedGraph.edges.length, 4);
    assert.deepEqual(graphSaved.photoProposal.dimensionsMm, {width:2000, height:1500, depth:500});
    assert.equal(graphSaved.photoProposal.extrusion, 'manual-confirmed');
    assert.deepEqual([...new Set(graphSaved.nodes.map(n => n.y))].sort((a,b) => a-b), [0,500]);
    assert.equal(Math.max(...graphSaved.nodes.map(n => n.x)), 2000);
    assert.equal(Math.max(...graphSaved.nodes.map(n => n.z)), 1500);
    assert.match(graphSaved.members[0].note, /MANUALMENTE/);
    assert.match(graphSaved.members[0].note, /no uniones verificadas/);
    assert.equal(await page.locator('#memberCount').textContent(), '12');
    await page.locator('#canvas').screenshot({ path: join(work, 'synthetic-photo-model-3d.png') });
    const remaining = (await savedProjects()).filter(p => p.id !== graphSaved.id);
    assert.deepEqual(remaining, graphBaseline, 'Photo-created project must preserve earlier projects');
  });

  await step('Photo-derived reviewed graph, dimensions, geometry and JPEG survive reload and browser restart', async () => {
    await page.reload({waitUntil:'networkidle'});
    await page.waitForFunction(() => document.querySelector('#saveState')?.textContent === 'Guardado en este dispositivo');
  if(legacyQa)await expandedFunctionalHarness();
    assert.deepEqual(await activeProject(), graphSaved);
    await page.locator('#piecesList [data-member]').first().click(); // View saved photo after selecting its member.
    await page.locator('.photogrid img').first().evaluate(img => img.decode());
    await context.close(); context = null; await launch();
    assert.deepEqual(await activeProject(), graphSaved);
    assert.equal((await savedProjects()).length, 4);
    assert.equal(await page.locator('#memberCount').textContent(), '12');
  });

  await step('Visible orbit pointer drag, top/side presets and zoom redraw view without changing saved geometry', async () => {
    const before = await savedProjects();
    const canvas = page.locator('#canvas');
    const afterPaint = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    const pixels = () => canvas.evaluate(element => element.toDataURL());
    await page.locator('#orbitMode').click();
    assert.match(await page.locator('#orbitMode').getAttribute('class'), /selected/);
    assert.match(await page.locator('#viewerHint').textContent(), /girar/);
    await selectOrientation('iso');
    await canvas.screenshot({path:join(work, 'orbit-before.png')});
    const beforeOrbit = await pixels();
    await canvas.scrollIntoViewIfNeeded();
    const box = await canvas.boundingBox(); assert(box && box.width > 200 && box.height > 200);
    // Mouse produces real PointerEvents in Chromium's mobile viewport. This does
    // not claim actual-phone touch or multi-finger gesture verification.
    await page.mouse.move(box.x + box.width * .35, box.y + box.height * .52);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width * .64, box.y + box.height * .66, {steps:12});
    await page.mouse.up(); await afterPaint();
    assert.notEqual(await pixels(), beforeOrbit, 'Orbit must visibly redraw, not move a stored member');
    await canvas.screenshot({path:join(work, 'orbit-after.png')});
    for (const preset of ['top', 'side', 'front', 'iso']) {
      await selectOrientation(preset);
      if (preset === 'top' || preset === 'side') await canvas.screenshot({path:join(work, `view-${preset}.png`)});
    }
    const beforeZoom = await pixels();
    await page.locator('#zoomIn').click(); await afterPaint();
    assert.notEqual(await pixels(), beforeZoom, 'Zoom in must redraw the camera view');
    await page.locator('#zoomOut').click(); await page.locator('#fitView').click(); await afterPaint();
    assert.deepEqual(await savedProjects(), before, 'Orbit, presets and zoom are view-only');
    assert.equal(await page.locator('#undoBtn').isDisabled(), true);
  });

  await step('Azimuth buttons, pointer dial and keyboard rotate yaw only, preserving tilt, zoom, pivot and saved geometry', async () => {
    const beforeProjects = await savedProjects();
    await page.locator('#orbitMode').click();
    await selectOrientation('iso');
    await page.locator('#zoomIn').click(); await page.locator('#zoomIn').click(); await afterPaint();
    const start = await cameraState();
    await page.locator('#azimuthToggle').click();
    await page.locator('#azimuthPanel').waitFor({state:'visible'});
    await page.locator('#azimuthPlus').click(); await afterPaint();
    const plus = await cameraState();
    closeNumber(plus.yaw - start.yaw, Math.PI / 12, 'Azimuth +15 degrees'); sameCameraInvariants(plus, start);
    await page.locator('#azimuthMinus').click(); await afterPaint();
    const minus = await cameraState(); closeNumber(minus.yaw, start.yaw, 'Azimuth reverse'); sameCameraInvariants(minus, start);
    await page.locator('#azimuthDial').focus(); await page.locator('#azimuthDial').press('ArrowRight'); await afterPaint();
    const keyed = await cameraState(); assert.notEqual(keyed.yaw, minus.yaw); sameCameraInvariants(keyed, start);
    const dial = page.locator('#azimuthDial'); await dial.scrollIntoViewIfNeeded();
    const dialBox = await dial.boundingBox(); assert(dialBox && dialBox.width > 40);
    const cx = dialBox.x + dialBox.width/2, cy = dialBox.y + dialBox.height/2, radius = Math.min(dialBox.width,dialBox.height)*.3;
    await page.mouse.move(cx+radius,cy); await page.mouse.down();
    for (let step=1;step<=6;step++) { const angle=step*Math.PI/12; await page.mouse.move(cx+Math.cos(angle)*radius,cy+Math.sin(angle)*radius); }
    await page.mouse.up(); await afterPaint();
    const dialed = await cameraState(); closeNumber(dialed.yaw-keyed.yaw,Math.PI/2,'Quarter-circle dial drag (pointer pixel rounding)',.05); sameCameraInvariants(dialed,start);
    await page.locator('#azimuthPanel').screenshot({path:join(work, 'v12-azimuth-controls.png')});
    await page.locator('#canvas').screenshot({path:join(work, 'v12-azimuth-model.png')});
    await page.locator('#azimuthClose').click();
    assert.equal(await page.locator('#azimuthPanel').isVisible(), false);
    assert.deepEqual(await savedProjects(), beforeProjects);
  });

  await step('Orientation cube opens by visible button and synthetic two-finger tap without changing data', async () => {
    const beforeProjects = await savedProjects();
    await page.locator('#orientationBtn').click();
    await page.locator('#orientationCube').waitFor({state:'visible'});
    for (const preset of ['iso','front','top','side']) assert.equal(await page.locator(`[data-orientation="${preset}"]`).isVisible(), true);
    await page.screenshot({path:join(work, 'v12-orientation-cube.png')});
    await page.locator('#orientationClose').click();
    assert.equal(await page.locator('#modal').evaluate(element => element.open), false);
    const beforeTap = await cameraState();
    await syntheticTwoFingerGesture('tap');
    await page.locator('#orientationCube').waitFor({state:'visible'});
    assert.deepEqual(await cameraState(), beforeTap, 'Stationary two-finger tap must only open the picker');
    await page.locator('#orientationClose').click();
    assert.deepEqual(await savedProjects(), beforeProjects);
  });

  await step('Synthetic two-finger pan and pinch never open cube or edit members, including move mode', async () => {
    const beforeProjects = await savedProjects();
    await page.locator('#moveMode').click(); await afterPaint();
    const beforePan = await cameraState();
    await syntheticTwoFingerGesture('pan');
    const afterPan = await cameraState();
    assert.notDeepEqual(afterPan.center, beforePan.center, 'Two-finger pan must move the view pivot');
    closeNumber(afterPan.yaw, beforePan.yaw, 'Pan preserves azimuth'); closeNumber(afterPan.pitch, beforePan.pitch, 'Pan preserves tilt');
    closeNumber(afterPan.zoomFactor, beforePan.zoomFactor, 'Pure pan preserves zoom');
    assert.equal(afterPan.pointerCount, 0);
    assert.equal(await page.locator('#modal').evaluate(element => element.open), false);
    await syntheticTwoFingerGesture('pinch');
    const afterPinch = await cameraState();
    assert(afterPinch.zoomFactor > afterPan.zoomFactor, 'Pinch apart must zoom the view');
    closeNumber(afterPinch.yaw, afterPan.yaw, 'Pinch preserves azimuth'); closeNumber(afterPinch.pitch, afterPan.pitch, 'Pinch preserves tilt');
    assert.equal(afterPinch.pointerCount, 0);
    assert.equal(await page.locator('#modal').evaluate(element => element.open), false);
    assert.deepEqual(await savedProjects(), beforeProjects, 'Multi-touch must not call member movement, even in move mode');
    await page.locator('#canvas').screenshot({path:join(work, 'v12-two-finger-pan-pinch.png')});
    await page.locator('#orbitMode').click(); await page.locator('#fitView').click();
  });

  await step('Touch command fallback navigates camera and selects a piece; Accept opens its editable form only', async () => {
    await page.locator('#piecesList [data-member]').first().click(); // Explicit selection before testing reversible next/previous.
    const beforeProjects = await savedProjects();
    await page.locator('#controlPanelBtn').click();
    await page.locator('#controlMode').selectOption('navigation');
    await page.locator('#controlFocus').selectOption('azimuth');
    const start = await cameraState();
    await page.locator('#controlPlus').click(); await afterPaint();
    const plus = await cameraState(); assert.notEqual(plus.yaw, start.yaw); sameCameraInvariants(plus, start);
    await page.locator('#controlMinus').click(); await afterPaint();
    const returned = await cameraState(); closeNumber(returned.yaw, start.yaw, 'Fallback +/- reverses azimuth'); sameCameraInvariants(returned, start);
    await page.locator('#controlFocus').selectOption('zoom');
    await page.locator('#controlPlus').click(); await afterPaint();
    assert((await cameraState()).zoomFactor > returned.zoomFactor);
    await page.locator('#controlMode').selectOption('editing');
    assert.equal(await page.locator('#controlFocus').inputValue(), 'selected-piece');
    const selected = (await cameraState()).selectedId;
    await page.locator('#controlPlus').click();
    const nextSelected = (await cameraState()).selectedId;
    assert.notEqual(nextSelected, selected, 'Editing fallback +/- selects, never translates, a piece');
    await page.locator('#controlMinus').click();
    assert.equal((await cameraState()).selectedId, selected);
    await page.locator('#controlAccept').click();
    await page.locator('#memberForm').waitFor({state:'visible'});
    assert.equal(await page.locator('#label').inputValue(), (await activeProject()).members.find(m => m.id === selected).label);
    assert.deepEqual(await savedProjects(), beforeProjects);
    await page.locator('#cancelEdit').click();
    assert.equal(await page.locator('#modal').evaluate(element => element.open), false);
  });

  await step('Measurement +/- is blocked, including synthetic disabled-button click; Accept opens empty manual reading', async () => {
    const beforeProjects = await savedProjects();
    await page.locator('#controlPanelBtn').click();
    await page.locator('#controlMode').selectOption('measurement');
    assert.equal(await page.locator('#controlFocus').inputValue(), 'manual-reading');
    assert.equal(await page.locator('#controlMinus').isDisabled(), true);
    assert.equal(await page.locator('#controlPlus').isDisabled(), true);
    const before = await cameraState();
    for (const selector of ['#controlMinus','#controlPlus']) await page.locator(selector).evaluate(el => el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true})));
    assert.deepEqual(await cameraState(), before);
    assert.deepEqual(await savedProjects(), beforeProjects);
    await page.screenshot({path:join(work, 'v12-measurement-steps-blocked.png')});
    await page.locator('#controlAccept').click();
    await page.locator('#memberForm').waitFor({state:'visible'});
    assert.equal(await page.locator('#lengthMeasured').inputValue(), '', 'No instrument reading may be fabricated');
    assert.equal(await page.locator('#status').inputValue(), 'estimated', 'Manual-entry handoff must not mark measured');
    assert.deepEqual(await savedProjects(), beforeProjects);
    await page.locator('#lengthMeasured').fill('1234');
    assert.deepEqual(await savedProjects(), beforeProjects, 'Typing a manual value is still an unsaved draft');
    dialogAnswers.push(true); await page.locator('#cancelEdit').click();
    assert.deepEqual(await savedProjects(), beforeProjects, 'Cancel must discard the manual reading draft');
    await page.locator('#controlPanelBtn').click();
    assert.equal(await page.locator('#controlMode').inputValue(), 'navigation', 'Reopening controls resets explicit context');
    assert.equal(await page.locator('#controlFocus').inputValue(), 'azimuth');
    assert.equal(await page.locator('#controlPlus').isDisabled(), false);
    await page.locator('#closeControls').click();
  });

  await step('Field view reverses via exit and Back; notification settings bridge stub only runs after explicit click', async () => {
    const before = await savedProjects();
    await page.locator('#fieldModeBtn').click();
    assert.match(await page.locator('#modalBody').textContent(), /no activa, desactiva ni restaura/);
    await page.locator('#toggleFieldMode').click();
    assert.equal(await page.locator('body').evaluate(el => el.classList.contains('field-mode')), true);
    assert.equal(await page.locator('#fieldExit').isVisible(), true);
    for (const control of ['#orbitMode', '#zoomIn', '#zoomOut', '#fitView', '#orientationBtn', '#azimuthToggle']) assert.equal(await page.locator(control).isVisible(), true, control);
    assert.deepEqual(await page.evaluate(() => window.__nativeCalls), [{method:'setFieldMode', enabled:true}]);
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({path:join(work, 'field-mode-stub.png')});
    await page.locator('#fieldModeBtn').click();
    await page.locator('#openDndSettings').click();
    assert.equal(await page.evaluate(() => window.__nativeCalls.filter(c => c.method === 'openDndSettings').length), 1);
    await closeModal();
    await page.locator('#fieldExit').click();
    assert.equal(await page.locator('body').evaluate(el => el.classList.contains('field-mode')), false);
    assert.equal(await page.locator('#fieldExit').isVisible(), false);
    assert.match(await page.locator('#toast').textContent(), /manualmente/);
    // Exercise the native status callback as a stub, not Android or real DND.
    await page.evaluate(() => window.terrenoNativeFieldModeChanged(true));
    assert.equal(await page.locator('body').evaluate(el => el.classList.contains('field-mode')), true);
    assert.equal(await page.evaluate(() => window.terrenoHandleBack()), true);
    assert.equal(await page.locator('body').evaluate(el => el.classList.contains('field-mode')), false);
    assert.equal(await page.evaluate(() => window.terrenoHandleBack()), false);
    assert.equal(await page.evaluate(() => window.__nativeCalls.filter(c => c.method === 'openDndSettings').length), 1, 'Exit must not silently toggle notification policy');
    assert.deepEqual(await savedProjects(), before, 'Field view does not mutate project data');
  });

  await step('Mobile viewport has no horizontal overflow; capture internal screenshot', async () => {
    await page.evaluate(() => window.scrollTo(0, 0));
    const dims = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth, canvas: document.querySelector('#canvas').getBoundingClientRect().toJSON() }));
    assert.equal(dims.viewport, 390); assert(dims.document <= 391, JSON.stringify(dims));
    assert(dims.canvas.width > 200 && dims.canvas.height > 200);
    await page.screenshot({ path: screenshot, fullPage: true });
  });
  assert.deepEqual(report.errors, [], 'No uncaught page errors');
  const external = report.blockedRequests.filter(url => new URL(url).origin !== origin);
  assert.deepEqual(external, [], 'The app must not request remote assets or upload photos');
  assert.deepEqual(report.blockedRequests, [], 'All bundled assets must load from the approved origin');
  report.status = 'passed';
} catch (error) {
  report.status = 'failed'; report.failure = error.stack || String(error);
  if (page && !page.isClosed()) report.visibleFailureText = await page.locator('body').innerText().then(text => text.slice(-7000)).catch(() => 'Unavailable');
  if (page && !page.isClosed()) await page.screenshot({ path: join(work, 'failure.png'), fullPage: true }).catch(() => {});
  process.exitCode = 1;
} finally {
  if (context) await context.close().catch(error => { report.cleanupError = error.message; });
  await rm(profile, { recursive:true, force:true }).catch(error => { report.cleanupError = error.message; });
  report.finishedAt = new Date().toISOString();
  const reportPath = join(work, 'report.json'); await writeFile(reportPath, JSON.stringify(report, null, 2));
  console.log(JSON.stringify({ status: report.status, passed: report.tests.length, errors: report.errors, failure: report.failure, reportPath, screenshot, modalScreenshot, blockedRequests: report.blockedRequests }, null, 2));
}
