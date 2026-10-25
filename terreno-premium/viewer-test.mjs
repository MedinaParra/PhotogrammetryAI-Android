/** Reproducible viewer unit tests; run with `node viewer-test.mjs`.
 * Canvas, window, animation frames and pointer events are SIMULATED in Node.
 * These tests check geometry and event-handler logic, NOT real browser layout,
 * rendered pixels, mobile touch behavior, camera access or device permissions.
 */
import assert from 'node:assert/strict';
import { StructureViewer } from './dist/viewer.js';

let passed = 0;
const near = (a, b, tolerance = 1e-7) => assert(Math.abs(a - b) <= tolerance, `${a} ≈ ${b}`);
const copy = value => JSON.parse(JSON.stringify(value));
const frames = new Map();
const windowListeners = new Set();
let frameId = 0;
globalThis.window = {
  devicePixelRatio: 2,
  addEventListener: (name, fn) => windowListeners.add(fn),
  removeEventListener: (name, fn) => windowListeners.delete(fn),
};
globalThis.requestAnimationFrame = fn => { frames.set(++frameId, fn); return frameId; };
globalThis.cancelAnimationFrame = id => frames.delete(id);

function canvasStub(width = 390, height = 350) {
  const attributes = new Map();
  const listeners = new Map();
  const calls = [];
  const context = new Proxy({ measureText: text => ({ width: text.length * 7 }) }, {
    get: (object, key) => key in object ? object[key] : (...args) => calls.push([key, ...args]),
    set: (object, key, value) => (object[key] = value, true),
  });
  return {
    style: {}, listeners, calls, context,
    getContext: () => context,
    hasAttribute: name => attributes.has(name),
    setAttribute: (name, value) => attributes.set(name, value),
    addEventListener: (name, fn) => listeners.set(name, fn),
    removeEventListener: (name, fn) => { if (listeners.get(name) === fn) listeners.delete(name); },
    getBoundingClientRect: () => ({ left: 0, top: 0, width, height }),
    focus() {}, setPointerCapture() {}, releasePointerCapture() {},
  };
}

function projectFixture() {
  return {
    id: 'synthetic-viewer-test',
    nodes: [{ id: 'a', x: 0, y: 0, z: 0 }, { id: 'b', x: 1000, y: 0, z: 0 }],
    members: [{
      id: 'v1', label: 'Viga de prueba', startNode: 'a', endNode: 'b',
      profile: { type: 'rhs', height: 100, width: 60, tw: 5, tf: 5 },
      roll: 0, status: 'estimated',
    }],
  };
}

function fixture(width, height) {
  const canvas = canvasStub(width, height);
  const moves = [], selections = [], cameraChanges = [], orientationRequests = [];
  const viewer = new StructureViewer(canvas, {
    onMove: value => moves.push(value), onSelect: value => selections.push(value),
    onCameraChange: value => cameraChanges.push(value), onOrientationRequest: () => orientationRequests.push(true),
  });
  const project = projectFixture();
  viewer.setData(project, 'v1');
  return { canvas, viewer, project, moves, selections, cameraChanges, orientationRequests };
}

const pointer = (x, y, id = 1, timeStamp) => ({
  pointerId: id, button: 0, clientX: x, clientY: y, timeStamp, preventDefault() {},
});
const test = (name, fn) => {
  fn(); passed++; console.log(`✓ ${name}`);
};

test('Prisma en mm: dimensiones, orientación y roll de 90°', () => {
  const { viewer, project } = fixture();
  project.members[0].profile.type = 'rect';
  const spans = () => {
    const points = viewer.geometry(project.members[0]).flat();
    return [0, 1, 2].map(axis => Math.max(...points.map(p => p[axis])) - Math.min(...points.map(p => p[axis])));
  };
  assert.deepEqual(spans(), [1000, 60, 100]);
  project.members[0].roll = 90;
  const rotated = spans(); near(rotated[0], 1000); near(rotated[1], 100); near(rotated[2], 60);
  viewer.destroy();
});

