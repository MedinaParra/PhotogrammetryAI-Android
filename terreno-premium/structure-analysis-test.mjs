import assert from 'node:assert/strict';
import { analyzeStructurePixels } from './dist/structure-analysis.js';

let passed = 0;
const test = (name, fn) => { fn(); passed++; console.log(`✓ ${name}`); };
// Synthetic raster fixtures, NOT field photographs or a recognition benchmark.
function fixture({ width = 240, height = 220, background = 242, foreground = 30 } = {}) {
  const data = new Uint8ClampedArray(width * height * 4);
  for (let i = 0; i < data.length; i += 4) { data[i] = data[i + 1] = data[i + 2] = background; data[i + 3] = 255; }
  const pixel = (x, y, value = foreground) => {
    if (x < 0 || y < 0 || x >= width || y >= height) return;
    const i = (Math.round(y) * width + Math.round(x)) * 4;
    data[i] = data[i + 1] = data[i + 2] = value;
  };
  const line = (x1, y1, x2, y2, thickness = 5, value = foreground) => {
    const dx = x2 - x1, dy = y2 - y1, length2 = dx * dx + dy * dy;
    for (let y = Math.max(0, Math.floor(Math.min(y1, y2) - thickness)); y <= Math.min(height - 1, Math.ceil(Math.max(y1, y2) + thickness)); y++) {
      for (let x = Math.max(0, Math.floor(Math.min(x1, x2) - thickness)); x <= Math.min(width - 1, Math.ceil(Math.max(x1, x2) + thickness)); x++) {
        const t = Math.max(0, Math.min(1, ((x - x1) * dx + (y - y1) * dy) / length2));
        if (Math.hypot(x - x1 - t * dx, y - y1 - t * dy) <= thickness / 2) pixel(x, y, value);
      }
    }
  };
  return { width, height, data, line, pixel };
}
function frame(options = {}) {
  const f = fixture(options), angle = (options.angle || 0) * Math.PI / 180;
  const points = [[-.31, -.34], [.31, -.34], [.31, .34], [-.31, .34]].map(([x, y]) => ({ x: f.width / 2 + x * f.width * Math.cos(angle) - y * f.height * Math.sin(angle), y: f.height / 2 + x * f.width * Math.sin(angle) + y * f.height * Math.cos(angle) }));
  const pairs = [[0, 1], [1, 2], [3, 0], ...(options.portal ? [] : [[2, 3]])];
  for (const [i, j] of pairs) f.line(points[i].x, points[i].y, points[j].x, points[j].y, options.thickness || 5);
  return { ...f, points };
}
function graph(result, type, edgeCount) {
  assert.equal(result.status, 'proposal', result.reason);
  assert.equal(result.candidates[0].type, type);
  assert.equal(result.nodes.length, 4);
  assert.equal(result.edges.length, edgeCount);
  assert.equal(result.lines.length, edgeCount);
  const ids = new Set(result.nodes.map(n => n.id));
  for (const n of result.nodes) for (const axis of ['x', 'y']) assert.ok(Number.isFinite(n[axis]) && n[axis] >= 0 && n[axis] <= 1);
  for (const e of result.edges) { assert.ok(ids.has(e.a) && ids.has(e.b) && e.a !== e.b); assert.ok(e.confidence >= 0 && e.confidence <= 1); }
  for (const l of result.lines) for (const k of ['x1', 'y1', 'x2', 'y2', 'strength']) assert.ok(l[k] >= 0 && l[k] <= 1);
  assert.match(result.reason, /no una probabilidad/);
  assert.match(result.reason, /deben medirse/);
  assert.match(result.reason, /fondo/);
  return result;
}
const reject = f => {
  const r = analyzeStructurePixels(f);
  assert.equal(r.status, 'unidentifiable', JSON.stringify(r));
  assert.deepEqual(r.candidates, []); assert.deepEqual(r.nodes, []); assert.deepEqual(r.edges, []);
  return r;
};

