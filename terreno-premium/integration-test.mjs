/** App-handler integration tests with a deliberately minimal simulated DOM.
 * These are NOT browser, layout, touch, camera, canvas, download or IndexedDB QA.
 * The real core and app source run in Node VM; viewer and browser APIs are mocked. Photo analyzers are explicit deterministic UI stubs;
 * real pixel engines have their own test suites. Real template generator runs here.
 */
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { webcrypto } from 'node:crypto';
import vm from 'node:vm';
import {createTemplateProject,createSupportTemplate} from './dist/templates.js';

const root = new URL('./', import.meta.url);
const [html, coreSource, appSource, controllerSource] = await Promise.all(['dist/index.html','dist/core.js','dist/app.js','dist/command-controller.js'].map(path=>readFile(new URL(path,root),'utf8')));
const ids = new Map(), elements = new Set(), local = new Map();
const decode = value => String(value).replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&');
const attrs = source => Object.fromEntries([...source.matchAll(/([\w-]+)(?:="([^"]*)"|='([^']*)'|=([^\s>]+))?/g)].map(m=>[m[1],decode(m[2]??m[3]??m[4]??'')]));
class Element {
  constructor(tag='div',attributes={},owner=null) {
    this.tagName=tag.toUpperCase();this.attributes=attributes;this.id=attributes.id||'';this.owner=owner;
    this.value=attributes.value||'';this.textContent='';this.hidden='hidden' in attributes;this.disabled='disabled' in attributes;this.checked='checked' in attributes;this.open=false;this.files=[];this.dataset={};this.style={};this.listeners={};this.owned=new Set();
    this.classes=new Set((attributes.class||'').split(/\s+/).filter(Boolean));
    this.classList={add:x=>this.classes.add(x),remove:x=>this.classes.delete(x),toggle:(x,force)=>{const yes=force??!this.classes.has(x);yes?this.classes.add(x):this.classes.delete(x);return yes;},contains:x=>this.classes.has(x)};
    for(const [k,v] of Object.entries(attributes))if(k.startsWith('data-'))this.dataset[k.slice(5).replace(/-([a-z])/g,(_,c)=>c.toUpperCase())]=v;
    elements.add(this);if(this.id)ids.set(this.id,this);owner?.owned.add(this);
  }
  set innerHTML(value) {
    for(const child of this.owned){elements.delete(child);if(ids.get(child.id)===child)ids.delete(child.id);for(const desc of child.owned){elements.delete(desc);if(ids.get(desc.id)===desc)ids.delete(desc.id);}}
    this.owned.clear();this._html=value;
    for(const match of String(value).matchAll(/<([a-z][\w-]*)\b([^>]*)>/gi))new Element(match[1],attrs(match[2]),this);
    for(const match of String(value).matchAll(/<select\b([^>]*)>([\s\S]*?)<\/select>/gi)){
      const select=ids.get(attrs(match[1]).id);if(!select)continue;
      const options=[...match[2].matchAll(/<option\b([^>]*)>([\s\S]*?)<\/option>/gi)].map(m=>({...attrs(m[1]),text:decode(m[2])}));
      const chosen=options.find(o=>'selected' in o)||options[0];select.value=chosen?.value??chosen?.text??'';
    }
    for(const match of String(value).matchAll(/<textarea\b([^>]*)>([\s\S]*?)<\/textarea>/gi)){const el=ids.get(attrs(match[1]).id);if(el)el.value=decode(match[2]);}
  }
  get innerHTML(){return this._html||'';}
  addEventListener(name,fn){this.listeners[name]=fn;}
  showModal(){this.open=true;}
  close(){this.open=false;}
  scrollIntoView(){}
  focus(){this.focused=true;}
  setAttribute(name,value){this.attributes[name]=String(value);}
  getBoundingClientRect(){return {left:0,top:0,width:200,height:200};}
  setPointerCapture(){}
  closest(){return null;}
  getContext(){return {drawImage(){},fillRect(){},strokeRect(){},beginPath(){},moveTo(){},lineTo(){},stroke(){},arc(){},fill(){},fillText(){},getImageData:(x,y,w,h)=>({data:new Uint8ClampedArray(w*h*4)})};}
  toDataURL(){return 'data:image/jpeg;base64,aGVsbG8=';}
  click(){if(this.tagName==='A')downloads.push({href:this.href,name:this.download});return this.onclick?.({target:this,preventDefault(){}});}
}
const body=new Element('body');body.innerHTML=html;
const downloads=[];const blobs=new Map();let confirmations=[];
const document={
  body,
  querySelector(selector){if(selector.startsWith('#'))return ids.get(selector.slice(1))||null;return null;},
  querySelectorAll(selector){const m=selector.match(/^\[([^\]]+)\]$/);return m?[...elements].filter(e=>Object.hasOwn(e.attributes,m[1])):[];},
  createElement(tag){return new Element(tag);},
};
class StructureViewer {
  constructor(canvas,callbacks){this.canvas=canvas;this.callbacks=callbacks;this.fitCalls=0;this.camera={yaw:.7,pitch:.4,center:[10,20,30],zoomFactor:1.3,scale:.7,baseScale:.5};this.labelMode="selected";}
  setData(p,id){this.project=p;this.selectedId=id;}
  fit(){this.fitCalls++;}
  preset(name){this.lastPreset=name;}
  zoom(value){this.lastZoom=value;this.camera.zoomFactor+=value;this.callbacks.onCameraChange?.(this.getCameraState());}
  rotateAzimuth(value){this.camera.yaw+=value;this.callbacks.onCameraChange?.(this.getCameraState());}
  getCameraState(){return JSON.parse(JSON.stringify(this.camera));}
  setLabelMode(value){this.labelMode=value;}
  setMode(mode){this.mode=mode;}
}
class SimulatedImage {
  constructor(){this.width=2400;this.height=1600;}
  set src(value){this._src=value;queueMicrotask(()=>this.onload?.());}
}
const context=vm.createContext({document,StructureViewer,Image:SimulatedImage,createTemplateProject,createSupportTemplate,Uint8ClampedArray,analyzeSectionPixels:()=>{context.__sectionCalls++;return context.__sectionAnswer;},analyzeStructurePixels:()=>{context.__structureCalls++;return context.__structureAnswer;},crypto:webcrypto,console,Intl,Date,Math,JSON,Number,String,Set,Map,Array,Promise,Error,RegExp,Blob,TextEncoder,
  localStorage:{getItem:k=>local.get(k)??null,setItem:(k,v)=>local.set(k,String(v)),removeItem:k=>local.delete(k)},
  window:{addEventListener(){}},
  URL:{createObjectURL:blob=>{const key=`blob:mock-${blobs.size}`;blobs.set(key,blob);return key;},revokeObjectURL(){}},
  setTimeout:()=>1,clearTimeout(){},confirm:message=>{confirmations.push(message);return true;},
});
// UI-only worker stub: async delivery, no claim of detector validation.
const photoWorkers=[];
context.Worker=class {
 constructor(url,options){assert.equal(url,'./photo-worker.js');assert.equal(options.type,'module');photoWorkers.push(this);}
 postMessage(message){this.message=message;queueMicrotask(()=>{if(this.terminated||context.__holdPhotoWorker)return;const result=message.kind==='section'?context.analyzeSectionPixels():context.analyzeStructurePixels();this.onmessage?.({data:{id:message.id,result}});});}
 terminate(){this.terminated=true;}
};
const exports=[...coreSource.matchAll(/export (?:async )?function (\w+)|export const (\w+)/g)].map(m=>m[1]||m[2]);
vm.runInContext(`globalThis.__core=(()=>{${coreSource.replace(/export /g,'')}\nreturn {${exports.join(',')}};})();`,context);
vm.runInContext(`globalThis.createCommandController=(()=>{${controllerSource.replace(/export /g,'')}\nreturn createCommandController;})();`,context);
const runnable=appSource.replace(/^import \{([^}]+)\} from '\.\/core\.js';/m,'const {$1}=__core;').replace(/^import \{StructureViewer\} from '\.\/premium-viewer\.js';/m,'const {StructureViewer}=globalThis;').replace(/^import \{(analyzeSectionPixels|createTemplateProject,createSupportTemplate|analyzeStructurePixels|createCommandController)\} from '[^']+';/gm,'const {$1}=globalThis;').replace(/\ninit\(\);\s*$/,'\nglobalThis.__ready=init();');
vm.runInContext(runnable,context);await context.__ready;
const run=code=>vm.runInContext(code,context);
const state=()=>JSON.parse(run('JSON.stringify(project)'));
const event={preventDefault(){}};
let passed=0;
async function test(name,fn){await fn();passed++;console.log(`✓ ${name}`);}
const nodeValues=p=>JSON.stringify(p.nodes.map(({id,x,y,z})=>({id,x,y,z})));