test('Cinco tipos de perfil: caras finitas en vigas horizontales, verticales y diagonales', () => {
  const { viewer, project } = fixture();
  const expectedFaces = { rect: 6, rhs: 24, i: 18, round: 96, custom: 6 };
  for (const [type, count] of Object.entries(expectedFaces)) {
    project.members[0].profile.type = type;
    for (const end of [[1000, 0, 0], [0, 0, 1000], [400, 700, 900]]) {
      [project.nodes[1].x, project.nodes[1].y, project.nodes[1].z] = end;
      const faces = viewer.geometry(project.members[0]);
      assert.equal(faces.length, count);
      assert(faces.flat(2).every(Number.isFinite));
    }
  }
  viewer.destroy();
});

test('Proyección frontal/superior/lateral y encuadre con tamaños móvil y escritorio simulados', () => {
  for (const [width, height] of [[390, 350], [1200, 700]]) {
    const { viewer } = fixture(width, height);
    viewer.preset('front');
    const origin = viewer.projectPoint([0, 0, 0]);
    const end = viewer.projectPoint([1000, 0, 0]);
    near(end.x - origin.x, 1000 * viewer.scale); near(end.y, origin.y);
    assert(origin.x > 0 && end.x < width);
    assert(viewer.projectPoint([0, 0, 100]).y < origin.y);
    viewer.preset('top');
    const topOrigin = viewer.projectPoint([0, 0, 0]);
    assert(viewer.projectPoint([0, 100, 0]).y > topOrigin.y);
    near(viewer.projectPoint([0, 0, 100]).y, topOrigin.y);
    viewer.preset('side');
    const sideOrigin = viewer.projectPoint([0, 0, 0]);
    assert(viewer.projectPoint([0, 100, 0]).x < sideOrigin.x);
    near(viewer.projectPoint([100, 0, 0]).x, sideOrigin.x);
    viewer.destroy();
  }
});

test('Mover emite delta en mm solo al soltar y no modifica el proyecto', () => {
  const { viewer, project, moves } = fixture();
  const original = copy(project);
  viewer.preset('front'); viewer.setMode('move');
  const scale = viewer.scale;
  viewer.pointerDown(pointer(100, 100)); viewer.pointerMove(pointer(120, 90));
  assert.equal(moves.length, 0); assert(viewer.preview);
  assert.deepEqual(project, original);
  viewer.pointerUp(pointer(120, 90));
  assert.equal(moves.length, 1); assert.equal(moves[0].memberId, 'v1');
  near(moves[0].dx, 20 / scale, .1); assert.equal(moves[0].dy, 0); near(moves[0].dz, 10 / scale, .1);
  assert.deepEqual(project, original); assert.equal(viewer.preview, null);
  viewer.destroy();
});

test('Multitáctil simulado: pellizco cambia zoom y cancela desplazamiento pendiente', () => {
  const { viewer, moves } = fixture();
  viewer.setMode('move');
  viewer.pointerDown(pointer(100, 100)); viewer.pointerMove(pointer(110, 100));
  viewer.pointerDown(pointer(130, 130, 2));
  const before = viewer.zoomFactor;
  viewer.pointerMove(pointer(160, 160, 2));
  assert(viewer.zoomFactor > before);
  viewer.pointerUp(pointer(110, 100)); viewer.pointerUp(pointer(160, 160, 2));
  assert.equal(moves.length, 0); assert.equal(viewer.preview, null);
  viewer.destroy();
});

test('Selección por etiqueta y cara, y tap vacío sin selección', () => {
  const { viewer, selections } = fixture();
  viewer.preset('front'); viewer.draw();
  const label = viewer.labelHits[0]; assert(label);
  assert.equal(viewer.pick({ x: label.x + label.w / 2, y: label.y + label.h / 2 }), 'v1');
  const center = viewer.projectPoint([500, 0, 0]); assert.equal(viewer.pick(center), 'v1');
  viewer.pointerDown(pointer(center.x, center.y)); viewer.pointerUp(pointer(center.x, center.y));
  assert.equal(selections.at(-1), 'v1');
  viewer.pointerDown(pointer(-100, -100)); viewer.pointerUp(pointer(-100, -100));
  assert.equal(selections.at(-1), null);
  viewer.destroy();
});