test('Marco rectangular claro: cuatro líneas y grafo2D observado', () => graph(analyzeStructurePixels(frame()), 'bastidor', 4));
test('Pórtico claro: sin inventar travesaño inferior', () => graph(analyzeStructurePixels(frame({ portal: true })), 'portico', 3));
test('Marco girado +8° y -10°', () => { for (const angle of [8, -10]) graph(analyzeStructurePixels(frame({ angle })), 'bastidor', 4); });
test('Pórtico girado +6° y -8°', () => { for (const angle of [6, -8]) graph(analyzeStructurePixels(frame({ angle, portal: true })), 'portico', 3); });
test('Contraste invertido y moderado', () => { for (const opts of [{ foreground: 240, background: 15 }, { foreground: 95, background: 170 }]) graph(analyzeStructurePixels(frame(opts)), 'bastidor', 4); });
test('Coordenadas siguen la evidencia: no rectángulo fijo', () => {
  const f = frame({ angle: 8 }), r = graph(analyzeStructurePixels(f), 'bastidor', 4);
  for (let i = 0; i < 4; i++) assert.ok(Math.hypot(r.nodes[i].x * (f.width - 1) - f.points[i].x, r.nodes[i].y * (f.height - 1) - f.points[i].y) < 9);
  assert.ok(Math.abs(r.nodes[0].y - r.nodes[1].y) > .04);
});
test('Resolución máxima512: salida normalizada y cálculo acotado', () => {
  const r = graph(analyzeStructurePixels(frame({ width: 512, height: 480, thickness: 10 })), 'bastidor', 4);
  assert.equal(r.metrics.analysisWidth, 256);
});
test('Sin bordes y contraste insuficiente: abstención', () => { reject(fixture()); reject(frame({ foreground: 230, background: 242 })); });
test('Ruido pseudoaleatorio: abstención, no propuesta', () => {
  const f = fixture(); let seed = 4567;
  for (let y = 0; y < f.height; y++) for (let x = 0; x < f.width; x++) { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; f.pixel(x, y, seed >>> 24); }
  reject(f);
});
test('Blob circular y rectángulo relleno: abstención', () => {
  const circle = fixture();
  for (let y = 0; y < circle.height; y++) for (let x = 0; x < circle.width; x++) if (Math.hypot(x - 120, y - 110) < 70) circle.pixel(x, y);
  reject(circle);
  const filled = fixture();
  for (let y = 35; y < 185; y++) for (let x = 45; x < 195; x++) filled.pixel(x, y);
  reject(filled);
});
test('Una diagonal o dos columnas sin unión no proponen un marco', () => {
  const diagonal = fixture(); diagonal.line(35, 35, 200, 180); reject(diagonal);
  const columns = fixture(); columns.line(45, 35, 45, 185); columns.line(195, 35, 195, 185); reject(columns);
});
test('Escena densa/rejilla y marco con fondo texturado: abstención', () => {
  const grid = fixture();
  for (let x = 20; x < 230; x += 25) grid.line(x, 15, x, 205, 3);
  for (let y = 20; y < 210; y += 25) grid.line(15, y, 225, y, 3);
  reject(grid);
  const complex = frame();
  for (let i = 0; i < 10; i++) complex.line(65 + i * 8, 65, 65 + i * 8, 155, 2);
  reject(complex);
});
test('Dos estructuras separadas: abstención ambigua', () => {
  const f = fixture({ width: 320, height: 200 });
  for (const x of [30, 180]) { f.line(x, 35, x + 105, 35); f.line(x, 35, x, 165); f.line(x + 105, 35, x + 105, 165); f.line(x, 165, x + 105, 165); }
  reject(f);
});
test('Toma recortada sin extremos: abstención', () => {
  const f = fixture(); f.line(0, 35, 195, 35); f.line(0, 35, 0, 185); f.line(195, 35, 195, 185); f.line(0, 185, 195, 185); reject(f);
});
test('Giro fuera del alcance casi frontal y V invertida: abstención', () => {
  reject(frame({ angle: 40 }));
  const f = fixture(); f.line(45, 185, 120, 35); f.line(120, 35, 195, 185); reject(f);
});
test('Travesaño interrumpido y base sin coronación: no inventa conexión superior', () => {
  const gap = fixture(); gap.line(45, 35, 100, 35); gap.line(140, 35, 195, 35); gap.line(45, 35, 45, 185); gap.line(195, 35, 195, 185); reject(gap);
  const inverted = fixture(); inverted.line(45, 35, 45, 185); inverted.line(195, 35, 195, 185); inverted.line(45, 185, 195, 185); reject(inverted);
});
test('RGBA transparente no se usa como geometría visible', () => {
  const f = frame(); for (let i = 3; i < f.data.length; i += 4) f.data[i] = 0; reject(f);
});
test('Entrada inválida falla explícitamente y no muta píxeles válidos', () => {
  for (const input of [undefined, {}, { width: 0, height: 200, data: [] }, { width: 513, height: 200, data: [] }, { width: 100.5, height: 100, data: [] }, { width: 100, height: 100, data: [] }]) assert.throws(() => analyzeStructurePixels(input));
  const invalid = fixture(); invalid.data = [...invalid.data]; invalid.data[4] = NaN; assert.throws(() => analyzeStructurePixels(invalid));
  const f = frame(), before = f.data.slice(); analyzeStructurePixels(f); assert.deepEqual(f.data, before);
});
test('Resultado no contiene dimensiones reales ni conexiones físicas certificadas', () => {
  const r = graph(analyzeStructurePixels(frame()), 'bastidor', 4);
  assert.equal(r.depth, undefined); assert.equal(r.profile, undefined); assert.equal(r.dimensions, undefined);
  assert.match(r.reason, /No reconoce materiales, perfiles, conexiones físicas u ocultas, profundidad ni medidas/);
  assert.match(r.metrics.scoreMeaning, /no probabilidad/);
  assert.doesNotThrow(() => JSON.parse(JSON.stringify(r)));
});
console.log(`\n${passed} grupos de análisis geométrico correctos. Solo fixtures sintéticos; no valida fotos reales ni reconocimiento semántico.`);