await test('Inicialización guarda ejemplo sintético y muestra estado confirmado',async()=>{
  assert.equal(state().example,true);assert.equal(state().members.length,6);assert.equal(state().features.length,2);assert.equal(local.get('terreno-active-project'),state().id);assert.equal(ids.get('saveState').textContent,'Guardado en este dispositivo');assert.equal((await run('loadProjects()')).length,1);
});
// Keep original regression fixture after verifying the new startup family.
await run('commit({...exampleProject(),id:project.id,updatedAt:project.updatedAt},{undo:false})');run('history=[];selectedId=project.members[0].id;render()');
await test('Cambio de unidad por handler conserva geometría y persiste',async()=>{
  const geometry=nodeValues(state());ids.get('unitSelect').value='m';await ids.get('unitSelect').onchange();assert.equal(state().unit,'m');assert.equal(nodeValues(state()),geometry);assert.equal((await run('loadProjects()'))[0].unit,'m');assert.equal(ids.get('undoBtn').disabled,false);
});
await test('Deshacer restaura unidad y queda persistido',async()=>{
  await ids.get('undoBtn').onclick();assert.equal(state().unit,'mm');assert.equal((await run('loadProjects()'))[0].unit,'mm');
});
await test('Formulario de nueva pieza guarda dos nodos y sección válidos',async()=>{
  run('editMember()');ids.get('label').value='Viga de integración';ids.get('ax').value='0';ids.get('ay').value='0';ids.get('az').value='0';ids.get('bx').value='2000';ids.get('by').value='0';ids.get('bz').value='0';
  await ids.get('memberForm').onsubmit(event);assert.equal(state().members.length,9);assert.equal(state().nodes.length,8);const m=state().members.at(-1);assert.equal(m.label,'Viga de integración');assert.equal(run('lengthOf(project,project.members.at(-1))'),2000);assert.equal(ids.get('modal').open,false);
});
await test('Formulario inválido no modifica proyecto ni registro persistido',async()=>{
  const before=JSON.stringify(state());run('editMember()');ids.get('height').value='-5';await ids.get('memberForm').onsubmit(event);assert.equal(ids.get('formError').hidden,false);assert.equal(JSON.stringify(state()),before);assert.equal((await run('loadProjects()'))[0].members.length,9);run('closeModal(true)');
});
await test('Edición medida exige lectura y método y acepta ambos',async()=>{
  run('editMember(project.members.at(-1))');ids.get('status').value='measured';await ids.get('memberForm').onsubmit(event);assert.equal(ids.get('formError').hidden,false);
  ids.get('lengthMeasured').value='2000';ids.get('note').value='Cinta, lectura repetida';await ids.get('memberForm').onsubmit(event);assert.equal(state().members.at(-1).status,'measured');assert.equal(state().members.at(-1).lengthMeasured,2000);
});
await test('Movimiento y undo modifican y restauran nodos persistidos',async()=>{
  const geometry=nodeValues(state()),member=state().members.at(-1);await run(`moveMember({memberId:${JSON.stringify(member.id)},dx:10,dy:0,dz:0})`);assert.notEqual(nodeValues(state()),geometry);assert.equal(ids.get('undoBtn').disabled,false);await ids.get('undoBtn').onclick();assert.equal(nodeValues(state()),geometry);
});
await test('Movimiento de nodo compartido pide confirmación y conserva nodos ajenos',async()=>{
  const before=state(),member=before.members[0],unrelated=before.nodes.find(n=>![member.startNode,member.endNode].includes(n.id));const oldConfirmations=confirmations.length;
  await run(`moveMember({memberId:${JSON.stringify(member.id)},dx:10,dy:0,dz:0})`);assert(confirmations.length>oldConfirmations);assert.deepEqual(state().nodes.find(n=>n.id===unrelated.id),unrelated);await ids.get('undoBtn').onclick();assert.equal(nodeValues(state()),nodeValues(before));
});
await test('Eliminar pieza requiere acción explícita y undo recupera sus nodos',async()=>{
  const before=state();run('deleteMember(project.members.at(-1))');assert.equal(state().members.length,before.members.length);await ids.get('confirmDelete').onclick();assert.equal(state().members.length,before.members.length-1);await ids.get('undoBtn').onclick();assert.equal(state().members.length,before.members.length);assert.equal(nodeValues(state()),nodeValues(before));
});
await test('Flujo de foto simulado la adjunta localmente a la pieza elegida',async()=>{
  run('selectedId=project.members.at(-1).id;requestPhoto("photoFile")');const input=ids.get('photoFile');input.files=[{type:'image/jpeg',size:1200,name:'evidencia-sintetica.jpg'}];await input.onchange({target:input});const m=state().members.at(-1);assert.equal(m.photos.length,1);assert.equal(m.photos[0].name,'evidencia-sintetica.jpg');assert(m.photos[0].dataUrl.startsWith('data:image/jpeg;base64,'));assert.equal((await run('loadProjects()'))[0].members.at(-1).photos.length,1);
});
await test('Exportación del handler genera JSON legible que conserva datos',async()=>{
  run('exportProject(true)');const download=downloads.at(-1);assert(download.name.endsWith('.json'));const content=await blobs.get(download.href).text();assert.deepEqual(JSON.parse(content).project,state());
});
await test('Importación desde texto crea copia y conserva original',async()=>{
  const original=state(),json=run('serializeProject(project)');context.__backup=json;await run('importText(__backup)');assert.notEqual(state().id,original.id);assert.equal(state().members.length,original.members.length);const saved=await run('loadProjects()');assert.equal(saved.length,2);assert(saved.some(p=>p.id===original.id));
});
await test('Recarga lógica de app restaura el proyecto activo y no duplica datos',async()=>{
  const current=state();await run('init()');assert.equal(state().id,current.id);assert.equal(state().members.length,current.members.length);assert.equal((await run('loadProjects()')).length,2);
});
await test('Conflicto externo no sobrescribe datos ni cambia estado en memoria',async()=>{
  const current=state();await run(`saveProject({...project,name:'Cambio en otra pestaña'})`);
  await assert.rejects(()=>run(`commit({...project,name:'Cambio obsoleto'})`),/Conflicto/);assert.equal(state().name,current.name);assert.equal((await run('loadProjects()')).find(p=>p.id===current.id).name,'Cambio en otra pestaña');assert.equal(ids.get('errorBanner').hidden,false);
});
await test('Exportación Android usa puente limitado y no descarga Blob',async()=>{
  let received; context.window.TerrenoAndroid={exportJson:(name,json)=>{received={name,json};}};
  const before=downloads.length;run('exportProject(true)');assert.equal(downloads.length,before);assert(received.name.endsWith('.json'));assert.deepEqual(JSON.parse(received.json).project,state());
  assert(ids.get('toast').textContent.includes('Todavía no está guardado'));run('window.terrenoNativeExportResult(true,"Guardado comprobado")');assert.equal(ids.get('toast').textContent,'Guardado comprobado');delete context.window.TerrenoAndroid;
});
await test('Atrás Android cierra modal y bloquea guardado/foto en curso',async()=>{
  run('help()');assert.equal(run('window.terrenoHandleBack()'),true);assert.equal(ids.get('modal').open,false);
  assert.equal(run('window.terrenoHandleBack()'),false);run('busy=true');assert.equal(run('window.terrenoHandleBack()'),true);run('busy=false;processingPhoto=true');assert.equal(run('window.terrenoHandleBack()'),true);run('processingPhoto=false');
});
// Handler-only fixtures: pixel classification is stubbed explicitly, never claimed as image QA.
context.__sectionCalls=0;context.__structureCalls=0;
context.__sectionAnswer={status:'candidates',reason:'Fixture UI, no reconocimiento real',candidates:[{type:'rhs',label:'Tubo rectangular',score:.85,reason:'Fixture de handler'}]};
context.__structureAnswer={status:'proposal',reason:'Fixture UI de marco',candidates:[{type:'bastidor',label:'Marco de fixture',score:.8,reason:'Fixture de handler'}],nodes:[{id:'a',x:.2,y:.2},{id:'b',x:.8,y:.2},{id:'c',x:.8,y:.8},{id:'d',x:.2,y:.8}],edges:[{a:'a',b:'b'},{a:'b',b:'c'},{a:'c',b:'d'},{a:'d',b:'a'}]};
async function pickFirstPhoto(kind='structure') {run('startSectionPhoto()');ids.get('photoAnalysisKind').value=kind;ids.get('sectionPick').onclick();const target=ids.get('sectionPhotoFile');target.files=[{type:'image/png',size:1200,name:'fixture-local.png'}];await target.onchange({target});}
async function analyzePhoto(){ids.get('sectionConsent').checked=true;ids.get('sectionCropForm').onsubmit(event);await Promise.resolve();}
function templateValues(depth='0'){for(const [id,value]of Object.entries({templateName:'Borrador de integración',templateWidth:'2000',templateDepth:depth,templateHeight:'1500',templateSectionH:'80',templateSectionW:'60',templateTw:'4',templateTf:'4'}))ids.get(id).value=value;ids.get('templateUnit').value='mm';}
await test('Foto primero cancela sin persistir foto ni cambiar proyecto',async()=>{
  await run('init()');const before=JSON.stringify(state()),count=(await run('loadProjects()')).length;await pickFirstPhoto();assert.equal(run('sectionDraft.kind'),'structure');assert.equal(JSON.stringify(state()),before);run('closeModal()');assert.equal(run('sectionDraft'),null);assert.equal((await run('loadProjects()')).length,count);assert.equal(JSON.stringify(state()),before);
});
await test('Análisis exige condiciones explícitas y recorte válido',async()=>{
  await pickFirstPhoto();const calls=context.__structureCalls;assert(calls>0,'Full image automatically analysed');ids.get('sectionCropForm').onsubmit(event);assert.equal(context.__structureCalls,calls);assert.equal(ids.get('sectionError').hidden,false);ids.get('sectionConsent').checked=true;ids.get('cropX').value='90';ids.get('sectionCropForm').onsubmit(event);assert.equal(context.__structureCalls,calls);ids.get('cropX').value='0';ids.get('sectionCropForm').onsubmit(event);await Promise.resolve();assert.equal(context.__structureCalls,calls+1);assert(ids.get('sectionCandidates').innerHTML.includes('no es probabilidad'));run('closeModal(true)');
});
await test('Worker: cancelar detiene cálculo y descarta respuesta tardía sin persistir',async()=>{
 context.__holdPhotoWorker=true;const before=JSON.stringify(state());await pickFirstPhoto();const worker=photoWorkers.at(-1);assert.equal(ids.get('photoAnalysisState').attributes['data-state'],'running');assert.equal(run('processingPhoto'),true);ids.get('cancelPhotoAnalysis').onclick();assert(worker.terminated);assert.equal(run('processingPhoto'),false);assert.equal(ids.get('photoAnalysisState').attributes['data-state'],'cancelled');worker.onmessage({data:{id:worker.message.id,result:context.__structureAnswer}});assert.equal(run('sectionDraft.result'),null);assert.equal(JSON.stringify(state()),before);run('closeModal(true)');context.__holdPhotoWorker=false;
});
await test('Worker: cierre durante análisis termina trabajo sin pedir descartar ni guardar',async()=>{
 context.__holdPhotoWorker=true;await pickFirstPhoto();const worker=photoWorkers.at(-1),count=confirmations.length;run('closeModal()');assert(worker.terminated);assert.equal(run('sectionDraft'),null);assert.equal(ids.get('modal').open,false);assert.equal(confirmations.length,count);context.__holdPhotoWorker=false;
});
await test('Worker: respuesta antigua no desbloquea ni reemplaza trabajo nuevo',async()=>{
 context.__holdPhotoWorker=true;await pickFirstPhoto();const old=photoWorkers.at(-1);ids.get('cancelPhotoAnalysis').onclick();ids.get('sectionConsent').checked=true;ids.get('sectionCropForm').onsubmit(event);const current=photoWorkers.at(-1);old.onmessage({data:{id:old.message.id,result:context.__structureAnswer}});assert.equal(run('processingPhoto'),true);assert.equal(ids.get('photoCropControls').hidden,true);assert.equal(run('sectionDraft.result'),null);assert(!current.terminated);assert.equal(context.window.terrenoHandleBack(),true);assert(current.terminated);assert.equal(ids.get('modal').open,false);context.__holdPhotoWorker=false;
});
await test('Worker no disponible muestra error honesto y alternativa manual',async()=>{
 const saved=context.Worker;context.Worker=undefined;const before=JSON.stringify(state());await pickFirstPhoto();assert.equal(ids.get('photoAnalysisState').attributes['data-state'],'error');assert(ids.get('photoAnalysisManual'));assert.equal(run('processingPhoto'),false);assert.equal(JSON.stringify(state()),before);run('closeModal(true)');context.Worker=saved;
});
await test('Worker: error recuperable y timeout son estados distintos, nunca propuesta',async()=>{
 context.__holdPhotoWorker=true;const before=JSON.stringify(state());await pickFirstPhoto();let worker=photoWorkers.at(-1);worker.onerror({preventDefault(){}});assert.equal(ids.get('photoAnalysisState').attributes['data-state'],'error');assert(worker.terminated);assert.equal(run('sectionDraft.result'),null);
 const saved=context.setTimeout;let timeout;context.setTimeout=(fn,ms)=>{assert.equal(ms,15000);timeout=fn;return 77;};ids.get('sectionConsent').checked=true;ids.get('sectionCropForm').onsubmit(event);worker=photoWorkers.at(-1);timeout();assert.equal(ids.get('photoAnalysisState').attributes['data-state'],'timeout');assert(worker.terminated);assert.equal(run('processingPhoto'),false);assert.equal(JSON.stringify(state()),before);context.setTimeout=saved;context.__holdPhotoWorker=false;run('closeModal(true)');
});
await test('No identificable no inventa grafo ni crea proyecto',async()=>{
  const answer=context.__structureAnswer;context.__structureAnswer={status:'unidentifiable',reason:'Sin marco',candidates:[],nodes:[],edges:[]};const before=JSON.stringify(state());await pickFirstPhoto();await analyzePhoto();assert(ids.get('sectionCandidates').innerHTML.includes('No identificable'));assert.equal(document.querySelectorAll('[data-structure-candidate]').length,0);assert.equal(JSON.stringify(state()),before);run('closeModal(true)');context.__structureAnswer=answer;
});
await test('Grafo requiere revisión y cada edición revoca la aceptación',async()=>{
  const before=JSON.stringify(state());await pickFirstPhoto();await analyzePhoto();run('chooseStructureCandidate(0)');ids.get('graphContinue').onclick();assert.equal(ids.get('graphError').hidden,false);assert.equal(JSON.stringify(state()),before);ids.get('graphAccepted').checked=true;ids.get('graphX-g1').value='30';ids.get('graphX-g1').oninput();assert.equal(ids.get('graphAccepted').checked,false);assert.equal(run('sectionDraft.graphEdited'),true);ids.get('graphAccepted').checked=true;ids.get('graphContinue').onclick();assert.equal(ids.get('templateWidth').value,'');assert.equal(ids.get('templateDepth').value,'');assert.equal(ids.get('templateHeight').value,'');
});
await test('Grafo a 3D conserva edición, exige medidas y no infiere profundidad',async()=>{
  const original=state(),count=(await run('loadProjects()')).length;await ids.get('templateForm').onsubmit(event);assert.equal(ids.get('templateError').hidden,false);templateValues('500');ids.get('templateConfirmed').checked=true;await ids.get('templateForm').onsubmit(event);assert(ids.get('templateError').textContent.includes('extrusión manual'));assert.equal(state().id,original.id);ids.get('confirmExtrusion').checked=true;await ids.get('templateForm').onsubmit(event);const p=state();assert.notEqual(p.id,original.id);assert.equal(p.nodes.length,8);assert.equal(p.members.length,12);assert.equal(p.photoProposal.graphEdited,true);assert.equal(p.photoProposal.reviewedGraph.nodes[0].x,.3);assert(Math.abs(p.nodes[0].x-333.3333333333333)<.0001);assert.equal(p.photoProposal.extrusion,'manual-confirmed');assert.equal(p.members.filter(m=>m.photos.length).length,1);assert(p.members.every(m=>m.status==='estimated'&&m.lengthMeasured===null));assert.equal((await run('loadProjects()')).length,count+1);assert((await run('loadProjects()')).some(x=>x.id===original.id));await run('init()');assert.deepEqual(state().photoProposal,p.photoProposal);
});
await test('Grafo revisado admite plano explícito sin duplicación trasera',async()=>{
  await pickFirstPhoto();await analyzePhoto();run('chooseStructureCandidate(0)');ids.get('graphAccepted').checked=true;ids.get('graphContinue').onclick();templateValues('0');ids.get('templateConfirmed').checked=true;await ids.get('templateForm').onsubmit(event);assert.equal(state().nodes.length,4);assert.equal(state().members.length,4);assert(state().nodes.every(n=>n.y===0));assert.equal(state().photoProposal.extrusion,'none');
});
await test('Sección candidata deja dimensiones/posición vacías y cancelar no adjunta foto',async()=>{
  const before=JSON.stringify(state());await pickFirstPhoto('section');await analyzePhoto();run('useSectionCandidate(0)');for(const id of ['height','width','tw','tf','ax','ay','az','bx','by','bz'])assert.equal(ids.get(id).value,'');assert.equal(ids.get('profileType').value,'rhs');await ids.get('memberForm').onsubmit(event);assert.equal(ids.get('formError').hidden,false);ids.get('cancelEdit').onclick();assert.equal(JSON.stringify(state()),before);
});
await test('Sección confirmada guarda medidas manuales y trazabilidad local',async()=>{
  const count=state().members.length;await pickFirstPhoto('section');await analyzePhoto();run('useSectionCandidate(0)');for(const [id,value]of Object.entries({height:'80',width:'60',tw:'4',tf:'4',ax:'0',ay:'0',az:'0',bx:'1000',by:'0',bz:'0'}))ids.get(id).value=value;ids.get('confirmPhotoDimensions').checked=true;await ids.get('memberForm').onsubmit(event);assert.equal(state().members.length,count+1);const member=state().members.at(-1);assert.equal(member.photos.length,1);assert(member.note.includes('no inferidas de la imagen'));assert.equal(member.profile.width,60);assert.equal(run('lengthOf(project,project.members.at(-1))'),1000);
});
await test('Plantilla manual crea otro proyecto sin sobrescribir y convierte unidades',async()=>{
  const original=state();run('templateForm()');templateValues('1');ids.get('templateUnit').value='m';ids.get('templateWidth').value='2';ids.get('templateHeight').value='1.5';ids.get('templateSectionH').value='.08';ids.get('templateSectionW').value='.06';ids.get('templateTw').value='.004';ids.get('templateTf').value='.004';ids.get('templateConfirmed').checked=true;await ids.get('templateForm').onsubmit(event);assert.notEqual(state().id,original.id);assert.equal(state().unit,'m');assert.equal(state().template.origin,'manual-template');assert.equal(Math.max(...state().nodes.map(n=>n.x)),2000);assert.equal(state().members[0].profile.height,80);assert((await run('loadProjects()')).some(x=>x.id===original.id));
});

