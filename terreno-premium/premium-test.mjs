import assert from 'node:assert/strict';
import * as THREE from './dist/vendor/three/three.module.js';
import {StructureViewer} from './dist/premium-viewer.js';
import {createSupportTemplate} from './dist/templates.js';
import {serializeStep} from './dist/step-export.js';
const v=Object.create(StructureViewer.prototype);v.project=createSupportTemplate();v.width=844;v.height=390;v.scale=.13;v.center=[800,400,100];v.yaw=.72;v.pitch=.6;v.camera=new THREE.OrthographicCamera();
const before=JSON.stringify(v.project);
for(const [yaw,pitch] of [[.72,.6],[-2,.4],[0,Math.PI/2],[.3,-.4]]){v.yaw=yaw;v.pitch=pitch;v.syncGpuCamera();for(const a of [[0,0,0],[1300,400,130],[44,-231,584]]){const old=v.projectPoint(a),q=new THREE.Vector3(-a[0],a[1],a[2]).project(v.camera);assert(Math.abs((q.x+1)*v.width/2-old.x)<1e-7);assert(Math.abs((1-q.y)*v.height/2-old.y)<1e-7)}}
for(const m of v.project.members){const g=v.facesGeometry(v.geometry(m));assert(g.attributes.position.count>0);assert([...g.attributes.position.array,...g.attributes.normal.array].every(Number.isFinite));g.dispose()}
for(const f of v.project.features){const g=v.saddleGeometry(f);g.computeBoundingBox();assert(Math.abs(g.boundingBox.min.x-(f.x-f.length/2))<.001);assert(Math.abs(g.boundingBox.max.y-(f.y+f.width/2))<.001);assert([...g.attributes.position.array,...g.attributes.normal.array].every(Number.isFinite));const points=g.attributes.position.array,normals=g.attributes.normal.array,index=g.index.array;for(let i=0;i<index.length;i+=3){const a=index[i],b=index[i+1],c=index[i+2],pa=new THREE.Vector3().fromArray(points,a*3),pb=new THREE.Vector3().fromArray(points,b*3),pc=new THREE.Vector3().fromArray(points,c*3),n=new THREE.Vector3().fromArray(normals,a*3);assert(pb.sub(pa).cross(pc.sub(pa)).dot(n)>0,'Physical saddle winding agrees with outward normal')}g.dispose()}
assert.equal(JSON.stringify(v.project),before);assert.throws(()=>serializeStep(v.project),/cuna|completo|compatible|preliminar/i);
console.log('Premium render: camera projection parity, finite geometry/normals, saddle bounds, immutable model, STEP guard PASS');

// A 3-4-5 synthetic member gives an independent geometric expectation. The
// recorded field length deliberately differs: the overlay must never pass it
// off as the geometric length, or certify a user-entered measurement.
const annotationViewer=Object.create(StructureViewer.prototype);
const annotationProject=createSupportTemplate(),selected=annotationProject.members[0];
Object.assign(annotationProject.nodes.find(n=>n.id===selected.startNode),{x:0,y:0,z:0});
Object.assign(annotationProject.nodes.find(n=>n.id===selected.endNode),{x:300,y:400,z:0});
selected.label='Pieza sintética';selected.lengthMeasured=510;
const freeze=value=>{if(value&&typeof value==='object'){Object.values(value).forEach(freeze);Object.freeze(value);}return value;};
for(const [unit,expected] of [['mm','500 mm'],['cm','50 cm'],['m','0,5 m']]){
 for(const [status,provenance] of [['estimated','Estimado · no medido'],['measured','Medido · registro del usuario'],['confirmed','Confirmado por el usuario']]){
  const model=structuredClone(annotationProject);model.unit=unit;model.members[0].status=status;
  annotationViewer.project=freeze(model);annotationViewer.selectedId=selected.id;
  const snapshot=JSON.stringify(model),member=model.members[0],annotation=annotationViewer.selectionAnnotation(member);
  assert.equal(annotation.id,member.id);
  assert.equal(annotation.dimension,`${expected} · longitud geométrica`);
  assert.equal(annotation.status,provenance);
  assert.equal(model.members.map(m=>annotationViewer.selectionAnnotation(m)).filter(Boolean).length,1,'Only the selected member has a dimension');
  annotationViewer.selectedId=null;
  assert(model.members.every(m=>annotationViewer.selectionAnnotation(m)===null),'Deselecting removes the temporary dimension');
  assert.equal(JSON.stringify(model),snapshot,'Annotation must not mutate source geometry or provenance');
 }
}
// Record Canvas draw calls to check the real label hit rectangle includes all
// three lines. This is layout geometry coverage, not a browser visual claim.
const textDraws=[],boxes=[];
annotationViewer.width=844;annotationViewer.height=390;annotationViewer.selectedId=selected.id;annotationViewer.labelHits=[];
annotationViewer.ctx={save(){},restore(){},measureText(text){return {width:text.length*6};},beginPath(){},roundRect(x,y,w,h){boxes.push({x,y,w,h});},fill(){},stroke(){},fillText(text,x,y){textDraws.push({text,x,y});}};
annotationViewer.drawLabel(annotationViewer.project.members[0],422,195);
assert.equal(textDraws.length,3);
assert.equal(textDraws.filter(t=>t.text.includes('longitud geométrica')).length,1);
const hit=annotationViewer.labelHits[0];assert.equal(hit.id,selected.id);assert.deepEqual({...hit,id:undefined},{...boxes[0],id:undefined});
for(const line of textDraws)assert(line.y-12>=hit.y&&line.y+3<=hit.y+hit.h,'Text including ascent/descent must fit inside the pick box');
annotationViewer.gpuReady=true;
assert.equal(annotationViewer.pick({x:hit.x+hit.w/2,y:hit.y+hit.h-1}),selected.id,'Bottom of third-line label still selects the same member');
console.log('Premium selection: one geometric dimension, units, user-attributed provenance, deselection, immutable model, three-line hit box PASS');