test('Órbita y teclado cambian cámara, no nodos ni geometría guardada', () => {
  const { viewer, project, moves } = fixture();
  const original = copy(project), initialYaw = viewer.yaw;
  viewer.pointerDown(pointer(100, 100)); viewer.pointerMove(pointer(150, 120)); viewer.pointerUp(pointer(150, 120));
  assert.notEqual(viewer.yaw, initialYaw); assert.equal(moves.length, 0);
  const before = viewer.yaw;
  viewer.keyDown({ key: 'ArrowRight', preventDefault() {} }); near(viewer.yaw, before + .12);
  viewer.keyDown({ key: 'Home', preventDefault() {} }); assert.equal(viewer.zoomFactor, 1);
  assert.deepEqual(project, original); viewer.destroy();
});

test('Zoom acotado y coordenadas proyectadas finitas', () => {
  const { viewer } = fixture();
  viewer.zoom(999); assert.equal(viewer.zoomFactor, 12);
  viewer.zoom(-999); assert.equal(viewer.zoomFactor, .15);
  viewer.zoom(Number.NaN); assert.equal(viewer.zoomFactor, .15);
  assert(Object.values(viewer.projectPoint([100, 200, 300])).every(Number.isFinite));
  viewer.destroy();
});

test('Canvas simulado dibuja cada perfil y estado vacío; resolución DPR correcta', () => {
  const { viewer, project, canvas } = fixture();
  assert.equal(canvas.width, 780); assert.equal(canvas.height, 700);
  for (const type of ['rect', 'rhs', 'i', 'round', 'custom']) {
    project.members[0].profile.type = type; viewer.draw();
    assert(viewer.hits.length > 0); assert.equal(viewer.labelHits.length, 1);
  }
  viewer.setData({ id: 'empty', nodes: [], members: [] }); viewer.draw();
  assert.equal(viewer.hits.length, 0);
  assert(canvas.calls.some(([name, text]) => name === 'fillText' && text === 'Tu estructura empieza aquí'));
  viewer.destroy();
});

test('Cancelar puntero y destruir limpian previsualización, listeners y animaciones', () => {
  const count = windowListeners.size;
  const { viewer, canvas, moves } = fixture(); viewer.setMode('move');
  assert(windowListeners.size > count); assert(canvas.listeners.size > 0);
  viewer.pointerDown(pointer(100, 100)); viewer.pointerMove(pointer(120, 110));
  viewer.pointerCancel(pointer(120, 110));
  assert.equal(viewer.preview, null); assert.equal(viewer.gesture, null); assert.equal(moves.length, 0);
  const frame = viewer.frame; assert(frames.has(frame));
  viewer.destroy(); assert.equal(canvas.listeners.size, 0); assert.equal(windowListeners.size, count); assert(!frames.has(frame));
});

test('Azimut conserva pitch, pivote, zoom y escalas sin llamar fit ni cambiar geometría', () => {
  const { viewer, project, moves, cameraChanges } = fixture();
  viewer.zoom(.7);viewer.center=[120,340,560];
  const before=viewer.getCameraState(),original=copy(project),calls=cameraChanges.length;
  viewer.fit=()=>assert.fail('rotateAzimuth no debe encuadrar');
  assert.equal(viewer.rotateAzimuth(Math.PI/3),true);
  const after=viewer.getCameraState();near(after.yaw,before.yaw+Math.PI/3);
  for(const key of ['pitch','center','zoomFactor','scale','baseScale'])assert.deepEqual(after[key],before[key]);
  assert.equal(cameraChanges.length,calls+1);assert.deepEqual(cameraChanges.at(-1),after);
  assert.deepEqual(project,original);assert.equal(moves.length,0);viewer.destroy();
});