await test('Modo campo es reversible y nunca cambia No molestar automáticamente',async()=>{
  let calls=[],dnd=0;context.window.TerrenoAndroid={setFieldMode:value=>calls.push(value),openDndSettings:()=>dnd++};run('fieldControls()');ids.get('toggleFieldMode').onclick();assert.equal(run('fieldMode'),true);assert.equal(body.classList.contains('field-mode'),true);assert.deepEqual(calls,[true]);assert.equal(dnd,0);run('fieldControls()');ids.get('openDndSettings').onclick();assert.equal(dnd,1);run('closeModal(true)');assert.equal(run('window.terrenoHandleBack()'),true);assert.equal(run('fieldMode'),false);assert.deepEqual(calls,[true,false]);assert.equal(dnd,1);delete context.window.TerrenoAndroid;
});

const cameraState=()=>JSON.parse(run('JSON.stringify(viewer.getCameraState())'));
const pointer=(pointerId,clientX,clientY)=>({pointerId,clientX,clientY,preventDefault(){},stopPropagation(){}});
await test('Azimut virtual ± conserva inclinación, zoom, pivote y toda la geometría',async()=>{
  const before=JSON.stringify(state()),camera=cameraState();ids.get('azimuthToggle').onclick();assert.equal(ids.get('azimuthPanel').hidden,false);ids.get('azimuthPlus').onclick();const after=cameraState();assert(Math.abs(after.yaw-camera.yaw-Math.PI/12)<1e-10);for(const key of ['pitch','zoomFactor','scale','baseScale','center'])assert.deepEqual(after[key],camera[key]);assert.equal(JSON.stringify(state()),before);ids.get('azimuthMinus').onclick();assert(Math.abs(cameraState().yaw-camera.yaw)<1e-10);assert.equal(run('window.terrenoHandleBack()'),true);assert.equal(ids.get('azimuthPanel').hidden,true);
});
await test('Arrastre circular y teclado solo cambian azimut; cancelación y segundo dedo no saltan',async()=>{
  run('showAzimuth(true)');const camera=cameraState(),dial=ids.get('azimuthDial');dial.onpointerdown(pointer(1,200,100));dial.onpointermove(pointer(1,100,200));assert(Math.abs(cameraState().yaw-camera.yaw-Math.PI/2)<1e-10);dial.onpointercancel(pointer(1,100,200));const cancelled=cameraState().yaw;dial.onpointermove(pointer(1,0,100));assert.equal(cameraState().yaw,cancelled);dial.onpointerdown(pointer(2,200,100));dial.onpointerdown(pointer(3,100,200));dial.onpointermove(pointer(2,0,100));assert.equal(cameraState().yaw,cancelled);dial.onkeydown({key:'ArrowRight',preventDefault(){}});assert(Math.abs(cameraState().yaw-cancelled-Math.PI/36)<1e-10);assert.equal(cameraState().pitch,camera.pitch);dial.onkeydown({key:'Escape'});assert.equal(ids.get('azimuthPanel').hidden,true);
});
await test('Centro del dial es zona muerta: salir reancla sin salto; ratón secundario y cancelar no giran',async()=>{
  run('showAzimuth(true)');const dial=ids.get('azimuthDial'),camera=cameraState(),before=JSON.stringify(state());dial.onpointerdown(pointer(10,100,100));dial.onpointermove(pointer(10,111,100));assert.equal(cameraState().yaw,camera.yaw);dial.onpointermove(pointer(10,200,100));assert.equal(cameraState().yaw,camera.yaw);dial.onpointermove(pointer(10,100,200));assert(Math.abs(cameraState().yaw-camera.yaw-Math.PI/2)<1e-10);const turned=cameraState().yaw;dial.onpointermove(pointer(10,100,100));dial.onpointermove(pointer(10,100,0));assert.equal(cameraState().yaw,turned);dial.onpointermove(pointer(10,200,100));assert(Math.abs(cameraState().yaw-turned-Math.PI/2)<1e-10);dial.onpointermove(pointer(10,100,100));dial.onpointercancel(pointer(10,100,100));const cancelled=cameraState().yaw;dial.onpointermove(pointer(10,0,100));assert.equal(cameraState().yaw,cancelled);for(const button of [1,2]){dial.onpointerdown({...pointer(20+button,200,100),pointerType:'mouse',button});dial.onpointermove({...pointer(20+button,100,200),pointerType:'mouse',button});assert.equal(cameraState().yaw,cancelled);}assert.equal(cameraState().pitch,camera.pitch);assert.equal(JSON.stringify(state()),before);run('showAzimuth(false)');
});
await test('Cubo por botón o callback ofrece presets sin alterar modelo y respeta formularios abiertos',async()=>{
  const before=JSON.stringify(state());ids.get('orientationBtn').onclick();assert.equal(ids.get('modal').open,true);assert(ids.get('orientationCube'));document.querySelectorAll('[data-orientation]').find(x=>x.dataset.orientation==='top').onclick();assert.equal(run('viewer.lastPreset'),'top');assert.equal(ids.get('modal').open,false);run('viewer.callbacks.onOrientationRequest()');assert.equal(ids.get('modalTitle').textContent,'Orientación de la cámara');ids.get('orientationClose').onclick();run('editMember(project.members[0])');ids.get('label').value='No reemplazar borrador';run('viewer.callbacks.onOrientationRequest()');assert(ids.get('memberForm'));assert.equal(ids.get('label').value,'No reemplazar borrador');run('closeModal(true)');assert.equal(JSON.stringify(state()),before);
});
await test('Navegación contextual ± solo cámara; Aceptar limpia modo/foco y no guarda datos',async()=>{
  const before=JSON.stringify(state());ids.get('controlPanelBtn').onclick();const camera=cameraState();ids.get('controlPlus').onclick();assert(Math.abs(cameraState().yaw-camera.yaw-Math.PI/36)<1e-10);ids.get('controlFocus').value='zoom';ids.get('controlFocus').onchange();ids.get('controlPlus').onclick();assert.equal(run('viewer.lastZoom'),.1);ids.get('controlAccept').onclick();assert.equal(ids.get('modal').open,false);assert.equal(run('commandController.getState().mode'),'navigation');assert.equal(run('commandController.getState().focus'),null);assert.equal(JSON.stringify(state()),before);
});
await test('Cambiar a medición bloquea ± y eventos de contexto anterior sin fabricar lectura',async()=>{
  run('selectedId=project.members[0].id;render()');
  const before=JSON.stringify(state()),camera=cameraState();ids.get('controlPanelBtn').onclick();ids.get('controlMode').value='measurement';ids.get('controlMode').onchange();assert.equal(ids.get('controlMinus').disabled,true);assert.equal(ids.get('controlPlus').disabled,true);ids.get('controlPlus').onclick();assert.equal(run('commandController.step(1).ok'),false);assert.equal(run('commandController.dispatch({type:"step",mode:"navigation",focus:"azimuth",direction:1,source:"touch"}).ok'),false);assert.deepEqual(cameraState(),camera);ids.get('controlAccept').onclick();assert(ids.get('memberForm'));assert.equal(ids.get('lengthMeasured').value,'');assert.equal(ids.get('lengthMeasured').focused,true);assert.equal(JSON.stringify(state()),before);run('closeModal(true)');assert.equal(run('commandController.getState().focus'),null);
});
await test('Contexto editar selecciona sin mover ni guardar; Aceptar abre ficha explícita',async()=>{
  const before=JSON.stringify(state()),selected=run('selectedId');ids.get('controlPanelBtn').onclick();const stalePlus=ids.get('controlPlus').onclick;ids.get('controlMode').value='editing';ids.get('controlMode').onchange();stalePlus();assert.equal(run('selectedId'),selected);ids.get('controlPlus').onclick();assert.notEqual(run('selectedId'),selected);assert.equal(JSON.stringify(state()),before);ids.get('controlAccept').onclick();assert(ids.get('memberForm'));assert.equal(ids.get('label').value,run('getMember().label'));run('closeModal(true)');assert.equal(JSON.stringify(state()),before);
});
await test('Rótulos completos son opt-in y modo campo no reinicia la cámara',async()=>{
  ids.get('controlPanelBtn').onclick();assert.equal(ids.get('showAllLabels').checked,false);ids.get('showAllLabels').checked=true;ids.get('showAllLabels').onchange();assert.equal(run('viewer.labelMode'),'all');ids.get('showAllLabels').checked=false;ids.get('showAllLabels').onchange();assert.equal(run('viewer.labelMode'),'selected');run('closeModal(true)');const camera=cameraState(),fits=run('viewer.fitCalls');run('applyFieldMode(true);applyFieldMode(false)');assert.deepEqual(cameraState(),camera);assert.equal(run('viewer.fitCalls'),fits);
});


