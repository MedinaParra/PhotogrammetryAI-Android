/** Synthetic RGBA tests only. No claim of accuracy on real field photographs. */
import assert from 'node:assert/strict';
import {analyzeSectionPixels} from './dist/photo-analysis.js';

const size=128;
function image(predicate,{angle=0,foreground=[32,38,43],background=[244,244,240]}={}) {
  const data=new Uint8ClampedArray(size*size*4),c=Math.cos(angle),s=Math.sin(angle);
  for(let y=0;y<size;y++)for(let x=0;x<size;x++) {
    const dx=(x+.5-size/2)/size,dy=(y+.5-size/2)/size;
    const inside=predicate(dx*c+dy*s,-dx*s+dy*c),color=inside?foreground:background,i=(y*size+x)*4;
    data.set([...color,255],i);
  }
  return {data,width:size,height:size};
}
const box=(x,y)=>Math.abs(x)<.28&&Math.abs(y)<.22;
const tube=(x,y)=>box(x,y)&&!(Math.abs(x)<.20&&Math.abs(y)<.14);
const solidCircle=(x,y)=>x*x+y*y<.26*.26;
const circleTube=(x,y)=>solidCircle(x,y)&&x*x+y*y>.19*.19;
const iShape=(x,y)=>Math.abs(x)<.27&&Math.abs(y)<.30&&(Math.abs(y)>.22||Math.abs(x)<.045);
let count=0;
function test(name,fn){fn();count++;console.log('✓ '+name);}
function identifies(sample,type) {
  const result=analyzeSectionPixels(sample);
  assert.equal(result.status,'candidates',JSON.stringify(result));
  assert.equal(result.candidates[0].type,type);assert(result.candidates[0].score>=.88);
  assert.equal(result.metrics.physicalDimensionsEstimated,false);
  assert.match(result.metrics.scoreMeaning,/no probabilidad/);return result;
}
function rejects(sample) {
  const result=analyzeSectionPixels(sample);
  assert.equal(result.status,'unidentifiable',JSON.stringify(result));assert.deepEqual(result.candidates,[]);return result;
}
test('Rectángulo sintético: candidato y advertencia de macizo/hueco',()=>{
  const r=identifies(image(box),'rect');assert.match(r.candidates[0].reason,/No demuestra que sea macizo/);
});
test('Tubo rectangular sintético: hueco real en píxeles',()=>{
  const r=identifies(image(tube),'rhs');assert.equal(r.metrics.visibleHoles,1);
});
test('Perfil I/H sintético: candidato basado en silueta',()=>identifies(image(iShape),'i'));
test('Círculo sintético sin hueco: no inventa macizo/tubo',()=>{
  const r=identifies(image(solidCircle),'round');assert.match(r.candidates[0].reason,/No permite distinguir/);
});
test('Tubo circular sintético: identifica contorno y hueco',()=>identifies(image(circleTube),'round'));
test('Rotación moderada de sección rectangular',()=>identifies(image(tube,{angle:.31}),'rhs'));
test('Rotación de perfil I/H',()=>identifies(image(iShape,{angle:.47}),'i'));
test('Metal claro sobre fondo oscuro también se segmenta',()=>identifies(image(box,{foreground:[224,220,214],background:[25,29,33]}),'rect'));
test('Imagen uniforme sin pieza se rechaza',()=>rejects(image(()=>false)));
test('Contraste insuficiente se rechaza',()=>rejects(image(box,{foreground:[230,231,232],background:[244,244,240]})));
test('Pieza recortada por el borde se rechaza',()=>rejects(image((x,y)=>x<.2&&Math.abs(y)<.25)));
test('Dos piezas separadas se rechazan',()=>rejects(image((x,y)=>(Math.abs(x-.2)<.08||Math.abs(x+.2)<.08)&&Math.abs(y)<.15)));
test('Ruido aleatorio reproducible no produce candidato',()=>{
  let seed=12345;
  const sample=image(()=>false);
  for(let y=10;y<118;y++)for(let x=10;x<118;x++) {
    seed=(Math.imul(seed,1664525)+1013904223)>>>0;
    const v=seed>>>24,i=(y*size+x)*4;sample.data[i]=v;sample.data[i+1]=v;sample.data[i+2]=v;
  }
  rejects(sample);
});
test('Escena sintética de estructura completa con varios huecos se rechaza',()=>{
  const frame=(x,y)=>Math.abs(x)<.32&&Math.abs(y)<.30&&(Math.abs(x)>.285||Math.abs(y)>.265||Math.abs(y)<.018||Math.abs(x)<.018);
  const r=rejects(image(frame));assert(r.metrics.visibleHoles>=2);
});
test('Forma ambigua cuadrada-redondeada no se etiqueta por defecto',()=>{
  rejects(image((x,y)=>Math.abs(x)**4+Math.abs(y)**4<.27**4));
});
test('Perspectiva/forma trapezoidal pronunciada se rechaza',()=>rejects(image((x,y)=>Math.abs(y)<.29&&Math.abs(x)<(.16+.38*(y+.29)))));
test('Hueco excéntrico se rechaza',()=>rejects(image((x,y)=>box(x,y)&&!(x>-.05&&x<.23&&Math.abs(y)<.12))));
test('Fondo texturado en borde se rechaza',()=>{
  const sample=image(box);
  for(let y=0;y<size;y++)for(let x=0;x<size;x++)if(x<6||y<6||x>121||y>121) {
    const v=(x+y)%2?40:240,i=(y*size+x)*4;sample.data.set([v,v,v,255],i);
  }
  rejects(sample);
});
test('Entrada inválida, demasiado grande o array inconsistente se rechaza',()=>{
  for(const sample of [undefined,{}, {data:new Uint8ClampedArray(4),width:128,height:128}, {data:new Uint8ClampedArray(513*32*4),width:513,height:32}])
    assert.throws(()=>analyzeSectionPixels(sample),/RGBA/);
});
test('Determinismo y aislamiento: no modifica píxeles originales',()=>{
  const sample=image(tube),before=sample.data.slice();assert.deepEqual(analyzeSectionPixels(sample),analyzeSectionPixels(sample));assert.deepEqual(sample.data,before);
});
console.log(`\n${count} grupos sintéticos de análisis de imagen correctos. NO validan fotos reales ni reconocimiento general.`);
