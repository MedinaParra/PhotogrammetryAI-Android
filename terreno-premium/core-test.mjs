import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
// Import the buildless browser module without requiring a package.json type.
const source = await readFile(new URL('./dist/core.js', import.meta.url), 'utf8');
const core = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
let passed = 0;
const test = (name, fn) => { fn(); passed++; console.log(`✓ ${name}`); };
const copy = p => JSON.parse(JSON.stringify(p));
const example = core.exampleProject();
test('Proyecto nuevo válido y sin datos de terreno inventados', () => {
  const p = core.createProject('Obra prueba', 'Atril'); assert.equal(p.example, false); assert.equal(p.nodes.length, 0); assert.equal(core.validateProject(p), p);
});
test('Ejemplo sintético completo y marcado', () => { assert.equal(example.example, true); assert.equal(example.nodes.length, 6); assert.equal(example.members.length, 8); assert(example.members.every(m => m.status === 'estimated')); });
test('Longitud euclidiana de travesaño 1600 mm', () => assert.equal(core.lengthOf(example, example.members[4]), 1600));
test('Unidades: 1,25 m = 1250 mm; 12.5 cm = 125 mm', () => { assert.equal(core.parseMeasure('1,25', 'm'), 1250); assert.equal(core.parseMeasure('12.5', 'cm'), 125); assert.equal(core.toUnit(1250, 'm'), 1.25); });
test('Unidades: cero y fracción decimal', () => { assert.equal(core.parseMeasure('0', 'mm'), 0); assert.equal(core.parseMeasure(',5', 'cm'), 5); assert.equal(core.toUnit(-500, 'm'), -0.5); });
test('Medidas inválidas rechazadas', () => { for (const v of ['', '-1', '1,2,3', 'NaN', 'Infinity', '1e3', '1 000', '<script>']) assert.throws(() => core.parseMeasure(v)); assert.throws(() => core.parseMeasure('1', 'in')); assert.throws(() => core.parseMeasure('100000001')); });
test('Exportación e importación mantienen proyecto exacto', () => assert.deepEqual(core.parseProject(core.serializeProject(example)), example));
test('Fotos incrustadas preservadas y exclusión explícita', () => {
  const p = copy(example); p.members[0].photos.push({ id: 'photo1', name: 'Prueba.png', dataUrl: 'data:image/png;base64,aGVsbG8=', createdAt: new Date().toISOString() });
  assert.deepEqual(core.parseProject(core.serializeProject(p)).members[0].photos, p.members[0].photos);
  const stripped = JSON.parse(core.serializeProject(p, false)); assert.equal(stripped.photoPolicy, 'excluded'); assert.equal(stripped.project.members[0].photos.length, 0); assert.equal(p.members[0].photos.length, 1);
});
test('Referencia a nodo inexistente rechazada', () => { const p = copy(example); p.members[0].endNode = 'missing'; assert.throws(() => core.validateProject(p), /inexistente/); });
test('Identificador de nodo duplicado rechazado', () => { const p = copy(example); p.nodes[1].id = p.nodes[0].id; assert.throws(() => core.validateProject(p), /duplicado/); });
test('Elemento de longitud cero rechazado', () => { const p = copy(example); p.members[0].endNode = p.members[0].startNode; assert.throws(() => core.validateProject(p), /longitud/); });
test('Nodos distintos coincidentes rechazados como extremos', () => { const p = copy(example); Object.assign(p.nodes[4], {x:0,y:0,z:0}); assert.throws(() => core.validateProject(p), /longitud/); });
test('Coordenadas negativas válidas e infinitas rechazadas', () => { const p = copy(example); p.nodes[0].x = -100; core.validateProject(p); p.nodes[0].x = Infinity; assert.throws(() => core.validateProject(p)); });
test('Perfiles, espesores y dimensiones inválidas rechazados', () => {
  for (const change of [{height:0}, {width:-1}, {tw:100}, {tf:40}, {type:'fake'}, {tw:0}]) { const p = copy(example); Object.assign(p.members[0].profile,change); assert.throws(() => core.validateProject(p)); }
});
test('Sección rectangular maciza y circular válidas', () => { const p=copy(example); Object.assign(p.members[0].profile,{type:'rect',tw:0,tf:0}); core.validateProject(p); Object.assign(p.members[0].profile,{type:'round',height:80,width:80,tw:4,tf:0}); core.validateProject(p); });
test('Sección circular exige diámetro coherente', () => { const p=copy(example); Object.assign(p.members[0].profile,{type:'round',height:80,width:70,tw:4,tf:0}); assert.throws(()=>core.validateProject(p),/diámetro/); });
test('Perfil personalizado valida espesores y admite cero', () => { const p=copy(example); Object.assign(p.members[0].profile,{type:'custom',tw:0,tf:0}); core.validateProject(p); p.members[0].profile.tw=40; assert.throws(()=>core.validateProject(p),/personalizado/); p.members[0].profile.tw=2; p.members[0].profile.tf=40; assert.throws(()=>core.validateProject(p),/personalizado/); });
test('Canal C/U genérico exacto valida dimensiones y permanece intercambiable en JSON', () => { const p=copy(example); Object.assign(p.members[0].profile,{type:'channel',height:120,width:60,tw:5,tf:8}); core.validateProject(p); assert.deepEqual(core.parseProject(core.serializeProject(p)),p); for(const changed of [{tw:0},{tf:0},{tw:60},{tf:60}]){const q=copy(p);Object.assign(q.members[0].profile,changed);assert.throws(()=>core.validateProject(q));} });
test('Estados y longitud medida inválidos rechazados', () => { const p=copy(example); p.members[0].status='certified'; assert.throws(()=>core.validateProject(p)); p.members[0].status='measured'; p.members[0].lengthMeasured=-1; assert.throws(()=>core.validateProject(p)); });
test('Estados medido y confirmado exigen lectura y método también al importar', () => { for(const status of ['measured','confirmed']) { const p=copy(example); p.members[0].status=status; assert.throws(()=>core.validateProject(p),/longitud medida/); p.members[0].lengthMeasured=1900; p.members[0].note=' '; assert.throws(()=>core.validateProject(p),/observaciones/); p.members[0].note='Cinta métrica; verificación de campo'; core.validateProject(p); assert.deepEqual(core.parseProject(core.serializeProject(p)),p); } });
test('Importación inválida, versión ajena y archivo enorme rechazados', () => { assert.throws(()=>core.parseProject('{broken')); assert.throws(()=>core.parseProject(JSON.stringify({format:core.FORMAT,version:99,project:example}))); assert.throws(()=>core.parseProject(' '.repeat(core.LIMITS.jsonChars+1))); });
test('Fotos con enlace remoto o SVG rechazadas', () => { for(const url of ['https://example.test/photo.jpg','data:image/svg+xml;base64,aGVsbG8=']) { const p=copy(example); p.members[0].photos=[{id:'photo1',name:'x',dataUrl:url,createdAt:new Date().toISOString()}]; assert.throws(()=>core.validateProject(p)); } });
test('Demasiados nodos y campos ausentes rechazados', () => { const p=copy(example); p.nodes=Array(core.LIMITS.nodes+1).fill(p.nodes[0]); assert.throws(()=>core.validateProject(p)); const q=copy(example); delete q.members[0].photos; assert.throws(()=>core.validateProject(q)); });
test('Proyecto demasiado grande no puede quedar guardado sin respaldo reimportable', () => { const p=copy(example); p.extra='x'.repeat(core.LIMITS.jsonChars); assert.throws(()=>core.validateProject(p),/respaldo/); });