await test('Menú lateral gira, recorre y se oculta sin cambiar geometría',async()=>{
  const before=JSON.stringify(state());run('sceneWheelContext="menu";closeSceneMenu()');
  for(let i=0;i<5;i++){run('sceneStep(1)');assert.equal(run('sceneIndex'),i);assert.equal(ids.get('sceneMenu').hidden,false);}
  run('sceneStep(1)');assert.equal(ids.get('sceneMenu').hidden,true);assert.equal(run('sceneIndex'),-1);assert.equal(JSON.stringify(state()),before);
  run('sceneStep(-1)');assert.equal(run('sceneIndex'),4);ids.get('sceneMenuClose').onclick();assert.equal(run('sceneIndex'),-1);
});
await test('Rueda en modo giro conserva pivote y no abre menú ni modifica geometría',async()=>{
  const before=JSON.stringify(state()),camera=cameraState();ids.get('sceneWheelMode').onclick();assert.equal(run('sceneWheelContext'),'camera');run('sceneStep(1)');assert(Math.abs(cameraState().yaw-camera.yaw-Math.PI/36)<1e-10);assert.deepEqual(cameraState().center,camera.center);assert.equal(ids.get('sceneMenu').hidden,true);assert.equal(JSON.stringify(state()),before);ids.get('sceneWheelMode').onclick();
});
await test('Familia: cancelar conserva proyecto y crear ejemplo no sobrescribe anterior',async()=>{
  const before=state();run('familyChooser()');ids.get('familySaddles').onclick();ids.get('familyCancel').onclick();assert.equal(state().id,before.id);
  run('supportFamilyForm(true)');ids.get('familyLength').value='3600';ids.get('familyWidth').value='1000';ids.get('familyRadius').value='700';ids.get('familyConsent').checked=true;await ids.get('supportFamilyForm').onsubmit(event);assert.notEqual(state().id,before.id);assert.equal(state().template.dimensionsMm.length,3600);assert.equal(state().features.length,2);assert.equal(state().example,true);assert(state().members.every(x=>x.status==='estimated'&&x.lengthMeasured===null));assert((await run('loadProjects()')).some(x=>x.id===before.id));
  const json=run('serializeProject(project)');context.__familyBackup=json;assert.equal(run('parseProject(__familyBackup).features[0].exportable'),false);
});
await test('Pieza requiere selección y ficha se cierra explícitamente',async()=>{
  run('selectedId=null;runSceneAction("piece")');assert.equal(body.classList.contains('inspector-open'),false);run('selectedId=project.members[0].id;render();runSceneAction("piece")');assert.equal(body.classList.contains('inspector-open'),true);ids.get('sceneInspectorClose').onclick();assert.equal(body.classList.contains('inspector-open'),false);
});
console.log(`\n${passed} pruebas de integración correctas (DOM/visor simulados, localStorage en memoria).`);
console.log('No equivalen a QA de navegador, móvil real, render 3D, cámara, descarga física ni IndexedDB.');