test('Snapshot cámara independiente y azimut inválido no altera ni notifica', () => {
  const { viewer,cameraChanges }=fixture(),before=viewer.getCameraState(),count=cameraChanges.length;
  const snapshot=viewer.getCameraState();snapshot.center[0]=1e9;snapshot.yaw=99;
  assert.deepEqual(viewer.getCameraState(),before);
  for(const value of [NaN,Infinity,-Infinity,'0.2',null,undefined])assert.equal(viewer.rotateAzimuth(value),false);
  assert.equal(viewer.rotateAzimuth(0),true);assert.equal(cameraChanges.length,count);
  assert.deepEqual(viewer.getCameraState(),before);viewer.destroy();
});

test('Callbacks cámara cubren preset, órbita, zoom, teclado y encuadre con snapshot seguro', () => {
  const { viewer,cameraChanges }=fixture();
  const changes=action=>{const count=cameraChanges.length;action();assert(cameraChanges.length>count);assert.deepEqual(cameraChanges.at(-1),viewer.getCameraState());};
  changes(()=>viewer.preset('top'));changes(()=>viewer.zoom(.2));
  changes(()=>{viewer.pointerDown(pointer(100,100));viewer.pointerMove(pointer(120,110));viewer.pointerUp(pointer(120,110));});
  changes(()=>viewer.keyDown({key:'ArrowLeft',preventDefault(){}}));changes(()=>viewer.fit());
  const before=viewer.getCameraState(),count=cameraChanges.length;
  assert.equal(viewer.preset('constructor'),false);assert.equal(viewer.preset('unknown'),false);
  assert.deepEqual(viewer.getCameraState(),before);assert.equal(cameraChanges.length,count);
  cameraChanges.at(-1).center[1]=9999;assert.notEqual(viewer.center[1],9999);viewer.destroy();
});

test('Dos dedos: tap corto abre orientación una vez sin seleccionar ni mover cámara/geometría', () => {
  for(const mode of ['orbit','move']) {
    const {viewer,project,moves,selections,orientationRequests,cameraChanges}=fixture();viewer.setMode(mode);
    const camera=viewer.getCameraState(),original=copy(project),count=cameraChanges.length;
    viewer.pointerDown(pointer(100,100,1,1000));viewer.pointerDown(pointer(160,100,2,1020));
    viewer.pointerMove(pointer(102,101,1,1060));viewer.pointerMove(pointer(162,101,2,1065));
    viewer.pointerUp(pointer(102,101,1,1120));assert.equal(orientationRequests.length,0);
    viewer.pointerUp(pointer(162,101,2,1140));assert.equal(orientationRequests.length,1);
    assert.deepEqual(viewer.getCameraState(),camera);assert.equal(cameraChanges.length,count);
    assert.deepEqual(project,original);assert.equal(moves.length,0);assert.equal(selections.length,0);viewer.destroy();
  }
});

test('Límites tap:280ms se admite;281ms,8px o pulsación previa larga se cancelan', () => {
  for(const [duration,travel,expected] of [[280,0,1],[281,0,0],[150,7.9,1],[150,8,0]]) {
    const {viewer,orientationRequests,moves}=fixture();
    viewer.pointerDown(pointer(100,100,1,100));viewer.pointerDown(pointer(160,100,2,120));
    // Both fingers move together, so this tests per-finger travel not pinch.
    viewer.pointerUp(pointer(100,100+travel,1,100+duration));viewer.pointerUp(pointer(160,100+travel,2,100+duration));
    assert.equal(orientationRequests.length,expected,`${duration}ms,${travel}px`);assert.equal(moves.length,0);viewer.destroy();
  }
  const {viewer,orientationRequests}=fixture();viewer.pointerDown(pointer(100,100,1,0));viewer.pointerDown(pointer(160,100,2,500));
  viewer.pointerUp(pointer(100,100,1,550));viewer.pointerUp(pointer(160,100,2,560));assert.equal(orientationRequests.length,0);viewer.destroy();
});

