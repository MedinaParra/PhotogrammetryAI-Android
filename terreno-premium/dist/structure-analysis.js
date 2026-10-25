/**
 * Experimental LOCAL geometry assistant, not semantic recognition or engineering.
 * Sobel edges + a restricted Hough transform look for a dominant, isolated,
 * nearly frontal frame/portal. A window, drawing or background frame can have
 * the same geometry: the user must verify every proposal and visible junction.
 * Coordinates are image fractions, NEVER dimensions, depth or a metric 3D model.
 */
const clamp = n => Math.max(0, Math.min(1, n));
const rad = n => n * Math.PI / 180;
const distance = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
const median = values => values.sort((a, b) => a - b)[Math.floor(values.length / 2)] ?? 255;
const LIMIT = 'Experimental: solo un marco o pórtico aislado, contrastante y casi frontal. No reconoce materiales, perfiles, conexiones físicas u ocultas, profundidad ni medidas. Puede omitir diagonales y otros elementos visibles; no reconstruye toda la estructura. Revise el trazado; puede corresponder a un objeto del fondo.';

export function analyzeStructurePixels({ data, width, height } = {}) {
  if (!Number.isInteger(width) || !Number.isInteger(height) || width < 32 || height < 32 || width > 512 || height > 512) throw new Error('La imagen debe medir entre 32 y 512 píxeles por lado.');
  if ((!ArrayBuffer.isView(data) && !Array.isArray(data)) || data.length !== width * height * 4) throw new Error('Se requieren píxeles RGBA completos.');
  // Bound runtime on mobile without changing the coordinate system returned.
  const scale = Math.min(1, 256 / Math.max(width, height));
  const w = Math.max(16, Math.round(width * scale)), h = Math.max(16, Math.round(height * scale));
  const size = w * h, minSide = Math.min(w, h), gray = new Float32Array(size), smooth = new Float32Array(size);
  for (let i = 0; i < data.length; i++) if (!Number.isFinite(data[i]) || data[i] < 0 || data[i] > 255) throw new Error('Los canales RGBA deben estar entre 0 y 255.');
  let low = 255, high = 0;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const sx = Math.min(width - 1, Math.floor((x + .5) / scale)), sy = Math.min(height - 1, Math.floor((y + .5) / scale)), i = (sy * width + sx) * 4;
    const alpha = data[i + 3] / 255;
    const value = alpha * (.2126 * data[i] + .7152 * data[i + 1] + .0722 * data[i + 2]) + (1 - alpha) * 255;
    gray[y * w + x] = value; low = Math.min(low, value); high = Math.max(high, value);
  }
  const metrics = { analysisWidth: w, analysisHeight: h, contrast: high - low, edgeDensity: 0, lineCount: 0, explainedEdgeRatio: 0, scoreMeaning: 'ajuste geométrico, no probabilidad', coordinateSpace: 'imagen normalizada 2D' };
  const stop = reason => ({ status: 'unidentifiable', candidates: [], nodes: [], edges: [], lines: [], reason: `${reason} ${LIMIT}`, metrics });
  if (high - low < 32) return stop('Contraste insuficiente.');
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let sum = 0;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) sum += gray[Math.max(0, Math.min(h - 1, y + dy)) * w + Math.max(0, Math.min(w - 1, x + dx))] * (dy === 0 ? 2 : 1) * (dx === 0 ? 2 : 1);
    smooth[y * w + x] = sum / 16;
  }
  const magnitudes = new Float32Array(size), gx = new Float32Array(size), gy = new Float32Array(size);
  let maxMagnitude = 0;
  for (let y = 1; y < h - 1; y++) for (let x = 1; x < w - 1; x++) {
    const i = y * w + x;
    gx[i] = (-smooth[i - w - 1] + smooth[i - w + 1] - 2 * smooth[i - 1] + 2 * smooth[i + 1] - smooth[i + w - 1] + smooth[i + w + 1]) / 4;
    gy[i] = (-smooth[i - w - 1] - 2 * smooth[i - w] - smooth[i - w + 1] + smooth[i + w - 1] + 2 * smooth[i + w] + smooth[i + w + 1]) / 4;
    magnitudes[i] = Math.hypot(gx[i], gy[i]); maxMagnitude = Math.max(maxMagnitude, magnitudes[i]);
  }
  const threshold = Math.max(16, maxMagnitude * .28), points = [];
  let strongPixels = 0;
  for (let y = 2; y < h - 2; y++) for (let x = 2; x < w - 2; x++) {
    const i = y * w + x, m = magnitudes[i];
    if (m < threshold) continue;
    strongPixels++;
    const dx = Math.abs(gx[i]) > Math.abs(gy[i]) * 2.414 ? 1 : Math.abs(gy[i]) > Math.abs(gx[i]) * 2.414 ? 0 : 1;
    const dy = dx === 0 ? 1 : Math.abs(gx[i]) > Math.abs(gy[i]) * 2.414 ? 0 : Math.sign(gx[i] * gy[i]);
    if (m < magnitudes[i + dy * w + dx] || m < magnitudes[i - dy * w - dx]) continue;
    points.push({ x, y, nx: gx[i] / m, ny: gy[i] / m });
  }
  metrics.edgeDensity = strongPixels / size;
  if (metrics.edgeDensity > .25) return stop('Demasiados bordes o textura: aísle una sola estructura sobre un fondo sencillo.');
  if (points.length < minSide * 1.2) return stop('No hay suficientes bordes definidos.');
  const diagonal = Math.ceil(Math.hypot(w, h)), bins = diagonal * 2 + 1, peaks = [];
  for (const family of ['v', 'h']) for (let degrees = family === 'v' ? -20 : 70; degrees <= (family === 'v' ? 20 : 110); degrees += 2) {
    const angle = rad(degrees), nx = Math.cos(angle), ny = Math.sin(angle), votes = new Uint16Array(bins);
    for (const p of points) if (Math.abs(p.nx * nx + p.ny * ny) > .9) votes[Math.round(p.x * nx + p.y * ny) + diagonal]++;
    for (let i = 1; i < bins - 1; i++) if (votes[i] >= Math.max(16, minSide * .15) && votes[i] >= votes[i - 1] && votes[i] >= votes[i + 1]) peaks.push({ family, angle, nx, ny, rho: i - diagonal, votes: votes[i] });
  }
  peaks.sort((a, b) => b.votes - a.votes);
  const segments = [], minLength = Math.max(24, minSide * .24), mergeDistance = Math.max(6, minSide * .035);
  for (const peak of peaks.slice(0, 120)) {
    const samples = [];
    for (const p of points) if (Math.abs(p.x * peak.nx + p.y * peak.ny - peak.rho) <= 1.7 && Math.abs(p.nx * peak.nx + p.ny * peak.ny) > .88) samples.push(-p.x * peak.ny + p.y * peak.nx);
    samples.sort((a, b) => a - b);
    let first = 0, best = null;
    for (let j = 1; j <= samples.length; j++) if (j === samples.length || samples[j] - samples[j - 1] > 4) {
      const length = samples[j - 1] - samples[first];
      if (length >= minLength && (!best || length > best.length)) best = { lo: samples[first], hi: samples[j - 1], length };
      first = j;
    }
    if (!best) continue;
    const occupied = new Set(samples.filter(t => t >= best.lo && t <= best.hi).map(t => Math.round(t))).size;
    const support = Math.min(1, occupied / (best.length + 1));
    if (support < .64) continue;
    const a = { x: peak.nx * peak.rho - peak.ny * best.lo, y: peak.ny * peak.rho + peak.nx * best.lo }, b = { x: peak.nx * peak.rho - peak.ny * best.hi, y: peak.ny * peak.rho + peak.nx * best.hi };
    const line = { ...peak, ...best, a, b, support };
    // Both Sobel edges of a narrow member count as ONE observed line, not two columns.
    const duplicate = segments.some(s => {
      if (s.family !== line.family || Math.abs(s.angle - line.angle) > rad(9)) return false;
      const middle = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
      if (Math.abs(middle.x * s.nx + middle.y * s.ny - s.rho) > mergeDistance) return false;
      const t1 = -a.x * s.ny + a.y * s.nx, t2 = -b.x * s.ny + b.y * s.nx;
      return Math.max(0, Math.min(Math.max(t1, t2), s.hi) - Math.max(Math.min(t1, t2), s.lo)) / Math.min(line.length, s.length) > .6;
    });
    if (!duplicate) segments.push(line);
  }
  metrics.lineCount = segments.length;
  if (segments.length > 10) return stop('Escena con demasiadas líneas: no se puede separar un marco único.');
  const verticals = segments.filter(s => s.family === 'v'), horizontals = segments.filter(s => s.family === 'h');
  if (verticals.length < 2 || !horizontals.length) return stop('Faltan dos laterales aproximadamente verticales y un travesaño superior continuo.');
  const intersect = (a, b) => {
    const det = a.nx * b.ny - a.ny * b.nx;
    return Math.abs(det) < .1 ? null : { x: (a.rho * b.ny - a.ny * b.rho) / det, y: (a.nx * b.rho - a.rho * b.nx) / det };
  };
  const endNear = (line, point) => Math.min(distance(line.a, point), distance(line.b, point));
  const otherEnd = (line, point) => distance(line.a, point) > distance(line.b, point) ? line.a : line.b;
  const tolerance = Math.max(8, minSide * .045), hypotheses = [];
  for (let i = 0; i < verticals.length; i++) for (let j = i + 1; j < verticals.length; j++) {
    let left = verticals[i], right = verticals[j];
    if ((left.a.x + left.b.x) > (right.a.x + right.b.x)) [left, right] = [right, left];
    if (Math.abs(left.angle - right.angle) > rad(8)) continue;
    for (const top of horizontals) {
      const perpendicularError = Math.abs(top.angle - (left.angle + right.angle) / 2 - Math.PI / 2);
      if (perpendicularError > rad(12)) continue;
      const tl = intersect(left, top), tr = intersect(right, top);
      if (!tl || !tr || endNear(left, tl) > tolerance || endNear(right, tr) > tolerance || endNear(top, tl) > tolerance || endNear(top, tr) > tolerance) continue;
      if (distance(tl, tr) < w * .22) continue;
      let bl = otherEnd(left, tl), br = otherEnd(right, tr);
      if (bl.y - tl.y < h * .22 || br.y - tr.y < h * .22) continue;
      if (Math.abs(distance(tl, bl) - distance(tr, br)) > Math.max(tolerance * 1.5, Math.max(left.length, right.length) * .12)) continue;
      let bottom = null;
      for (const possible of horizontals) {
        if (possible === top || Math.abs(possible.angle - top.angle) > rad(8)) continue;
        const a = intersect(left, possible), b = intersect(right, possible);
        if (a && b && distance(a, bl) <= tolerance && distance(b, br) <= tolerance && endNear(possible, a) <= tolerance && endNear(possible, b) <= tolerance) { bottom = possible; bl = a; br = b; break; }
      }
      const nodes = [tl, tr, br, bl], selected = [left, right, top, ...(bottom ? [bottom] : [])];
      if (nodes.some(p => p.x < w * .025 || p.x > w * .975 || p.y < h * .025 || p.y > h * .975)) continue;
      const span = distance(tl, tr), rise = (distance(tl, bl) + distance(tr, br)) / 2;
      if (span / rise < .25 || span / rise > 4) continue;
      const fit = clamp(selected.reduce((sum, s) => sum + s.support, 0) / selected.length * .75 + .25 * (1 - perpendicularError / rad(15)));
      if (fit >= .74) hypotheses.push({ nodes, selected, bottom, fit, type: bottom ? 'bastidor' : 'portico' });
    }
  }
  hypotheses.sort((a, b) => b.fit - a.fit);
  if (!hypotheses.length) return stop('Los segmentos no forman un marco o pórtico frontal inequívoco; pruebe otra toma.');
  const best = hypotheses[0];
  if (hypotheses.some(other => other !== best && other.fit > best.fit - .12 && other.nodes.some((n, i) => distance(n, best.nodes[i]) > tolerance * 2))) return stop('Hay varias formas compatibles: la propuesta sería ambigua.');
  let explained = 0;
  for (const p of points) if (best.selected.some(s => {
    const t = -p.x * s.ny + p.y * s.nx;
    return Math.abs(p.x * s.nx + p.y * s.ny - s.rho) <= mergeDistance && t >= s.lo - tolerance && t <= s.hi + tolerance;
  })) explained++;
  metrics.explainedEdgeRatio = explained / points.length;
  if (metrics.explainedEdgeRatio < .72) return stop('Hay demasiados bordes ajenos al trazado; fondo complejo o estructura no compatible.');
  // Reject a large filled blob: a visible frame should leave a background-like
  // opening. This intentionally abstains on strongly changing backgrounds.
  const outer = [], inner = [];
  for (let y = 0; y < h; y += 3) for (let x = 0; x < w; x += 3) if (x < w * .08 || x > w * .92 || y < h * .08 || y > h * .92) outer.push(gray[y * w + x]);
  const [tl, tr, br, bl] = best.nodes;
  for (let a = .25; a <= .75; a += .05) for (let b = .25; b <= .75; b += .05) {
    const x = (1 - b) * ((1 - a) * tl.x + a * tr.x) + b * ((1 - a) * bl.x + a * br.x);
    const y = (1 - b) * ((1 - a) * tl.y + a * tr.y) + b * ((1 - a) * bl.y + a * br.y);
    inner.push(gray[Math.round(y) * w + Math.round(x)]);
  }
  metrics.backgroundDifference = Math.abs(median(inner) - median(outer));
  if (metrics.backgroundDifference > Math.max(24, (high - low) * .24)) return stop('El interior no se distingue como una abertura sobre fondo sencillo; puede ser una superficie llena o un fondo diferente.');
  const nodes = best.nodes.map((p, i) => ({ id: `image-n${i + 1}`, x: clamp(p.x / (w - 1)), y: clamp(p.y / (h - 1)) }));
  const links = [[0, 1], [1, 2], [3, 0], ...(best.bottom ? [[2, 3]] : [])];
  const score = Number((best.fit * metrics.explainedEdgeRatio).toFixed(3));
  const reason = best.bottom ? 'Cuatro trazos dominantes delimitan un marco visible en 2D. Confirme que pertenecen a la estructura y revise sus uniones.' : 'Dos trazos laterales y un travesaño superior delimitan un pórtico visible en 2D. No se detectó una base continua; no significa que no exista.';
  return {
    status: 'proposal',
    candidates: [{ type: best.type, label: best.type === 'bastidor' ? 'Bastidor: marco frontal visible' : 'Pórtico: dos laterales y travesaño', score, reason }],
    nodes,
    edges: links.map(([a, b]) => ({ a: nodes[a].id, b: nodes[b].id, confidence: score })),
    lines: best.selected.map(s => ({ x1: clamp(s.a.x / (w - 1)), y1: clamp(s.a.y / (h - 1)), x2: clamp(s.b.x / (w - 1)), y2: clamp(s.b.y / (h - 1)), strength: Number(s.support.toFixed(3)) })),
    reason: `${reason} ${LIMIT} La puntuación mide ajuste geométrico, no una probabilidad. Ancho, alto, fondo y perfil deben medirse/confirmarse aparte.`,
    metrics,
  };
}