const local = new Map();
globalThis.localStorage = { getItem:k=>local.get(k) ?? null, setItem:(k,v)=>local.set(k,String(v)), removeItem:k=>local.delete(k) };
assert.deepEqual(await core.loadProjects(), []); passed++; console.log('✓ Almacenamiento alternativo inicia vacío');
const result = await core.saveProject(example); assert.equal(result.mode,'localstorage'); passed++; console.log('✓ Guardado local confirmado con modo visible');
await core.saveProject(example); const saved = await core.loadProjects(); assert.equal(saved.length,1); assert.equal(saved[0].id,example.id); passed++; console.log('✓ Guardado por id no duplica proyectos');
const revision = saved[0].updatedAt;
const edit = copy(saved[0]); edit.name = 'Edición confirmada';
const edited = await core.saveProject(edit, revision); assert(edited.savedAt > revision); assert.equal((await core.loadProjects())[0].name,edit.name); passed++; console.log('✓ Actualización con revisión esperada correcta y revisión monotónica');
await assert.rejects(()=>core.saveProject(example, revision),/Conflicto/); assert.equal((await core.loadProjects())[0].name,edit.name); passed++; console.log('✓ Conflicto de versión no sobrescribe cambios más recientes');
const absent=core.createProject('Eliminado','Atril'); await assert.rejects(()=>core.saveProject(absent,absent.updatedAt),/Conflicto/); passed++; console.log('✓ Revisión esperada de proyecto eliminado produce conflicto');
const simultaneous = await Promise.allSettled([core.saveProject({...edit,name:'Edición A'}, edited.savedAt), core.saveProject({...edit,name:'Edición B'}, edited.savedAt)]);
assert.equal(simultaneous.filter(x=>x.status==='fulfilled').length,1); assert.equal(simultaneous.filter(x=>x.status==='rejected').length,1); passed++; console.log('✓ Dos escrituras con misma revisión permiten solo una actualización');
const freshCore = await import(`data:text/javascript;base64,${Buffer.from(source+'\n// reload').toString('base64')}`);
assert.equal((await freshCore.loadProjects())[0].members.length,8); passed++; console.log('✓ Reinicialización de módulo conserva datos persistidos');
const original = globalThis.localStorage.setItem;
globalThis.localStorage.setItem = () => { const e = new Error('full'); e.name='QuotaExceededError'; throw e; };
await assert.rejects(()=>core.saveProject(example),/No hay espacio/); assert.equal((await core.loadProjects()).length,1); passed++; console.log('✓ Fallo de cuota explícito y registro previo conservado');
globalThis.localStorage.setItem = original;
await core.removeProject(example.id); assert.equal((await core.loadProjects()).length,0); passed++; console.log('✓ Eliminación persistida');
const unicodeHuge=core.createProject('Unicode','Prueba');unicodeHuge.extra='é'.repeat(21*1024*1024);
assert.throws(()=>core.serializeProject(unicodeHuge),/40 MB/);assert.throws(()=>core.parseProject(JSON.stringify({format:core.FORMAT,version:core.VERSION,project:unicodeHuge})),/40 MB/);passed++;console.log('✓ Respaldo Unicode se limita por bytes UTF-8, igual que la importación');
console.log(`\n${passed} pruebas correctas.`);