test('Pan de dos dedos desplaza solo cámara y conserva escala, yaw y pitch finales', () => {
  for(const mode of ['orbit','move']) {
    const {viewer,project,moves,selections,orientationRequests,cameraChanges}=fixture();viewer.setMode(mode);
    const before=viewer.getCameraState(),original=copy(project),point=viewer.projectPoint([300,0,0]),count=cameraChanges.length;
    viewer.pointerDown(pointer(100,100,1,0));viewer.pointerDown(pointer(160,100,2,10));
    viewer.pointerMove(pointer(130,120,1,50));viewer.pointerMove(pointer(190,120,2,60));
    viewer.pointerUp(pointer(130,120,1,90));viewer.pointerUp(pointer(190,120,2,100));
    const after=viewer.getCameraState(),projected=viewer.projectPoint([300,0,0]);
    near(projected.x-point.x,30);near(projected.y-point.y,20);
    for(const key of ['yaw','pitch','zoomFactor','scale','baseScale'])near(after[key],before[key]);
    assert.notDeepEqual(after.center,before.center);assert(cameraChanges.length>count);
    assert.deepEqual(project,original);assert.equal(moves.length,0);assert.equal(selections.length,0);assert.equal(orientationRequests.length,0);viewer.destroy();
  }
});

test('Pinch mantiene el punto ancla bajo el centro táctil y nunca abre orientación', () => {
  const {viewer,project,moves,orientationRequests}=fixture();viewer.setMode('move');
  const original=copy(project),before=viewer.getCameraState(),mid={x:130,y:100},axes=viewer.cameraAxes();
  const anchor=viewer.center.map((c,i)=>c+axes.right[i]*(mid.x-viewer.width/2)/viewer.scale+axes.down[i]*(mid.y-viewer.height/2)/viewer.scale);
  viewer.pointerDown(pointer(100,100,1,0));viewer.pointerDown(pointer(160,100,2,10));
  viewer.pointerMove(pointer(80,100,1,40));viewer.pointerMove(pointer(180,100,2,50));
  viewer.pointerUp(pointer(80,100,1,80));viewer.pointerUp(pointer(180,100,2,90));
  near(viewer.zoomFactor/before.zoomFactor,100/60);const p=viewer.projectPoint(anchor);near(p.x,mid.x);near(p.y,mid.y);
  near(viewer.yaw,before.yaw);near(viewer.pitch,before.pitch);assert.deepEqual(project,original);
  assert.equal(moves.length,0);assert.equal(orientationRequests.length,0);viewer.destroy();
});

test('Pequeño pinch neto8px se distingue de tap aunque cada dedo recorra4px', () => {
  const {viewer,orientationRequests}=fixture();const before=viewer.zoomFactor;
  viewer.pointerDown(pointer(100,100,1,0));viewer.pointerDown(pointer(160,100,2,10));
  viewer.pointerMove(pointer(96,100,1,50));viewer.pointerMove(pointer(164,100,2,60));
  viewer.pointerUp(pointer(96,100,1,90));viewer.pointerUp(pointer(164,100,2,100));
  assert(viewer.zoomFactor>before);assert.equal(orientationRequests.length,0);viewer.destroy();
});

test('Tercer dedo cancela todo el ciclo incluso al volver a dos dedos', () => {
  const {viewer,moves,selections,orientationRequests}=fixture();viewer.setMode('move');const before=viewer.getCameraState();
  viewer.pointerDown(pointer(100,100,1,0));viewer.pointerDown(pointer(160,100,2,10));viewer.pointerDown(pointer(130,160,3,20));
  viewer.pointerMove(pointer(120,120,1,40));viewer.pointerUp(pointer(130,160,3,50));
  viewer.pointerMove(pointer(200,100,2,60));viewer.pointerUp(pointer(120,120,1,90));viewer.pointerUp(pointer(200,100,2,100));
  assert.deepEqual(viewer.getCameraState(),before);assert.equal(orientationRequests.length,0);assert.equal(moves.length,0);assert.equal(selections.length,0);viewer.destroy();
});

