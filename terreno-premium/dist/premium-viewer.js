import * as THREE from './vendor/three/three.module.js';
import {StructureViewer as SurveyViewer} from './viewer.js';
// Render-only adapter. The survey geometry, section/roll convention, camera state,
// pointer lifecycle and STEP exporter stay authoritative and unchanged.
export class StructureViewer extends SurveyViewer {
 constructor(canvas,options={}){
  super(canvas,options);this.gpuReady=false;this.lastGeometry=null;
  const glCanvas=document.createElement('canvas');glCanvas.className='premium-render';glCanvas.setAttribute('aria-hidden','true');this.glCanvas=glCanvas;
  try{
   const gl=glCanvas.getContext('webgl2',{alpha:true,antialias:true,powerPreference:'low-power'});
   if(!gl)throw Error('WebGL2 no disponible');
   this.renderer=new THREE.WebGLRenderer({canvas:glCanvas,context:gl,alpha:true,antialias:true});
   this.renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.75));this.renderer.setClearColor(0x151c24,0);
   this.renderer.debug.onShaderError=()=>{this.gpuReady=false;glCanvas.hidden=true;this.rendererName='Canvas · GPU no disponible';this.fallbackReason='No se pudo compilar el material local.';this.drawSoon();};
   this.renderer.outputColorSpace=THREE.SRGBColorSpace;this.renderer.toneMapping=THREE.ACESFilmicToneMapping;this.renderer.toneMappingExposure=1.15;
   this.renderer.shadowMap.enabled=true;this.renderer.shadowMap.type=THREE.PCFSoftShadowMap;
   canvas.parentNode.insertBefore(glCanvas,canvas);this.scene=new THREE.Scene();this.camera=new THREE.OrthographicCamera();this.camera.near=.1;this.camera.far=1e7;
   this.model=new THREE.Group();this.model.scale.x=-1;this.scene.add(this.model);
   this.scene.add(new THREE.HemisphereLight(0xdcecff,0x69717b,2.0));
   this.key=new THREE.DirectionalLight(0xfff6e7,3.1);this.key.castShadow=true;this.key.shadow.mapSize.set(1024,1024);this.key.shadow.bias=-.00008;this.key.shadow.normalBias=1;this.key.shadow.radius=3;this.scene.add(this.key);this.scene.add(this.key.target);
   this.rim=new THREE.DirectionalLight(0xc3d6f0,1.7);this.scene.add(this.rim);this.scene.add(this.rim.target);
   this.steel=new THREE.MeshStandardMaterial({color:0xa7b1b9,metalness:.28,roughness:.48,side:THREE.DoubleSide});
   this.selectedSteel=new THREE.MeshStandardMaterial({color:0xd0b67c,metalness:.25,roughness:.48,side:THREE.DoubleSide});
   this.saddleSteel=new THREE.MeshStandardMaterial({color:0x8897a4,metalness:.22,roughness:.53,side:THREE.DoubleSide});
   this.edgesMaterial=new THREE.LineBasicMaterial({color:0x28323e,transparent:true,opacity:.36});
   this.selectedEdges=new THREE.LineBasicMaterial({color:0xf7dba0,transparent:true,opacity:.75});
   this.raycaster=new THREE.Raycaster();this.gpuReady=true;this.rendererName='WebGL2 · Three.js';
   glCanvas.addEventListener('webglcontextlost',e=>{e.preventDefault();this.gpuReady=false;glCanvas.hidden=true;this.rendererName='Canvas · recuperación de GPU';this.drawSoon();});
   glCanvas.addEventListener('webglcontextrestored',()=>{this.gpuReady=true;glCanvas.hidden=false;this.rendererName='WebGL2 · Three.js';this.lastGeometry=null;this.drawSoon();});
  }catch(error){this.renderer?.dispose();glCanvas.remove();this.rendererName='Canvas · modo compatible';this.fallbackReason=String(error.message);}
  this.visibilityHandler=()=>{if(!document.hidden)this.drawSoon()};document.addEventListener('visibilitychange',this.visibilityHandler);this.drawSoon();
 }
 clearModel(){if(!this.model)return;for(const child of [...this.model.children]){this.model.remove(child);child.geometry?.dispose();if(child.userData.ownMaterial)child.material.dispose();}this.pickMeshes=[];this.memberObjects=[];}
 facesGeometry(faces){const positions=[];for(const p of faces)for(let i=1;i<p.length-1;i++)positions.push(...p[0],...p[i],...p[i+1]);const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));g.computeVertexNormals();return g;}
 saddleGeometry(f){
  // Same semantic radius/width/length/thickness as legacy display; finer visual
  // tessellation only. This remains a non-exportable, unverified preview feature.
  const positions=[],normals=[],indices=[],n=64;
  const point=(x,y,bottom=false)=>[f.x+x*f.length/2,f.y+y,f.z+f.radius-Math.sqrt(Math.max(0,f.radius*f.radius-y*y))-(bottom?f.thickness:0)];
  const normal=(y,bottom=false)=>{const slope=y/Math.sqrt(Math.max(1e-12,f.radius*f.radius-y*y)),k=(bottom?-1:1)/Math.hypot(slope,1);return [0,-slope*k,k]};
  // Hard plate boundaries, analytic smooth normals along the curved surface.
  // Render coordinate reflection lives on model.matrixWorld, so Three handles
  // winding and normal transformation together; geometry winding stays physical.
  const quad=(v,ns)=>{const a=positions.length/3;v.forEach((p,i)=>{positions.push(...p);normals.push(...ns[i])});indices.push(a,a+1,a+2,a,a+2,a+3)};
  for(let i=0;i<n;i++){const y=-f.width/2+f.width*i/n,z=-f.width/2+f.width*(i+1)/n;
   quad([point(-1,y),point(1,y),point(1,z),point(-1,z)],[normal(y),normal(y),normal(z),normal(z)]);
   quad([point(-1,z,true),point(1,z,true),point(1,y,true),point(-1,y,true)],[normal(z,true),normal(z,true),normal(y,true),normal(y,true)]);
   for(const x of [-1,1]){const v=[point(x,y),point(x,z),point(x,z,true),point(x,y,true)];if(x===1)v.reverse();quad(v,Array(4).fill([x,0,0]));}
  }
  for(const side of [-1,1]){const y=side*f.width/2,v=[point(-1,y),point(-1,y,true),point(1,y,true),point(1,y)];if(side===1)v.reverse();quad(v,Array(4).fill([0,side,0]));}
  const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));g.setAttribute('normal',new THREE.Float32BufferAttribute(normals,3));g.setIndex(indices);return g;
 }
 rebuild(){
  this.clearModel();
  for(const m of this.project.members||[]){const savedPreview=this.preview;this.preview=null;let g;try{g=this.facesGeometry(this.geometry(m))}finally{this.preview=savedPreview;}const selected=m.id===this.selectedId;const mesh=new THREE.Mesh(g,selected?this.selectedSteel:this.steel);mesh.userData.memberId=m.id;mesh.castShadow=true;mesh.receiveShadow=true;this.model.add(mesh);this.pickMeshes.push(mesh);const e=new THREE.LineSegments(new THREE.EdgesGeometry(g,30),selected?this.selectedEdges:this.edgesMaterial);this.model.add(e);this.memberObjects.push({id:m.id,mesh,edges:e});}
  for(const f of this.project.features||[])if(f.type==='saddle-preview'){const mesh=new THREE.Mesh(this.saddleGeometry(f),this.saddleSteel);mesh.castShadow=true;mesh.receiveShadow=true;this.model.add(mesh);}
  const box=new THREE.Box3().setFromObject(this.model);if(box.isEmpty())return;
  const size=box.getSize(new THREE.Vector3()),center=box.getCenter(new THREE.Vector3()),span=Math.max(size.x,size.y,size.z,500);
  const ground=new THREE.Mesh(new THREE.PlaneGeometry(span*4,span*4),new THREE.ShadowMaterial({color:0x05090f,opacity:.28}));ground.position.copy(this.model.worldToLocal(new THREE.Vector3(center.x,center.y,box.min.z-2)));ground.receiveShadow=true;ground.userData.ownMaterial=true;this.model.add(ground);
  this.key.position.set(center.x-span*.7,center.y-span*.9,center.z+span*1.8);this.key.target.position.copy(center);Object.assign(this.key.shadow.camera,{left:-span,right:span,top:span,bottom:-span,near:1,far:span*6});this.key.shadow.camera.updateProjectionMatrix();
  this.rim.position.set(center.x+span,center.y+span*.4,center.z+span*.7);this.rim.target.position.copy(center);
 }
 syncGpuCamera(){
  const axes=this.cameraAxes(),distance=Math.max(10000,1/this.scale*Math.max(this.width,this.height)*3),halfW=this.width/(2*this.scale),halfH=this.height/(2*this.scale);
  // The legacy screen basis is left handed. Convert render space by F=(-X,Y,Z)
  // on the model group AND camera, never on the survey data. The normal positive
  // frustum remains intact. Three detects negative matrixWorld determinant and
  // corrects frontFace winding; inverse-transpose transforms normals consistently.
  this.camera.near=Math.max(.01,distance/10000);this.camera.far=distance*8;this.camera.left=-halfW;this.camera.right=halfW;this.camera.top=halfH;this.camera.bottom=-halfH;
  const reflect=p=>[-p[0],p[1],p[2]];
  this.camera.position.fromArray(reflect(this.center.map((v,i)=>v+axes.depth[i]*distance)));this.camera.up.fromArray(reflect(axes.down.map(v=>-v)));this.camera.lookAt(...reflect(this.center));this.camera.updateProjectionMatrix();this.camera.updateMatrixWorld(true);
 }
 draw(){
  if(!this.gpuReady){super.draw();const badge=document.querySelector('#renderStatus');if(badge)badge.textContent=this.rendererName||'Canvas · modo compatible';return;}if(document.hidden)return;
  const c=this.ctx,w=this.width,h=this.height;if(!c||!w||!h)return;
  c.clearRect(0,0,w,h);this.hits=[];this.labelHits=[];
  const key=JSON.stringify([this.project.nodes,this.project.members,this.project.features]);if(key!==this.lastGeometry){this.rebuild();this.lastGeometry=key;}
  for(const item of this.memberObjects||[]){const selected=item.id===this.selectedId,delta=this.preview?.memberId===item.id?this.preview.delta:[0,0,0];item.mesh.material=selected?this.selectedSteel:this.steel;item.edges.material=selected?this.selectedEdges:this.edgesMaterial;item.mesh.position.fromArray(delta);item.edges.position.fromArray(delta);}
  if(this.gpuWidth!==w||this.gpuHeight!==h){this.renderer.setSize(w,h,false);this.gpuWidth=w;this.gpuHeight=h;}this.syncGpuCamera();try{this.renderer.render(this.scene,this.camera);}catch(error){this.gpuReady=false;this.glCanvas.hidden=true;this.rendererName='Canvas · recuperación de GPU';this.fallbackReason=String(error.message);const badge=document.querySelector('#renderStatus');if(badge)badge.textContent=this.rendererName;super.draw();return;}
  if(this.mode==='draw'){c.save();c.globalAlpha=.35;super.drawGrid();c.restore();}
  for(const m of this.project.members||[])if(m.id===this.selectedId||this.labelMode==='all'){const ends=this.ends(m);if(!ends)continue;const a=this.projectPoint(ends[0]),b=this.projectPoint(ends[1]);if(m.id===this.selectedId){c.strokeStyle='#e7cb91';c.fillStyle='#151c24';for(const p of [a,b]){c.beginPath();c.arc(p.x,p.y,4,0,Math.PI*2);c.fill();c.stroke();}}this.drawLabel(m,(a.x+b.x)/2,(a.y+b.y)/2);}
  if(!(this.project.members||[]).length){c.fillStyle='#b7c3d2';c.font='500 16px Inter,system-ui';c.textAlign='center';c.fillText('Tu estructura empieza aquí',w/2,h/2);c.textAlign='left';}
  this.drawDrawingPreview();
  const badge=document.querySelector('#renderStatus');if(badge)badge.textContent=this.rendererName;
 }
 drawLabel(m,x,y){const c=this.ctx,text=String(m.label||m.id);c.font='500 12px Inter,system-ui';const w=Math.min(220,c.measureText(text).width+28),h=30;x=Math.min(this.width-w-18,Math.max(18,x-w/2));y=Math.max(88,y-44);c.fillStyle='#202a36';c.strokeStyle='#9c8557';c.beginPath();c.roundRect(x,y,w,h,8);c.fill();c.stroke();c.fillStyle='#f4e5c4';c.fillText(text.slice(0,32),x+14,y+19);this.labelHits.push({id:m.id,x,y,w,h});}
 pick(p){if(!this.gpuReady)return super.pick(p);for(const h of this.labelHits)if(p.x>=h.x&&p.x<=h.x+h.w&&p.y>=h.y&&p.y<=h.y+h.h)return h.id;this.syncGpuCamera();this.raycaster.setFromCamera(new THREE.Vector2(p.x/this.width*2-1,1-p.y/this.height*2),this.camera);const hit=this.raycaster.intersectObjects(this.pickMeshes||[],false)[0];this.lastPickSource=hit?'raycast':'fallback';this.lastGpuPickedId=hit?.object.userData.memberId||null;return this.lastGpuPickedId||super.pick(p);}
 destroy(){document.removeEventListener('visibilitychange',this.visibilityHandler);this.clearModel();for(const mat of [this.steel,this.selectedSteel,this.saddleSteel,this.edgesMaterial,this.selectedEdges])mat?.dispose();this.key?.shadow.dispose();this.renderer?.dispose();this.glCanvas?.remove();super.destroy();}
}
