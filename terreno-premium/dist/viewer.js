/* Local-only, dependency-free orthographic 3D survey viewer.
 * Coordinates and onMove deltas use millimetres; Z is vertical.
 * onMove is a preview-only translation in the current view plane. The caller
 * owns validation, shared-node handling, history and persistence.
 */
const TAU = Math.PI * 2;
const DRAW_PLANES = {front:{angles:[0,0],axis:1},top:{angles:[0,Math.PI/2],axis:2},side:{angles:[Math.PI/2,0],axis:0}};
const COLORS = { estimated: '#d99828', measured: '#269b9b', confirmed: '#3b9368' };
const add = (a,b) => a.map((n,i) => n+b[i]);
const sub = (a,b) => a.map((n,i) => n-b[i]);
const mul = (a,s) => a.map(n => n*s);
const dot = (a,b) => a.reduce((s,n,i) => s+n*b[i],0);
const cross = (a,b) => [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const norm = a => Math.hypot(...a);
const unit = a => mul(a,1/(norm(a)||1));
const clamp = (x,a,b) => Math.max(a,Math.min(b,x));
const pos = n => [n.x,n.y,n.z];

function segmentDistance(p,a,b) {
  const dx=b.x-a.x,dy=b.y-a.y;
  const t=clamp(((p.x-a.x)*dx+(p.y-a.y)*dy)/(dx*dx+dy*dy||1),0,1);
  return Math.hypot(p.x-a.x-t*dx,p.y-a.y-t*dy);
}
function inside(p,poly) {
  let yes=false;
  for(let i=0,j=poly.length-1;i<poly.length;j=i++) {
    const a=poly[i],b=poly[j];
    if((a.y>p.y)!==(b.y>p.y) && p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x) yes=!yes;
  }
  return yes;
}
function shade(hex,k) {
  const n=parseInt(hex.slice(1),16);
  return `rgb(${[n>>16,(n>>8)&255,n&255].map(v=>Math.round(clamp(v*k,0,255))).join(',')})`;
}
function rect(w,h,x=0,y=0) {
  return [[x-w/2,y-h/2],[x+w/2,y-h/2],[x+w/2,y+h/2],[x-w/2,y+h/2]];
}

/** StructureViewer: no network, camera, Bluetooth, or device permissions. */
export class StructureViewer {
  constructor(canvas,{onSelect=()=>{},onMove=()=>{},onCameraChange=()=>{},onOrientationRequest=()=>{},onDraftMember=()=>{}}={}) {
    if(!canvas?.getContext) throw new Error('Se necesita un canvas para el visor.');
    this.canvas=canvas; this.ctx=canvas.getContext('2d');
    this.onSelect=onSelect; this.onMove=onMove;this.onDraftMember=onDraftMember;
    this.onCameraChange=onCameraChange;this.onOrientationRequest=onOrientationRequest;this.initializing=true;
    this.project={nodes:[],members:[]}; this.selectedId=null; this.mode='orbit';this.labelMode='selected';
    this.yaw=Math.PI/4; this.pitch=Math.PI/5; this.center=[0,0,0]; this.scale=0.1;this.baseScale=0.1;
    this.zoomFactor=1; this.pointers=new Map(); this.hits=[]; this.labelHits=[];
    this.width=0; this.height=0; this.frame=0; this.gesture=null; this.preview=null;this.multiGesture=null;
    this.drawingPlane=null;this.drawingOffset=0;this.draftPreview=null;
    this.canvas.style.touchAction='none'; this.canvas.style.userSelect='none';
    if(!this.canvas.hasAttribute('tabindex')) this.canvas.tabIndex=0;
    if(!this.canvas.hasAttribute('aria-label')) this.canvas.setAttribute('aria-label','Vista tridimensional de la estructura. Un dedo gira; dos dedos desplazan la vista y pellizcan para acercar; un toque corto con dos dedos abre la orientación.');
    this.handlers={
      pointerdown:e=>this.pointerDown(e), pointermove:e=>this.pointerMove(e),
      pointerup:e=>this.pointerUp(e), pointercancel:e=>this.pointerCancel(e),
      wheel:e=>{e.preventDefault();this.zoom(-Math.sign(e.deltaY)*0.13);},
      keydown:e=>this.keyDown(e),
    };
    Object.entries(this.handlers).forEach(([name,fn])=>canvas.addEventListener(name,fn,{passive:false}));
    this.resizeObserver=typeof ResizeObserver!=='undefined'?new ResizeObserver(()=>this.resize()):null;
    this.resizeObserver?.observe(canvas);
    this.resizeHandler=()=>this.resize();
    if(!this.resizeObserver) window.addEventListener('resize',this.resizeHandler);
    this.resize();this.initializing=false;
  }
  setData(project,selectedId=null) {
    const changed=this.project?.id!==project?.id;
    this.project=project||{nodes:[],members:[]};this.selectedId=selectedId;
    this.preview=null;this.draftPreview=null;
    if(this.gesture)this.gesture.cancelled=true;
    if(changed||!this.fitted) this.fit(); else this.drawSoon();
  }
  setMode(mode) {
    if(mode==='draw'&&!this.drawingPlane)return false;
    this.mode=mode==='move'?'move':mode==='draw'?'draw':'orbit'; this.preview=null;this.draftPreview=null; this.gesture=null;
    if(this.multiGesture)this.multiGesture.cancelled=true;
    if(this.mode==='draw') {
      const angles=DRAW_PLANES[this.drawingPlane].angles;
      if(this.yaw!==angles[0]||this.pitch!==angles[1]){[this.yaw,this.pitch]=angles;this.fit();}
    }
    this.canvas.style.cursor=this.mode==='draw'?'crosshair':this.mode==='move'?'move':'grab';this.drawSoon();return true;
  }
  /** An explicit coordinate plane: offset is a manually chosen millimetre value. */
  setDrawingPlane(plane,offset=0) {
    if(!Object.hasOwn(DRAW_PLANES,plane)||typeof offset!=='number'||!Number.isFinite(offset)||Math.abs(offset)>1e8)return false;
    this.drawingPlane=plane;this.drawingOffset=offset;this.draftPreview=null;this.preview=null;
    if(this.gesture)this.gesture.cancelled=true;
    if(this.multiGesture)this.multiGesture.cancelled=true;
    [this.yaw,this.pitch]=DRAW_PLANES[plane].angles;this.fit();return true;
  }
  drawingPoint(p,snap=true) {
    if(!this.drawingPlane||!Number.isFinite(p?.x)||!Number.isFinite(p?.y)||p.x<0||p.y<0||p.x>this.width||p.y>this.height||!Number.isFinite(this.scale)||this.scale<=0)return null;
    const definition=DRAW_PLANES[this.drawingPlane],axes=this.cameraAxes(),axis=definition.axis;
    // Never project an oblique view onto an unspecified depth. Drawing requires
    // the camera normal to be aligned with the explicitly selected plane.
    if(Math.abs(axes.depth[axis])<1-1e-8)return null;
    const base=add(this.center,add(mul(axes.right,(p.x-this.width/2)/this.scale),mul(axes.down,(p.y-this.height/2)/this.scale)));
    const world=add(base,mul(axes.depth,(this.drawingOffset-base[axis])/axes.depth[axis]));
    world[axis]=this.drawingOffset;
    if(world.some(n=>!Number.isFinite(n)||Math.abs(n)>1e8))return null;
    if(snap) {
      const candidates=[];
      for(const node of this.project.nodes||[]) {
        const coordinates=pos(node);
        if(coordinates.some(n=>!Number.isFinite(n))||Math.abs(coordinates[axis]-this.drawingOffset)>1e-6)continue;
        const screen=this.projectPoint(coordinates),distance=Math.hypot(screen.x-p.x,screen.y-p.y);
        if(distance<=12)candidates.push({node,distance});
      }
      candidates.sort((a,b)=>a.distance-b.distance);
      // Tied/overlapping nodes are deliberately not joined automatically.
      if(candidates.length&&(!candidates[1]||candidates[1].distance-candidates[0].distance>=1)) {
        const n=candidates[0].node;return {x:n.x,y:n.y,z:n.z,nodeId:n.id};
      }
    }
    return {x:world[0],y:world[1],z:world[2]};
  }
  setLabelMode(mode) {
    if(mode!=='all'&&mode!=='selected')return false;
    this.labelMode=mode;this.drawSoon();return true;
  }
  getCameraState() {
    return {yaw:this.yaw,pitch:this.pitch,center:[...this.center],zoomFactor:this.zoomFactor,scale:this.scale,baseScale:this.baseScale};
  }
  notifyCameraChange() {if(!this.initializing)this.onCameraChange(this.getCameraState());}
  // Azimuth control NEVER fits, resets, changes inclination or edits a node.
  rotateAzimuth(deltaRadians) {
    if(this.mode==='draw')return false;
    if(typeof deltaRadians!=='number'||!Number.isFinite(deltaRadians)||!Number.isFinite(this.yaw+deltaRadians))return false;
    if(deltaRadians===0)return true;
    this.yaw+=deltaRadians;this.drawSoon();this.notifyCameraChange();return true;
  }
  cameraAxes() {
    const cy=Math.cos(this.yaw),sy=Math.sin(this.yaw),cp=Math.cos(this.pitch),sp=Math.sin(this.pitch);
    return {right:[cy,-sy,0],down:[sp*sy,sp*cy,-cp],depth:[cp*sy,cp*cy,sp]};
  }
  projectPoint(p) {
    const q=sub(p,this.center),a=this.cameraAxes();
    return {x:this.width/2+dot(q,a.right)*this.scale,y:this.height/2+dot(q,a.down)*this.scale,d:dot(q,a.depth)};
  }
  resize() {
    const bounds=this.canvas.getBoundingClientRect();
    const w=Math.max(1,bounds.width),h=Math.max(1,bounds.height);
    if(w===this.width&&h===this.height) return;
    this.width=w;this.height=h;
    const dpr=Math.min(window.devicePixelRatio||1,2.5);
    this.canvas.width=Math.round(w*dpr);this.canvas.height=Math.round(h*dpr);
    this.ctx.setTransform(dpr,0,0,dpr,0,0);
    this.fit(false);
  }
  fit(resetZoom=true) {
    if(this.mode==='draw'&&this.gesture){this.gesture.cancelled=true;this.draftPreview=null;}
    const nodes=this.project.nodes||[];
    if(resetZoom) this.zoomFactor=1;
    if(!nodes.length){this.center=[0,0,0];this.baseScale=0.1;this.scale=this.baseScale*this.zoomFactor;this.drawSoon();this.notifyCameraChange();return;}
    const points=nodes.map(pos);
    for(const f of this.project.features||[])if(f.type==='saddle-preview') {
      const rise=f.radius-Math.sqrt(Math.max(0,f.radius*f.radius-f.width*f.width/4));
      for(const x of [f.x-f.length/2,f.x+f.length/2])for(const y of [f.y-f.width/2,f.y+f.width/2])points.push([x,y,f.z+rise]);
    }
    const min=[0,1,2].map(i=>Math.min(...points.map(p=>p[i])));
    const max=[0,1,2].map(i=>Math.max(...points.map(p=>p[i])));
    this.center=min.map((v,i)=>(v+max[i])/2);
    const section=Math.max(20,...(this.project.members||[]).map(m=>Math.max(m.profile?.width||0,m.profile?.height||0)));
    const axes=this.cameraAxes();
    const xs=points.map(p=>dot(sub(p,this.center),axes.right));
    const ys=points.map(p=>dot(sub(p,this.center),axes.down));
    const spanX=Math.max(section*2,Math.max(...xs)-Math.min(...xs)+section*1.8);
    const spanY=Math.max(section*2,Math.max(...ys)-Math.min(...ys)+section*1.8);
    // Centre the projected bounds as well as the world bounds.
    this.center=add(this.center,add(mul(axes.right,(Math.max(...xs)+Math.min(...xs))/2),mul(axes.down,(Math.max(...ys)+Math.min(...ys))/2)));
    this.baseScale=Math.min(Math.max(60,this.width-96)/spanX,Math.max(60,this.height-110)/spanY);
    this.scale=this.baseScale*this.zoomFactor;this.fitted=true;this.drawSoon();this.notifyCameraChange();
  }
  preset(name) {
    const presets={iso:[Math.PI/4,Math.PI/5],front:[0,0],top:[0,Math.PI/2],side:[Math.PI/2,0]};
    if(!Object.hasOwn(presets,name))return false;
    if(this.mode==='draw'&&name!==this.drawingPlane)return false;
    [this.yaw,this.pitch]=presets[name];
    this.preview=null;this.fit();return true;
  }
  zoom(delta) {
    this.zoomFactor=clamp(this.zoomFactor*Math.exp(Number(delta)||0),0.15,12);
    this.scale=(this.baseScale||0.1)*this.zoomFactor;this.drawSoon();this.notifyCameraChange();
  }
  pointFromEvent(e) {const r=this.canvas.getBoundingClientRect();return {x:e.clientX-r.left,y:e.clientY-r.top};}
  eventTime(e) {return Number.isFinite(e.timeStamp)?e.timeStamp:globalThis.performance?.now?.()??Date.now();}
  trackMultiPoint(id,p) {
    const m=this.multiGesture,start=m?.starts.get(id);
    if(!m||!start)return;
    m.current.set(id,{...p});
    m.travel=Math.max(m.travel,Math.hypot(p.x-start.x,p.y-start.y));
    if(m.travel>=8)m.tap=false;
    const ps=[...m.current.values()];
    if(Math.abs(Math.hypot(ps[0].x-ps[1].x,ps[0].y-ps[1].y)-m.initialDistance)>=8)m.tap=false;
  }
  pointerDown(e) {
    if(e.button!==undefined&&e.button!==0) return;
    e.preventDefault();this.canvas.focus({preventScroll:true});
    const p=this.pointFromEvent(e);this.pointers.set(e.pointerId,p);
    try{this.canvas.setPointerCapture(e.pointerId);}catch{}
    if(this.pointers.size===1) {
      this.multiGesture=null;
      this.gesture={start:p,last:p,yaw:this.yaw,pitch:this.pitch,travel:0,memberId:this.selectedId,startTime:this.eventTime(e)};
      if(this.mode==='draw') {
        this.gesture.draftStart=this.drawingPoint(p);
        this.gesture.cancelled=!this.gesture.draftStart;
        this.draftPreview=this.gesture.draftStart?{start:this.gesture.draftStart,end:this.gesture.draftStart}:null;
        this.drawSoon();
      }
      this.canvas.style.cursor=this.mode==='draw'?'crosshair':this.mode==='move'?'move':'grabbing';
    } else if(this.pointers.size===2&&!this.multiGesture) {
      const ps=[...this.pointers.values()],d=Math.hypot(ps[0].x-ps[1].x,ps[0].y-ps[1].y),g=this.gesture;
      this.multiGesture={starts:new Map([...this.pointers].map(([id,p])=>[id,{...p}])),current:new Map([...this.pointers].map(([id,p])=>[id,{...p}])),startTime:g?.startTime??this.eventTime(e),travel:g?.travel||0,tap:!!g&&!g.cancelled&&g.travel<5,cancelled:false,active:false,
        initialDistance:d,lastDistance:d,lastMid:{x:(ps[0].x+ps[1].x)/2,y:(ps[0].y+ps[1].y)/2}};
      this.preview=null;this.draftPreview=null;if(g)g.cancelled=true;this.drawSoon();
    } else {
      // A third finger invalidates the entire gesture, including after it lifts.
      if(this.multiGesture)this.multiGesture.cancelled=true;
      this.preview=null;this.draftPreview=null;if(this.gesture)this.gesture.cancelled=true;this.drawSoon();
    }
  }
  pointerMove(e) {
    if(!this.pointers.has(e.pointerId)) return;
    e.preventDefault();const p=this.pointFromEvent(e);this.pointers.set(e.pointerId,p);
    if(this.multiGesture) {
      const m=this.multiGesture;this.trackMultiPoint(e.pointerId,p);
      if(m.cancelled||this.pointers.size!==2)return;
      const ps=[...this.pointers.values()],d=Math.hypot(ps[0].x-ps[1].x,ps[0].y-ps[1].y),mid={x:(ps[0].x+ps[1].x)/2,y:(ps[0].y+ps[1].y)/2};
      if(Math.abs(d-m.initialDistance)>=8)m.tap=false;
      if(!m.active&&m.tap)return; // Tap slop: jitter neither pans nor zooms.
      m.active=true;m.tap=false;
      const oldScale=this.scale,axes=this.cameraAxes();
      if(m.lastDistance>0&&d>0)this.zoomFactor=clamp(this.zoomFactor*d/m.lastDistance,.15,12);
      this.scale=(this.baseScale||.1)*this.zoomFactor;
      // Keep the world point under the previous midpoint under the new one.
      // This combines screen-plane pan and midpoint-anchored pinch zoom.
      const dx=(m.lastMid.x-this.width/2)/oldScale-(mid.x-this.width/2)/this.scale;
      const dy=(m.lastMid.y-this.height/2)/oldScale-(mid.y-this.height/2)/this.scale;
      this.center=add(this.center,add(mul(axes.right,dx),mul(axes.down,dy)));
      m.lastDistance=d;m.lastMid=mid;this.preview=null;
      this.drawSoon();this.notifyCameraChange();return;
    }
    const g=this.gesture;if(!g||g.cancelled)return;
    const dx=p.x-g.start.x,dy=p.y-g.start.y;g.travel=Math.max(g.travel,Math.hypot(dx,dy));g.last=p;
    if(this.mode==='draw') {
      const end=this.drawingPoint(p);this.draftPreview=end&&g.draftStart?{start:g.draftStart,end}:null;
      this.drawSoon();return;
    }
    if(g.travel<5)return;
    if(this.mode==='move'&&g.memberId) {
      const a=this.cameraAxes();const delta=add(mul(a.right,dx/this.scale),mul(a.down,dy/this.scale));
      this.preview={memberId:g.memberId,delta};
    } else if(this.mode==='orbit') {
      this.yaw=g.yaw+dx*0.009;this.pitch=clamp(g.pitch+dy*0.008,-Math.PI/2+0.01,Math.PI/2-0.01);
      this.notifyCameraChange();
    }
    this.drawSoon();
  }
  pointerUp(e) {
    if(!this.pointers.has(e.pointerId))return;
    const g=this.gesture,p=this.pointFromEvent(e),multi=this.multiGesture;
    this.trackMultiPoint(e.pointerId,p);this.pointers.delete(e.pointerId);
    try{this.canvas.releasePointerCapture(e.pointerId);}catch{}
    if(this.pointers.size)return;
    this.canvas.style.cursor=this.mode==='draw'?'crosshair':this.mode==='move'?'move':'grab';this.gesture=null;this.multiGesture=null;
    if(multi) {
      this.preview=null;this.draftPreview=null;this.drawSoon();
      const duration=this.eventTime(e)-multi.startTime;
      if(!multi.cancelled&&multi.tap&&!multi.active&&multi.travel<8&&duration>=0&&duration<=280)this.onOrientationRequest();
      return;
    }
    if(!g||g.cancelled){this.preview=null;this.draftPreview=null;this.drawSoon();return;}
    if(this.mode==='draw') {
      const start=g.draftStart,end=this.drawingPoint(p);this.draftPreview=null;this.preview=null;this.drawSoon();
      if(start&&end&&Math.hypot(p.x-g.start.x,p.y-g.start.y)>=8&&norm(sub(pos(end),pos(start)))>=.001) {
        this.onDraftMember({start:{...start},end:{...end},plane:this.drawingPlane,offset:this.drawingOffset,source:'screen-draft',requiresMeasurement:true});
      }
      return;
    }
    g.travel=Math.max(g.travel,Math.hypot(p.x-g.start.x,p.y-g.start.y));
    if(g.travel<5){this.preview=null;this.onSelect(this.pick(p));}
    else if(this.preview) {
      const {memberId,delta}=this.preview;this.preview=null;
      const [dx,dy,dz]=delta.map(v=>Math.round(v*10)/10||0);
      if(dx||dy||dz)this.onMove({memberId,dx,dy,dz});
    }
    this.drawSoon();
  }
  pointerCancel(e) {
    this.pointers.delete(e.pointerId);this.preview=null;this.draftPreview=null;
    if(this.gesture)this.gesture.cancelled=true;
    if(this.multiGesture)this.multiGesture.cancelled=true;
    if(!this.pointers.size){this.gesture=null;this.multiGesture=null;this.canvas.style.cursor=this.mode==='draw'?'crosshair':this.mode==='move'?'move':'grab';}
    try{this.canvas.releasePointerCapture(e.pointerId);}catch{}
    this.drawSoon();
  }
  keyDown(e) {
    if(this.mode==='draw'&&e.key==='Escape'){e.preventDefault();if(this.gesture)this.gesture.cancelled=true;this.draftPreview=null;this.drawSoon();return;}
    const changes={ArrowLeft:[-.12,0],ArrowRight:[.12,0],ArrowUp:[0,-.1],ArrowDown:[0,.1]};
    if(this.mode==='draw'&&changes[e.key]){e.preventDefault();return;}
    if(changes[e.key]){e.preventDefault();this.yaw+=changes[e.key][0];this.pitch=clamp(this.pitch+changes[e.key][1],-1.56,1.56);this.drawSoon();this.notifyCameraChange();}
    else if(e.key==='+'||e.key==='='){e.preventDefault();this.zoom(.15);}
    else if(e.key==='-'){e.preventDefault();this.zoom(-.15);}
    else if(e.key==='Home'){e.preventDefault();this.fit();}
  }
  pick(p) {
    for(let i=this.labelHits.length-1;i>=0;i--){const h=this.labelHits[i];if(p.x>=h.x&&p.x<=h.x+h.w&&p.y>=h.y&&p.y<=h.y+h.h)return h.id;}
    for(let i=this.hits.length-1;i>=0;i--) if(inside(p,this.hits[i].poly))return this.hits[i].id;
    let nearest=null,dist=18;
    for(const m of this.project.members||[]){const ends=this.ends(m);if(!ends)continue;const d=segmentDistance(p,this.projectPoint(ends[0]),this.projectPoint(ends[1]));if(d<dist){dist=d;nearest=m.id;}}
    return nearest;
  }
  ends(m) {
    const a=this.project.nodes.find(n=>n.id===m.startNode),b=this.project.nodes.find(n=>n.id===m.endNode);
    if(!a||!b)return null;
    const offset=this.preview?.memberId===m.id?this.preview.delta:[0,0,0];return [add(pos(a),offset),add(pos(b),offset)];
  }
  geometry(m) {
    const ends=this.ends(m);if(!ends)return [];
    const [a,b]=ends,t=unit(sub(b,a));if(norm(sub(b,a))<.001)return [];
    let u=unit(cross(Math.abs(t[2])>.97?[0,1,0]:[0,0,1],t)),v=unit(cross(t,u));
    const roll=(Number(m.roll)||0)*Math.PI/180;
    [u,v]=[add(mul(u,Math.cos(roll)),mul(v,Math.sin(roll))),add(mul(v,Math.cos(roll)),mul(u,-Math.sin(roll)))];
    const p=m.profile||{},w=Math.max(.1,Number(p.width)||50),h=Math.max(.1,Number(p.height)||50),tw=clamp(Number(p.tw)||3,.1,Math.min(w,h)/2),tf=clamp(Number(p.tf)||tw,.1,h/2);
    let polygons;
    if(p.type==='i') polygons=[rect(w,tf,0,(h-tf)/2),rect(w,tf,0,-(h-tf)/2),rect(tw,Math.max(.1,h-2*tf))];
    else if(p.type==='channel') polygons=[rect(w,tf,0,(h-tf)/2),rect(w,tf,0,-(h-tf)/2),rect(tw,Math.max(.1,h-2*tf),-(w-tw)/2)];
    else if(p.type==='rhs') polygons=[rect(w,tf,0,(h-tf)/2),rect(w,tf,0,-(h-tf)/2),rect(tw,Math.max(.1,h-2*tf),(w-tw)/2),rect(tw,Math.max(.1,h-2*tf),-(w-tw)/2)];
    else if(p.type==='round') {
      const outer=Array.from({length:16},(_,i)=>[Math.cos(i*TAU/16)*w/2,Math.sin(i*TAU/16)*h/2]);
      const inner=Array.from({length:16},(_,i)=>[Math.cos(i*TAU/16)*Math.max(.1,w/2-tw),Math.sin(i*TAU/16)*Math.max(.1,h/2-tw)]);
      polygons=outer.map((q,i)=>[q,outer[(i+1)%16],inner[(i+1)%16],inner[i]]);
    } else polygons=[rect(w,h)];
    const faces=[];
    const at=(end,q)=>add(end,add(mul(u,q[0]),mul(v,q[1])));
    for(const poly of polygons) {
      const front=poly.map(q=>at(a,q)),back=poly.map(q=>at(b,q));
      faces.push(front.slice().reverse(),back);
      for(let i=0;i<poly.length;i++){const j=(i+1)%poly.length;faces.push([front[i],front[j],back[j],back[i]]);}
    }
    return faces;
  }
  drawSoon() {if(!this.frame)this.frame=requestAnimationFrame(()=>{this.frame=0;this.draw();});}
  path(poly) {const c=this.ctx;c.beginPath();poly.forEach((p,i)=>i?c.lineTo(p.x,p.y):c.moveTo(p.x,p.y));c.closePath();}
  drawGrid() {
    const c=this.ctx,ns=this.project.nodes||[];
    if(!ns.length)return;
    const minX=Math.min(0,...ns.map(n=>n.x)),maxX=Math.max(0,...ns.map(n=>n.x)),minY=Math.min(0,...ns.map(n=>n.y)),maxY=Math.max(0,...ns.map(n=>n.y));
    const extent=Math.max(maxX-minX,maxY-minY,500);
    const exp=10**Math.floor(Math.log10(extent/7)),step=Math.ceil(extent/7/exp)*exp;
    const x1=Math.floor(minX/step)*step-step,x2=Math.ceil(maxX/step)*step+step,y1=Math.floor(minY/step)*step-step,y2=Math.ceil(maxY/step)*step+step;
    const z=Math.min(0,...ns.map(n=>n.z));
    c.save();c.lineWidth=1;c.strokeStyle='#424b53';
    const line=(a,b)=>{a=this.projectPoint(a);b=this.projectPoint(b);c.beginPath();c.moveTo(a.x,a.y);c.lineTo(b.x,b.y);c.stroke();};
    for(let x=x1;x<=x2+step*.1;x+=step)line([x,y1,z],[x,y2,z]);
    for(let y=y1;y<=y2+step*.1;y+=step)line([x1,y,z],[x2,y,z]);
    c.restore();this.gridStep=step;
  }
  draw() {
    const c=this.ctx,w=this.width,h=this.height;if(!c||!w||!h)return;
    c.clearRect(0,0,w,h);c.fillStyle='#242d35';c.fillRect(0,0,w,h);this.hits=[];this.labelHits=[];
    if(!(this.project.members||[]).length) {
      if(this.mode==='draw'){this.drawDrawingPreview();this.drawAxes();return;}
      c.fillStyle='#526575';c.font='600 17px system-ui, sans-serif';c.textAlign='center';c.fillText('Tu estructura empieza aquí',w/2,h/2-9);
      c.fillStyle='#718391';c.font='13px system-ui, sans-serif';c.fillText('Agrega una viga y define sus extremos.',w/2,h/2+17);c.textAlign='left';this.drawAxes();return;
    }
    this.drawGrid();
    const faces=[];
    for(const m of this.project.members||[]) {
      for(const verts of this.geometry(m)) {
        const poly=verts.map(p=>this.projectPoint(p)),normal=unit(cross(sub(verts[1],verts[0]),sub(verts[2],verts[0])));
        const lighting=.82+.22*Math.abs(dot(normal,unit([-.4,-.6,1])));
        faces.push({poly,d:poly.reduce((s,p)=>s+p.d,0)/poly.length,id:m.id,color:m.id===this.selectedId?'#eab246':(m.status==='estimated'?'#a4afb8':COLORS[m.status]||'#a4afb8'),lighting});
      }
    }
    // Semantic curved preview: triangulation is for display only, not STEP or analysis.
    for(const f of this.project.features||[])if(f.type==='saddle-preview') {
      const half=f.width/2,segments=24,points=[];
      for(let i=0;i<=segments;i++){const y=-half+f.width*i/segments;points.push([f.x-f.length/2,f.y+y,f.z+f.radius-Math.sqrt(Math.max(0,f.radius*f.radius-y*y))]);}
      for(let i=0;i<segments;i++) {
        const a=points[i],b=points[i+1],c=[b[0]+f.length,b[1],b[2]],d=[a[0]+f.length,a[1],a[2]];
        for(const verts of [[a,b,c,d],[a,d,[d[0],d[1],d[2]-f.thickness],[a[0],a[1],a[2]-f.thickness]]]) {
          const poly=verts.map(p=>this.projectPoint(p));faces.push({poly,d:poly.reduce((s,p)=>s+p.d,0)/poly.length,id:null,color:'#8c9ba8',lighting:1});
        }
      }
    }
    faces.sort((a,b)=>a.d-b.d);
    for(const face of faces) {
      this.path(face.poly);c.fillStyle=shade(face.color,face.lighting);c.fill();
      c.strokeStyle=face.id!==null&&face.id===this.selectedId?'#ffcb61':shade(face.color,.72);c.lineWidth=face.id!==null&&face.id===this.selectedId?1.2:.55;c.stroke();
      if(face.id)this.hits.push(face);
    }
    // A centreline makes very slender members selectable without inflating geometry.
    for(const m of this.project.members||[]) {
      const ends=this.ends(m);if(!ends)continue;
      const a=this.projectPoint(ends[0]),b=this.projectPoint(ends[1]);
      if(m.id===this.selectedId) {
        c.strokeStyle='#173c7a';c.lineWidth=1;c.setLineDash([4,4]);c.beginPath();c.moveTo(a.x,a.y);c.lineTo(b.x,b.y);c.stroke();c.setLineDash([]);
        for(const p of [a,b]){c.beginPath();c.arc(p.x,p.y,4.8,0,TAU);c.fillStyle='#fff';c.fill();c.lineWidth=2;c.strokeStyle='#245ab4';c.stroke();}
      }
      if(this.labelMode==='all'||m.id===this.selectedId)this.drawLabel(m,(a.x+b.x)/2,(a.y+b.y)/2);
    }
    this.drawAxes();
    this.drawDrawingPreview();
    if(this.preview){c.fillStyle='#154c91';c.font='600 12px system-ui, sans-serif';c.fillText('Vista previa · suelta para aplicar',14,24);}
  }
  drawDrawingPreview() {
    if(this.mode!=='draw'||!this.draftPreview)return;
    const c=this.ctx,start=this.draftPreview.start,end=this.draftPreview.end,a=this.projectPoint(pos(start)),b=this.projectPoint(pos(end));
    c.save();c.strokeStyle='#733baf';c.lineWidth=2.5;c.setLineDash([7,4]);c.beginPath();c.moveTo(a.x,a.y);c.lineTo(b.x,b.y);c.stroke();c.setLineDash([]);
    for(const [point,screen] of [[start,a],[end,b]]) {
      c.beginPath();c.arc(screen.x,screen.y,point.nodeId?7:5,0,TAU);c.fillStyle=point.nodeId?'#19764c':'#fff';c.fill();c.strokeStyle=point.nodeId?'#125c3a':'#733baf';c.lineWidth=2;c.stroke();
      if(point.nodeId){c.fillStyle='#125c3a';c.font='600 11px system-ui, sans-serif';c.fillText(`Ajuste a nodo ${String(point.nodeId).slice(0,24)}`,clamp(screen.x+10,4,Math.max(4,this.width-180)),clamp(screen.y-10,16,this.height-10));}
    }
    c.fillStyle='#593285';c.font='600 12px system-ui, sans-serif';c.fillText('Borrador · confirmar longitud y perfil',14,24);c.restore();
  }
  drawLabel(m,x,y) {
    const c=this.ctx,selected=m.id===this.selectedId;
    c.font=`${selected?'700':'600'} 11px system-ui, sans-serif`;
    const raw=String(m.label||m.id),label=raw.length>22?raw.slice(0,20)+'…':raw;
    const w=c.measureText(label).width+16,h=23;
    x=clamp(x-w/2,5,Math.max(5,this.width-w-5));y=clamp(y-30,4,Math.max(4,this.height-h-28));
    c.fillStyle=selected?'#174f9b':'rgba(255,255,255,.94)';c.strokeStyle=selected?'#174f9b':'#cfdbe5';c.lineWidth=1;
    c.beginPath();if(c.roundRect)c.roundRect(x,y,w,h,6);else c.rect(x,y,w,h);c.fill();c.stroke();
    c.fillStyle=selected?'#fff':'#314858';c.textBaseline='middle';c.fillText(label,x+8,y+h/2+.5);c.textBaseline='alphabetic';
    this.labelHits.push({id:m.id,x,y,w,h});
  }
  drawAxes() {
    const c=this.ctx,a=this.cameraAxes(),origin={x:35,y:this.height-36};
    c.save();c.font='600 10px system-ui, sans-serif';c.lineWidth=2;
    for(const [v,name,color] of [[[1,0,0],'X','#bc6165'],[[0,1,0],'Y','#438f77'],[[0,0,1],'Z','#527dc1']]) {
      const x=origin.x+dot(v,a.right)*22,y=origin.y+dot(v,a.down)*22;
      c.strokeStyle=color;c.fillStyle=color;c.beginPath();c.moveTo(origin.x,origin.y);c.lineTo(x,y);c.stroke();c.fillText(name,x+3,y-3);
    }
    c.restore();
  }
  destroy() {
    if(this.frame)cancelAnimationFrame(this.frame);
    this.resizeObserver?.disconnect();
    if(!this.resizeObserver)window.removeEventListener('resize',this.resizeHandler);
    Object.entries(this.handlers).forEach(([name,fn])=>this.canvas.removeEventListener(name,fn));
    this.pointers.clear();this.multiGesture=null;this.gesture=null;this.preview=null;this.draftPreview=null;
  }
}