test('pointercancel y gesto largo no abren cubo; próximo gesto funciona normalmente', () => {
  const {viewer,orientationRequests,moves}=fixture();viewer.setMode('move');
  viewer.pointerDown(pointer(100,100,1,0));viewer.pointerDown(pointer(160,100,2,10));viewer.pointerCancel(pointer(100,100,1,50));
  viewer.pointerMove(pointer(190,120,2,60));viewer.pointerUp(pointer(190,120,2,70));assert.equal(orientationRequests.length,0);assert.equal(moves.length,0);
  viewer.pointerDown(pointer(100,100,1,100));viewer.pointerDown(pointer(160,100,2,110));viewer.pointerUp(pointer(100,100,1,500));viewer.pointerUp(pointer(160,100,2,510));assert.equal(orientationRequests.length,0);
  viewer.pointerDown(pointer(100,100,1,600));viewer.pointerDown(pointer(160,100,2,610));viewer.pointerUp(pointer(100,100,1,680));viewer.pointerUp(pointer(160,100,2,690));assert.equal(orientationRequests.length,1);viewer.destroy();
});

test('Después de multitáctil el dedo restante no mueve geometría ni selecciona', () => {
  const {viewer,project,moves,selections,orientationRequests}=fixture();viewer.setMode('move');const original=copy(project);
  viewer.pointerDown(pointer(100,100,1,0));viewer.pointerDown(pointer(160,100,2,10));viewer.pointerUp(pointer(100,100,1,60));
  viewer.pointerMove(pointer(220,160,2,70));viewer.pointerUp(pointer(220,160,2,90));
  assert.equal(moves.length,0);assert.equal(selections.length,0);assert.equal(orientationRequests.length,0);assert.deepEqual(project,original);viewer.destroy();
});

test('Mover previo, cambio de modo y cancelación tardía no disparan picker', () => {
  for(const action of ['priorMove','mode','cancel']) {
    const {viewer,moves,orientationRequests}=fixture();viewer.setMode('move');
    viewer.pointerDown(pointer(100,100,1,0));if(action==='priorMove')viewer.pointerMove(pointer(110,100,1,5));
    viewer.pointerDown(pointer(160,100,2,10));
    if(action==='mode')viewer.setMode('orbit');
    if(action==='cancel')viewer.pointerCancel(pointer(100,100,1,50));else viewer.pointerUp(pointer(action==='priorMove'?110:100,100,1,50));
    viewer.pointerUp(pointer(160,100,2,70));assert.equal(moves.length,0);assert.equal(orientationRequests.length,0);viewer.destroy();
  }
});

test('Etiquetas por defecto solo seleccionada; all es opt-in y ejes siguen sin footer duplicado', () => {
  const {viewer,project,canvas}=fixture();
  project.nodes.push({id:'c',x:1000,y:0,z:500});project.members.push({...copy(project.members[0]),id:'v2',label:'Segunda',startNode:'b',endNode:'c'});
  viewer.draw();assert.equal(viewer.labelMode,'selected');assert.deepEqual(viewer.labelHits.map(h=>h.id),['v1']);
  viewer.setData(project,null);viewer.draw();assert.equal(viewer.labelHits.length,0);
  assert.equal(viewer.setLabelMode('all'),true);viewer.draw();assert.equal(viewer.labelHits.length,2);
  assert.equal(viewer.setLabelMode('invalid'),false);assert.equal(viewer.labelMode,'all');
  viewer.setLabelMode('selected');viewer.setData(project,'v2');viewer.draw();assert.deepEqual(viewer.labelHits.map(h=>h.id),['v2']);
  for(const axis of ['X','Y','Z'])assert(canvas.calls.some(([name,text])=>name==='fillText'&&text===axis));
  assert(!canvas.calls.some(([name,text])=>name==='fillText'&&text==='Vista 3D esquemática · Z arriba'));viewer.destroy();
});

test('RHS respeta tf distinto de tw, consistente con el sólido STEP',()=>{
  const {viewer,project}=fixture();project.members[0].profile.tf=9;viewer.setData(project,'v1');
  const firstPlate=viewer.geometry(project.members[0]).slice(0,6).flat();
  near(Math.min(...firstPlate.map(p=>p[2])),41);near(Math.max(...firstPlate.map(p=>p[2])),50);viewer.destroy();
});

console.log(`\n${passed} grupos aprobados. Canvas y punteros simulados; NO equivale a QA táctil o visual real.`);
