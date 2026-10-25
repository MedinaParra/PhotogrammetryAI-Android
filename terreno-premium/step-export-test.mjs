import {createSupportTemplate} from './dist/templates.js';
/** Backend only. No UI, Android, browser, network or manufacturing claim.
 * node step-export-test.mjs [--fixtures /absolute/output/directory]
 * Optional fixtures are synthetic input to the independent CAD-kernel checks.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createProject,exampleProject} from './dist/core.js';
import {serializeStep,STEP_LIMITS} from './dist/step-export.js';

let passed=0;
const check=(name,fn)=>{fn();passed++;console.log(`PASS ${name}`);};
const copy=x=>structuredClone(x);
const profiles={
  rect:{type:'rect',width:60,height:100,tw:0,tf:0},
  rhs:{type:'rhs',width:60,height:100,tw:4,tf:7},
  i:{type:'i',width:80,height:160,tw:6,tf:10},
  channel:{type:'channel',width:80,height:160,tw:6,tf:10},
};
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const normalize=a=>a.map(n=>n/Math.hypot(...a));
function fixture(type='rect',a=[0,0,0],b=[0,0,1000],roll=0,profile=profiles[type]){
  const p=createProject(`Synthetic ${type} fixture`,'Synthetic CAD verification only');
  p.id=`fixture-${type}`;p.createdAt=p.updatedAt='2026-10-24T22:00:00.000Z';
  p.nodes=[a,b].map(([x,y,z],i)=>({id:`n${i}`,label:`N${i}`,x,y,z}));
  p.members=[{id:'m0',label:`Pieza ${type}`,startNode:'n0',endNode:'n1',profile:{...profile},roll,status:'estimated',note:'Synthetic test',lengthMeasured:null,photos:[]}];
  return p;
}
function expected(p){
  const nodes=new Map(p.nodes.map(n=>[n.id,n]));
  return {
    name:p.name.replace(/[\u0000-\u001f\u007f]/g,' '),
    members:p.members.map(m=>{
      const a=nodes.get(m.startNode),b=nodes.get(m.endNode),{type,width:w,height:h,tw,tf}=m.profile;
      const start=[a.x,a.y,a.z],end=[b.x,b.y,b.z],delta=end.map((n,i)=>n-start[i]),len=Math.hypot(...delta),t=normalize(delta);
      const s0=normalize(cross(Math.abs(t[2])>.97?[0,1,0]:[0,0,1],t)),s1=normalize(cross(t,s0));
      const angle=m.roll*Math.PI/180,c=Math.cos(angle),s=Math.sin(angle);
      const u=s0.map((n,i)=>n*c+s1[i]*s),v=s1.map((n,i)=>n*c-s0[i]*s);
      const area=type==='rect'?w*h:type==='rhs'?w*h-(w-2*tw)*(h-2*tf):2*w*tf+tw*(h-2*tf);
      const offset=type==='channel'?(-w/2+tw/2)*tw*(h-2*tf)/area:0;
      const center=start.map((n,i)=>(n+end[i])/2);
      const ext=u.map((n,i)=>Math.abs(n)*w/2+Math.abs(v[i])*h/2);
      const bounds=[...start.map((n,i)=>Math.min(n,end[i])-ext[i]),...start.map((n,i)=>Math.max(n,end[i])+ext[i])];
      const world=([x,y])=>center.map((n,i)=>n+u[i]*x+v[i]*y);
      const inside=type==='rect'?center:type==='rhs'?world([-w/2+tw/2,0]):world([0,h/2-tf/2]);
      const outside=type==='rect'?world([w,0]):type==='rhs'?center:world([w/2-.25*(w-tw),0]);
      return {id:m.id,label:m.label.replace(/[\u0000-\u001f\u007f]/g,' '),type,volume:area*len,bounds,centroid:center.map((n,i)=>n+u[i]*offset),faces:{rect:6,rhs:10,i:14,channel:10}[type],inside,outside};
    }),
  };
}
const cases=[];
const addCase=(name,p)=>{cases.push({name,project:p,expected:expected(p)});return p;};
for(const type of Object.keys(profiles)){
  check(`${type}: exact planar solid with named assembly part`,()=>{
    const p=addCase(type,fixture(type));const before=copy(p),step=serializeStep(p);
    assert.match(step,/^ISO-10303-21;/);assert.match(step,/FILE_SCHEMA\(\('AUTOMOTIVE_DESIGN'\)\)/);
    assert.equal((step.match(/=FACETED_BREP\(/g)||[]).length,1);
    assert.equal((step.match(/=FACE_SURFACE\(/g)||[]).length,expected(p).members[0].faces);
    assert.match(step,/NEXT_ASSEMBLY_USAGE_OCCURRENCE/);assert.match(step,/SI_UNIT\(\.MILLI\.,\.METRE\.\)/);
    assert.doesNotMatch(step,/TRIANGULATED|TESSELLATED|FACET_NORMAL|STL/);
    assert.deepEqual(p,before);assert.equal(serializeStep(p),step);
  });
  check(`${type}: reversed/oblique endpoints and roll`,()=>{
    addCase(`${type}-oblique`,fixture(type,[1500,-250,800],[-600,1000,225],137.2));
    addCase(`${type}-reversed`,fixture(type,[0,0,1000],[0,0,0],-36000));
  });
}
check('RHS has real inner loops rather than four overlapping bars',()=>{
  const s=serializeStep(fixture('rhs'));
  assert.equal((s.match(/=FACE_BOUND\(/g)||[]).length,2);
  assert.equal((s.match(/=CLOSED_SHELL\(/g)||[]).length,1);
});
check('Unicode, apostrophe, backslash and controls are escaped',()=>{
  const p=fixture();p.name="Bastidor Ñ 中文 😀 ' \\";p.members[0].label="Cañón 中文 😀 ' \\ línea\n2";
  const s=serializeStep(addCase('unicode',p));assert.match(s,/\\X2\\/);assert.doesNotMatch(s,/línea\n2/);
});
check('UI units do not rescale stored millimetres',()=>{
  const p=fixture('rhs');const s=serializeStep(p);
  for(const unit of ['mm','cm','m']){p.unit=unit;assert.equal(serializeStep(p),s);addCase(`unit-${unit}`,copy(p));}
});
check('multiple pieces retain duplicate display labels and overlaps',()=>{
  const p=exampleProject();p.id='multi-example';p.createdAt=p.updatedAt='2026-10-24T22:00:00.000Z';
  p.members.forEach((m,i)=>{m.profile={...profiles[Object.keys(profiles)[i%4]]};m.label=i<2?'Duplicate label':`Member ${i}`;m.roll=i*13;});
  addCase('multiple',p);const s=serializeStep(p);
  assert.equal((s.match(/=FACETED_BREP\(/g)||[]).length,p.members.length);
  assert.equal((s.match(/=NEXT_ASSEMBLY_USAGE_OCCURRENCE\(/g)||[]).length,p.members.length);
});
check('manufacturing details are not invented; photos/private notes excluded',()=>{
  const p=fixture();p.members[0].photos=[{id:'photo1',name:'private-name',createdAt:p.createdAt,dataUrl:'data:image/png;base64,AAAA'}];p.members[0].note='PRIVATE-NOTE';
  const s=serializeStep(p);assert.doesNotMatch(s,/PRIVATE-NOTE|private-name|AAAA/);assert.match(s,/no joints, welds, fillets/);
});
check('unsupported round and custom profiles reject ALL members',()=>{
  for(const type of ['round','custom']){
    const p=exampleProject();p.members[3].profile={type,width:100,height:100,tw:5,tf:5};
    assert.throws(()=>serializeStep(p),/no compatible/);
  }
});
check('zero pieces are rejected',()=>assert.throws(()=>serializeStep(createProject()),/no tiene piezas/));
check('invalid coordinates, refs and dimensions are rejected',()=>{
  for(const mutate of [p=>p.nodes[0].x=NaN,p=>p.nodes[0].z=Infinity,p=>p.members[0].endNode='absent',p=>p.members[0].profile.width=0,p=>p.members[0].roll=Infinity]){
    const p=fixture();mutate(p);assert.throws(()=>serializeStep(p));
  }
});
check('degenerate and undersized features are rejected rather than approximated',()=>{
  for(const type of ['rhs','i','channel']){
    const p=fixture(type);p.members[0].profile.tw=.0005;assert.throws(()=>serializeStep(p),/menor que/);
    p.members[0].profile.tw=profiles[type].tw;p.members[0].profile.tf=p.members[0].profile.height/2-.0001;
    assert.throws(()=>serializeStep(p),/menor que/);
  }
  assert.throws(()=>serializeStep(fixture('rect',[0,0,0],[0,0,.0001])));
});
check('large coordinates and minimum features get CAD fixtures',()=>{
  addCase('large-coordinates',fixture('rhs',[99999000,-99999000,99999000],[99999500,-99999300,99999800],37));
  addCase('minimum-thickness',fixture('rhs',[0,0,0],[0,0,1000],0,{type:'rhs',width:60,height:100,tw:.001,tf:.001}));
  addCase('minimum-length',fixture('rect',[0,0,0],[0,0,.001]));
});
check('orientation branch near vertical and deterministic random directions',()=>{
  let seed=2102026;const rand=()=>((seed=(Math.imul(seed,1664525)+1013904223)>>>0)/2**32);
  for(let n=0;n<16;n++){
    const type=Object.keys(profiles)[n%4];addCase(`random-${n}`,fixture(type,[rand()*2000-1000,rand()*2000-1000,rand()*2000-1000],[rand()*2000+1500,rand()*2000+1500,rand()*2000+1500],rand()*720-360));
  }
  for(const z of [.96999999,.97000001])addCase(`vertical-${z}`,fixture('channel',[0,0,0],[1000*Math.sqrt(1-z*z),0,1000*z],90));
});
check('member safety limit is explicit and enforced before geometry emission',()=>{
  const p=fixture();p.members=Array.from({length:STEP_LIMITS.members},(_,i)=>({...copy(p.members[0]),id:`m${i}`,label:`Piece ${i}`}));
  const s=serializeStep(p);assert.equal((s.match(/=FACETED_BREP\(/g)||[]).length,STEP_LIMITS.members);
  p.members.push({...copy(p.members[0]),id:'extra'});assert.throws(()=>serializeStep(p),/máximo 1000/);
});
check('Family with preview saddles rejects whole STEP rather than silently omitting them',()=>{assert.throws(()=>serializeStep(createSupportTemplate()),/STEP completo pendiente/);assert(serializeStep(createSupportTemplate({saddles:false})).includes('ISO-10303-21'));});
if(process.argv.includes('--fixtures')){
  const directory=path.resolve(process.argv[process.argv.indexOf('--fixtures')+1]);fs.mkdirSync(directory,{recursive:true});
  const manifest={schema:1,purpose:'Synthetic backend CAD verification, not a measured structure',cases:[]};
  for(const c of cases){const filename=`${c.name}.step`;fs.writeFileSync(path.join(directory,filename),serializeStep(c.project));manifest.cases.push({name:c.name,file:filename,...c.expected});}
  fs.writeFileSync(path.join(directory,'manifest.json'),JSON.stringify(manifest,null,2));
  console.log(`Wrote ${cases.length} synthetic CAD fixtures to ${directory}`);
}
console.log(`${passed} STEP backend test groups passed; CAD import requires the independent Python checks.`);
