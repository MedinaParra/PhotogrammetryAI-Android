import assert from 'node:assert/strict';
import {createTemplateProject,createSupportTemplate} from './dist/templates.js';
import {validateProject, lengthOf, serializeProject, parseProject, LIMITS} from './dist/core.js';

const profile = {type:'rhs',height:80,width:60,tw:3,tf:3};
const base = {width:2000,depth:1200,height:1800,profile};
const make = (type, extra={}) => createTemplateProject({...base,type,...(type==='portico'?{depth:0}:{}),...extra});
let passed=0;
function test(name, fn) { fn(); passed++; console.log(`✓ ${name}`); }
const close = (actual,expected) => assert(Math.abs(actual-expected) < 1e-8, `${actual} !== ${expected}`);

for (const [type,nodes,members,braced] of [['bastidor',8,12,16],['portico',4,3,4],['atril',6,9,11]]) {
  test(`${type}: topología válida, nodos compartidos y diagonales opcionales`,()=>{
    for (const bracing of [false,true]) {
      const p=make(type,{bracing}); assert.equal(validateProject(p),p);
      assert.equal(p.nodes.length,nodes); assert.equal(p.members.length,bracing?braced:members);
      const links=new Map(p.nodes.map(n=>[n.id,new Set()]));
      const edges=new Set();
      for (const m of p.members) {
        assert(lengthOf(p,m)>0); links.get(m.startNode).add(m.endNode); links.get(m.endNode).add(m.startNode);
        const edge=[m.startNode,m.endNode].sort().join('|'); assert(!edges.has(edge),'No duplicate edge'); edges.add(edge);
      }
      const seen=new Set(), queue=[p.nodes[0].id];
      while(queue.length){const id=queue.pop();if(seen.has(id))continue;seen.add(id);queue.push(...links.get(id));}
      assert.equal(seen.size,nodes,'All nodes belong to the connected geometric graph');
      assert([...links.values()].some(neighbours=>neighbours.size>=2),'Shared nodes connect members');
    }
  });
}
test('Bastidor: dimensiones X/Y/Z exactas y largos de cada familia',()=>{
  const p=make('bastidor');
  assert.deepEqual(['x','y','z'].map(axis=>Math.max(...p.nodes.map(n=>n[axis]))),[2000,1200,1800]);
  assert.deepEqual(p.members.slice(0,4).map(m=>lengthOf(p,m)),[1800,1800,1800,1800]);
  assert.deepEqual(p.members.slice(4,8).map(m=>lengthOf(p,m)),[2000,2000,1200,1200]);
});
test('Bastidor: diagonales mantienen ancho/fondo y altura',()=>{
  const p=make('bastidor',{bracing:true});
  p.members.slice(12,14).forEach(m=>close(lengthOf(p,m),Math.hypot(2000,1800)));
  p.members.slice(14).forEach(m=>close(lengthOf(p,m),Math.hypot(1200,1800)));
});
test('Pórtico: plano Y=0, dos columnas y travesaño',()=>{
  const p=make('portico',{bracing:true}); assert(p.nodes.every(n=>n.y===0));
  assert.deepEqual(p.members.slice(0,3).map(m=>lengthOf(p,m)),[1800,1800,2000]);
  close(lengthOf(p,p.members[3]),Math.hypot(2000,1800));
});
test('Atril: cuatro patas inclinadas y coronación centrada',()=>{
  const p=make('atril',{bracing:true});
  const top=p.nodes.filter(n=>n.z===1800);assert.equal(top.length,2);assert(top.every(n=>n.y===600));
  p.members.slice(0,4).forEach(m=>close(lengthOf(p,m),Math.hypot(600,1800)));
  assert.equal(lengthOf(p,p.members[6]),2000);
  p.members.slice(9).forEach(m=>close(lengthOf(p,m),Math.hypot(2000,600,1800)));
});
test('Plantilla no atribuye medidas ni reconocimiento automático',()=>{
  for(const type of ['bastidor','portico','atril']) {
    const p=make(type);assert.equal(p.example,false);assert.equal(p.unit,'mm');
    assert.equal(p.template.origin,'manual-template');assert.equal(p.template.dimensionsReference,'axis-to-axis');
    assert(p.members.every(m=>m.status==='estimated'&&m.lengthMeasured===null&&m.photos.length===0));
    assert(p.members.every(m=>m.note.includes('NO derivada de una foto')&&m.note.includes('pendientes de verificar')));
  }
});
test('Cada pieza recibe copia independiente del perfil, sin mutar entrada',()=>{
  const original=structuredClone(base),p=make('bastidor');
  assert.deepEqual(base,original);assert.deepEqual(p.members[0].profile,profile);
  p.members[0].profile.width=70;assert.equal(p.members[1].profile.width,60);assert.equal(profile.width,60);
});
test('JSON conserva la plantilla, geometría y trazabilidad exactas',()=>{
  for(const type of ['bastidor','portico','atril']) {const p=make(type,{bracing:true});assert.deepEqual(parseProject(serializeProject(p)),p);}
});
test('Milímetros fraccionarios no se escalan ni redondean',()=>{
  const p=make('bastidor',{width:1200.25,depth:600.125,height:900.5});
  assert.equal(p.nodes.find(n=>n.id==='tpl-h').x,1200.25);
  assert.equal(p.nodes.find(n=>n.id==='tpl-h').y,600.125);
  assert.equal(lengthOf(p,p.members[0]),900.5);
});
test('Todas las dimensiones aplicables requieren números positivos finitos',()=>{
  for(const type of ['bastidor','portico','atril'])
    for(const field of type==='portico'?['width','height']:['width','depth','height'])
      for(const value of [undefined,null,0,-1,NaN,Infinity,'1000',LIMITS.coordinate+1,0.0001])
        assert.throws(()=>make(type,{[field]:value}),/dimensión positiva/);
});
test('Pórtico no acepta profundidad accidental o ambigua',()=>{
  assert.equal(make('portico',{depth:undefined}).template.dimensionsMm.depth,0);
  for(const depth of [null,-1,1,NaN,Infinity,'0'])assert.throws(()=>make('portico',{depth}),/pórtico es plano/);
});
test('Tipo, nombre, opciones y diagonales inválidos son rechazados',()=>{
  for(const type of ['','__proto__','toString','unknown',undefined])assert.throws(()=>make(type),/Plantilla no válida/);
  for(const name of ['',null,' '.repeat(5),'x'.repeat(181)])assert.throws(()=>make('atril',{name}));
  for(const bracing of ['true',0,1,null,{}])assert.throws(()=>make('atril',{bracing}),/diagonales/);
  for(const options of [null,[],false])assert.throws(()=>createTemplateProject(options));
});
test('Perfil explícito inválido se rechaza; circular válido se conserva',()=>{
  for(const bad of [undefined,null,{...profile,tw:0},{...profile,height:0},{...profile,width:3},{...profile,type:'unknown'}])assert.throws(()=>make('atril',{profile:bad}));
  const circular={type:'round',height:100,width:100,tw:4,tf:0};
  assert.deepEqual(make('portico',{profile:circular}).members[0].profile,circular);
});
test('Dos creaciones producen proyectos diferentes y datos independientes',()=>{
  const a=make('atril'), b=make('atril'); assert.notEqual(a.id,b.id);a.nodes[0].x=250;assert.equal(b.nodes[0].x,0);
});

