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
