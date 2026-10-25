/** Local-only field-survey data. Coordinates and dimensions are stored in millimetres. */
export const FORMAT = 'levantamiento-estructural';
export const VERSION = 1;
export const LIMITS = Object.freeze({ nodes: 2000, members: 3000, projects: 100, photosPerMember: 12, photos: 120, photoChars: 4 * 1024 * 1024, jsonChars: 40 * 1024 * 1024, coordinate: 1e8 });
const UNITS = Object.freeze({ mm: 1, cm: 10, m: 1000 });
const TYPES = ['rect', 'rhs', 'i', 'channel', 'round', 'custom'];
const STATES = ['estimated', 'measured', 'confirmed'];
const LOCAL_KEY = 'levantamiento-estructural.projects.v1';
const DB_NAME = 'levantamiento-estructural-v1';
let dbPromise;
let mode = 'uninitialized';
let warning = '';
const fail = message => { throw new Error(message); };
const uid = prefix => `${prefix}-${globalThis.crypto?.randomUUID?.() || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`}`;
const object = (v, label) => (!v || typeof v !== 'object' || Array.isArray(v)) ? fail(`${label}: se esperaba un objeto.`) : v;
const text = (v, label, max, empty = false) => {
  if (typeof v !== 'string' || v.length > max || (!empty && !v.trim())) fail(`${label}: texto ${empty ? '' : 'no vacío '}de hasta ${max} caracteres.`);
  return v;
};
const id = (v, label) => { text(v, label, 120); if (!/^[A-Za-z0-9][A-Za-z0-9_.:-]*$/.test(v)) fail(`${label}: identificador inválido.`); return v; };
const number = (v, label, min = 0, max = LIMITS.coordinate) => {
  if (typeof v !== 'number' || !Number.isFinite(v) || v < min || v > max) fail(`${label}: debe ser un número entre ${min} y ${max}.`);
  return v;
};
const date = (v, label) => { text(v, label, 40); if (!/^\d{4}-\d{2}-\d{2}T/.test(v) || !Number.isFinite(Date.parse(v))) fail(`${label}: fecha inválida.`); };
const list = (v, label, max) => { if (!Array.isArray(v) || v.length > max) fail(`${label}: se admite una lista de hasta ${max} elementos.`); return v; };
const duplicate = (ids, value, label) => { if (ids.has(value)) fail(`${label}: identificador duplicado «${value}».`); ids.add(value); };
const utf8Size = value => new TextEncoder().encode(value).byteLength;
const clone = value => JSON.parse(JSON.stringify(value));

export function createProject(name = 'Nuevo levantamiento', structure = 'Atril') {
  const now = new Date().toISOString();
  const project = { id: uid('p'), name, structure, unit: 'mm', createdAt: now, updatedAt: now, example: false, nodes: [], members: [] };
  return validateProject(project);
}

export function exampleProject() {
  const p = createProject('Atril de ejemplo · datos sintéticos', 'Atril demostrativo — no corresponde a una estructura real');
  p.example = true;
  p.nodes = [
    { id: 'n1', label: 'A · pie izquierdo', x: 0, y: 0, z: 0 },
    { id: 'n2', label: 'B · pie derecho', x: 1600, y: 0, z: 0 },
    { id: 'n3', label: 'C · pie posterior izquierdo', x: 0, y: 1200, z: 0 },
    { id: 'n4', label: 'D · pie posterior derecho', x: 1600, y: 1200, z: 0 },
    { id: 'n5', label: 'E · coronación izquierda', x: 0, y: 600, z: 1800 },
    { id: 'n6', label: 'F · coronación derecha', x: 1600, y: 600, z: 1800 },
  ];
  const profile = { type: 'rhs', height: 80, width: 80, tw: 4, tf: 4 };
  p.members = [
    ['v1', 'Pata frontal izquierda', 'n1', 'n5'], ['v2', 'Pata frontal derecha', 'n2', 'n6'],
    ['v3', 'Pata posterior izquierda', 'n3', 'n5'], ['v4', 'Pata posterior derecha', 'n4', 'n6'],
    ['v5', 'Travesaño superior', 'n5', 'n6'], ['v6', 'Base frontal', 'n1', 'n2'],
    ['v7', 'Base posterior', 'n3', 'n4'], ['v8', 'Arriostre lateral', 'n1', 'n3'],
  ].map(([id, label, startNode, endNode]) => ({ id, label, startNode, endNode, profile: { ...profile }, roll: 0, status: 'estimated', note: 'Ejemplo sintético para practicar. No es una medición de terreno.', lengthMeasured: null, photos: [] }));
  return validateProject(p);
}

export function validateProfile(profile, label = 'Perfil') {
  object(profile, label);
  if (!TYPES.includes(profile.type)) fail(`${label}: tipo de perfil no reconocido.`);
  const h = number(profile.height, `${label} · alto`, 0.001, 1e6);
  const w = number(profile.width, `${label} · ancho`, 0.001, 1e6);
  const tw = number(profile.tw, `${label} · espesor de alma/pared`, 0, 1e6);
  const tf = number(profile.tf, `${label} · espesor de ala`, 0, 1e6);
  if (tw >= w || tf >= h) fail(`${label}: los espesores deben ser menores que las dimensiones exteriores.`);
  if (profile.type === 'rhs' || profile.type === 'i' || profile.type === 'channel') {
    if (tw <= 0 || tf <= 0) fail(`${label}: ambos espesores deben ser mayores que cero.`);
    if (2 * tf >= h || (profile.type === 'rhs' && 2 * tw >= w)) fail(`${label}: los espesores no dejan una sección interior válida.`);
  }
  if (profile.type === 'round' && tw > 0 && 2 * tw >= Math.min(h, w)) fail(`${label}: el espesor debe ser menor que la mitad del diámetro.`);
  if (profile.type === 'round' && Math.abs(h - w) > 1e-6) fail(`${label}: un perfil circular debe tener alto y ancho iguales al diámetro.`);
  if (profile.type === 'custom' && (2 * tw >= w || 2 * tf >= h)) fail(`${label}: los espesores del perfil personalizado deben ser menores que la mitad de sus dimensiones exteriores; use cero si no aplican.`);
  return profile;
}

export function validateProject(project) {
  const p = object(project, 'Proyecto');
  id(p.id, 'Proyecto'); text(p.name, 'Nombre del proyecto', 180); text(p.structure, 'Estructura', 240);
  if (!Object.hasOwn(UNITS, p.unit)) fail('Unidad no válida. Use mm, cm o m.');
  date(p.createdAt, 'Fecha de creación'); date(p.updatedAt, 'Fecha de actualización');
  if (typeof p.example !== 'boolean') fail('El indicador de ejemplo debe ser verdadero o falso.');
  list(p.nodes, 'Nodos', LIMITS.nodes); list(p.members, 'Elementos', LIMITS.members);
  const nodes = new Set(); const members = new Set(); const photos = new Set(); let photoChars = 0;
  for (const n of p.nodes) {
    object(n, 'Nodo'); id(n.id, 'Nodo'); duplicate(nodes, n.id, 'Nodo'); text(n.label, 'Nombre del nodo', 180);
    for (const axis of ['x', 'y', 'z']) number(n[axis], `${n.label} · ${axis.toUpperCase()}`, -LIMITS.coordinate, LIMITS.coordinate);
  }
  for (const m of p.members) {
    object(m, 'Elemento'); id(m.id, 'Elemento'); duplicate(members, m.id, 'Elemento'); text(m.label, 'Nombre del elemento', 180);
    id(m.startNode, `${m.label} · nodo inicial`); id(m.endNode, `${m.label} · nodo final`);
    if (!nodes.has(m.startNode) || !nodes.has(m.endNode)) fail(`${m.label}: un extremo hace referencia a un nodo inexistente.`);
    if (m.startNode === m.endNode || lengthOf(p, m) < 0.001) fail(`${m.label}: los extremos deben ser distintos y la longitud mayor que cero.`);
    validateProfile(m.profile, m.label);
    number(m.roll, `${m.label} · giro`, -36000, 36000);
    if (!STATES.includes(m.status)) fail(`${m.label}: estado no válido.`);
    text(m.note, `${m.label} · observaciones`, 4000, true);
    if (m.lengthMeasured !== null) number(m.lengthMeasured, `${m.label} · longitud medida`, 0.001, 1e8);
    if (m.status !== 'estimated' && (m.lengthMeasured === null || !m.note.trim())) fail(`${m.label}: una ficha medida o confirmada requiere longitud medida mayor que cero y método/observaciones.`);
    list(m.photos, `${m.label} · fotos`, LIMITS.photosPerMember);
    for (const photo of m.photos) {
      object(photo, 'Foto'); id(photo.id, 'Foto'); duplicate(photos, photo.id, 'Foto'); text(photo.name, 'Nombre de foto', 240); date(photo.createdAt, 'Fecha de foto');
      text(photo.dataUrl, 'Contenido de foto', LIMITS.photoChars);
      if (!/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/]+={0,2}$/.test(photo.dataUrl)) fail('Foto inválida: solo se permiten imágenes JPEG, PNG o WebP incrustadas, nunca enlaces externos.');
      photoChars += photo.dataUrl.length;
      if (photos.size > LIMITS.photos || photoChars > 32 * 1024 * 1024) fail('El proyecto excede el límite de 120 fotos o 32 MB de fotos codificadas. Exporte y divida el levantamiento.');
    }
  }
  if (p.features !== undefined) {
    list(p.features, 'Componentes de vista preliminar', 40);
    const featureIds = new Set();
    for (const f of p.features) {
      object(f,'Componente');id(f.id,'Componente');duplicate(featureIds,f.id,'Componente');text(f.label,'Componente',180);
      if(f.type!=='saddle-preview'||f.status!=='estimated'||f.exportable!==false||f.source!=='synthetic-family')fail('Componente preliminar no reconocido o marcado incorrectamente.');
      for(const axis of ['x','y','z'])number(f[axis],`Componente ${axis}`,-LIMITS.coordinate,LIMITS.coordinate);
      for(const axis of ['width','length','radius','thickness'])number(f[axis],`Componente ${axis}`,0.001,LIMITS.coordinate);
      if(f.width>=2*f.radius||f.thickness>=f.radius)fail('Cuna preliminar: ancho o espesor no compatible con el radio.');
    }
  }
  let serialized;
  try { serialized = JSON.stringify(p); } catch { fail('El proyecto contiene datos no serializables. No se guardó ni se modificó el respaldo.'); }
  if (utf8Size(serialized) > LIMITS.jsonChars - 1024) fail('El proyecto supera el límite seguro de respaldo de 40 MB. Exporte y divida el levantamiento antes de agregar más datos.');
  return p;
}

export function lengthOf(project, member) {
  const a = project.nodes.find(n => n.id === member.startNode);
  const b = project.nodes.find(n => n.id === member.endNode);
  if (!a || !b) fail('No se puede calcular la longitud: falta un nodo de extremo.');
  return Math.hypot(b.x - a.x, b.y - a.y, b.z - a.z);
}

export function parseMeasure(value, unit = 'mm') {
  if (!Object.hasOwn(UNITS, unit)) fail('Unidad no válida. Use mm, cm o m.');
  const raw = typeof value === 'number' ? String(value) : typeof value === 'string' ? value.trim() : '';
  if (!/^(?:\d+(?:[.,]\d*)?|[.,]\d+)$/.test(raw)) fail('Ingrese una medida numérica no negativa; puede usar coma o punto decimal.');
  const mm = Number(raw.replace(',', '.')) * UNITS[unit];
  return number(mm, 'Medida', 0, LIMITS.coordinate);
}

export function toUnit(mm, unit = 'mm') {
  if (!Object.hasOwn(UNITS, unit)) fail('Unidad no válida. Use mm, cm o m.');
  number(mm, 'Medida', -LIMITS.coordinate * 4, LIMITS.coordinate * 4);
  return Math.round((mm / UNITS[unit]) * 1e8) / 1e8;
}

export function serializeProject(project, includePhotos = true) {
  validateProject(project);
  const p = clone(project);
  if (!includePhotos) for (const m of p.members) m.photos = [];
  const data = { format: FORMAT, version: VERSION, exportedAt: new Date().toISOString(), photoPolicy: includePhotos ? 'embedded-local-images' : 'excluded', project: p };
  const pretty = JSON.stringify(data, null, 2);
  // Keep the exported backup inside the same bound enforced during import.
  return utf8Size(pretty) <= LIMITS.jsonChars ? pretty : JSON.stringify(data);
}

export function parseProject(json) {
  if (typeof json !== 'string' || json.length > LIMITS.jsonChars || utf8Size(json) > LIMITS.jsonChars) fail('El archivo JSON debe ser texto y no superar 40 MB.');
  let data;
  try { data = JSON.parse(json); } catch { fail('No se pudo leer el JSON. El archivo está incompleto o no tiene un formato válido.'); }
  object(data, 'Archivo');
  if (data.format !== FORMAT || data.version !== VERSION) fail('Archivo incompatible: se requiere levantamiento-estructural, versión 1.');
  return clone(validateProject(data.project));
}

function storageError(error, action) {
  const quota = ['QuotaExceededError', 'NS_ERROR_DOM_QUOTA_REACHED'].includes(error?.name);
  return new Error(quota ? `No hay espacio local suficiente para ${action}. No se confirmó el guardado. Exporte un respaldo y libere espacio o reduzca fotos.` : `No se pudo ${action} en este navegador. ${error?.message || 'Almacenamiento local no disponible.'} Exporte un respaldo antes de cerrar.`);
}

async function database() {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise(resolve => {
    if (!globalThis.indexedDB) return resolve(null);
    let request;
    try { request = globalThis.indexedDB.open(DB_NAME, 1); } catch { return resolve(null); }
    let done = false;
    const finish = db => { if (!done) { done = true; resolve(db); } else db?.close(); };
    request.onupgradeneeded = () => { if (!request.result.objectStoreNames.contains('projects')) request.result.createObjectStore('projects', { keyPath: 'id' }); };
    request.onsuccess = () => {
      const db = request.result;
      db.onversionchange = () => { db.close(); dbPromise = undefined; mode = 'uninitialized'; warning = 'El almacenamiento cambió en otra pestaña. Recargue antes de editar.'; };
      finish(db);
    };
    request.onerror = () => finish(null);
    request.onblocked = () => { warning = 'IndexedDB está bloqueado por otra pestaña. Cierre otras pestañas del levantamiento y recargue.'; finish(null); };
  }).then(db => {
    if (db) { mode = 'indexeddb'; warning = 'Guardado en este navegador y dispositivo. Borrar datos del sitio elimina los proyectos. Exporte respaldos.'; return db; }
    try {
      const ls = globalThis.localStorage;
      if (!ls) throw new Error('Sin localStorage');
      const key = `${LOCAL_KEY}.probe`;
      ls.setItem(key, '1'); ls.removeItem(key);
      mode = 'localstorage'; warning = 'Modo local de capacidad reducida (localStorage). Las fotos pueden agotar el espacio. Exporte respaldos frecuentes.';
      if (!globalThis.navigator?.locks?.request) warning += ' Este navegador no permite bloquear otras pestañas: edite desde una sola pestaña.';
    } catch { mode = 'unavailable'; warning = 'Este navegador no permite guardar datos. No cierre sin exportar un respaldo.'; }
    return null;
  });
  return dbPromise;
}

function readLocal() {
  const raw = globalThis.localStorage.getItem(LOCAL_KEY);
  if (raw === null) return [];
  let values;
  try { values = JSON.parse(raw); } catch { fail('Los datos locales no son legibles. No se sobrescribieron. Use un respaldo JSON o revise el almacenamiento.'); }
  list(values, 'Proyectos locales', LIMITS.projects);
  const ids = new Set();
  for (const p of values) { validateProject(p); duplicate(ids, p.id, 'Proyecto local'); }
  return values;
}

export function storageInfo() { return { mode, warning }; }

async function localLock(action) {
  if (globalThis.navigator?.locks?.request) return globalThis.navigator.locks.request(`${DB_NAME}.write`, async () => action());
  // The synchronous read/check/write remains best effort without Web Locks.
  // The reduced concurrency guarantee is surfaced by storageInfo().warning.
  return action();
}

function prepareSave(project, existing, expectedUpdatedAt) {
  if (expectedUpdatedAt !== undefined && (!existing || existing.updatedAt !== expectedUpdatedAt)) {
    const error = new Error('Conflicto: este proyecto cambió o fue eliminado en otra pestaña. No se sobrescribieron los datos. Exporte sus cambios si necesita conservarlos y recargue el proyecto antes de continuar.');
    error.name = 'ProjectConflictError';
    throw error;
  }
  // Millisecond timestamps are used as revision tokens. Ensure every save advances
  // the revision even when several writes happen within the same millisecond.
  project.updatedAt = new Date(Math.max(Date.now(), existing ? Date.parse(existing.updatedAt) + 1 : 0)).toISOString();
}

export async function loadProjects() {
  const db = await database();
  if (mode === 'unavailable') throw storageError(null, 'cargar proyectos');
  try {
    let projects;
    if (db) projects = await new Promise((resolve, reject) => {
      const tx = db.transaction('projects', 'readonly'); const req = tx.objectStore('projects').getAll();
      let values;
      req.onsuccess = () => { values = req.result; };
      tx.oncomplete = () => resolve(values || []); tx.onerror = () => reject(tx.error); tx.onabort = () => reject(tx.error || new Error('Lectura cancelada'));
    });
    else projects = readLocal();
    list(projects, 'Proyectos locales', LIMITS.projects);
    for (const p of projects) validateProject(p);
    return projects.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  } catch (e) { throw storageError(e, 'cargar proyectos'); }
}

export async function saveProject(project, expectedUpdatedAt) {
  validateProject(project);
  if (expectedUpdatedAt !== undefined) date(expectedUpdatedAt, 'Revisión esperada');
  const p = clone(project);
  const db = await database();
  if (mode === 'unavailable') throw storageError(null, 'guardar el proyecto');
  try {
    if (db) await new Promise((resolve, reject) => {
      const tx = db.transaction('projects', 'readwrite'); const store = tx.objectStore('projects');
      const count = store.count();
      count.onsuccess = () => {
        const existing = store.get(p.id);
        existing.onsuccess = () => {
          if (count.result >= LIMITS.projects && existing.result === undefined) { tx.abort(); reject(new Error('Máximo de 100 proyectos locales alcanzado.')); return; }
          try { prepareSave(p, existing.result, expectedUpdatedAt); } catch (error) { tx.abort(); reject(error); return; }
          store.put(p);
        };
      };
      tx.oncomplete = resolve; tx.onerror = () => reject(tx.error); tx.onabort = () => reject(tx.error || new Error('Guardado cancelado; se conservaron los datos previos.'));
    });
    else await localLock(() => {
      const projects = readLocal(); const i = projects.findIndex(x => x.id === p.id);
      prepareSave(p, i >= 0 ? projects[i] : undefined, expectedUpdatedAt);
      if (i >= 0) projects[i] = p; else { if (projects.length >= LIMITS.projects) fail('Máximo de 100 proyectos locales alcanzado.'); projects.push(p); }
      globalThis.localStorage.setItem(LOCAL_KEY, JSON.stringify(projects));
    });
    return { mode, savedAt: p.updatedAt };
  } catch (e) { if (e?.name === 'ProjectConflictError') throw e; throw storageError(e, 'guardar el proyecto'); }
}

export async function removeProject(projectId) {
  id(projectId, 'Proyecto'); const db = await database();
  if (mode === 'unavailable') throw storageError(null, 'eliminar el proyecto');
  try {
    if (db) await new Promise((resolve, reject) => {
      const tx = db.transaction('projects', 'readwrite'); tx.objectStore('projects').delete(projectId);
      tx.oncomplete = resolve; tx.onerror = () => reject(tx.error); tx.onabort = () => reject(tx.error || new Error('Eliminación cancelada.'));
    });
    else await localLock(() => globalThis.localStorage.setItem(LOCAL_KEY, JSON.stringify(readLocal().filter(p => p.id !== projectId))));
    return { mode };
  } catch (e) { throw storageError(e, 'eliminar el proyecto'); }
}