test('Familia de atril: ejemplo sintético con parámetros y componentes preliminares trazables',()=>{
  const p=createSupportTemplate();assert.equal(p.example,true);assert.equal(p.template.origin,'family-seed');assert.equal(p.members.length,6);assert.equal(p.features.length,2);assert(p.members.every(m=>m.status==='estimated'&&m.lengthMeasured===null));assert(p.features.every(f=>f.exportable===false));assert.deepEqual(parseProject(serializeProject(p)).features,p.features);
});
test('Familia sin cunas ni tomauñas no conserva accesorios invisibles',()=>{
  const p=createSupportTemplate({saddles:false,forks:false});assert.equal(p.members.length,4);assert.equal(p.features.length,0);
});
test('Familia rechaza dimensiones imposibles y componentes adulterados',()=>{
  for(const opts of [{length:100},{width:0},{radius:100},{saddles:'yes'}])assert.throws(()=>createSupportTemplate(opts));
  for(const field of ['width','radius','length','thickness','x']){const p=createSupportTemplate();p.features[0][field]=NaN;assert.throws(()=>validateProject(p));}
  const p=createSupportTemplate();p.features[0].exportable=true;assert.throws(()=>validateProject(p));
});
console.log(`\n${passed} grupos de plantillas correctos. Geometría sintética; no certifica una estructura real.`);
