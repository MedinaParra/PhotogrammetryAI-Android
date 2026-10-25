/**
 * Offline, deterministic cross-section silhouette assistance. No neural model,
 * catalogue lookup, physical measurement, network call or hidden fallback.
 *
 * The caller MUST ask for an isolated, visible transverse section, photographed
 * approximately front-on against a uniform contrasting background, with margin.
 * A silhouette cannot prove material, solidness, wall thickness or that an image
 * depicts a section rather than another object of the same shape. Scores below
 * are template intersection-over-union, NOT probabilities of correct identity.
 */
const GRID = 64;
const TYPES = ['rhs', 'i', 'round', 'rect'];
const LABELS = {
  rhs: 'Rectangular hueco aparente',
  i: 'Forma I / H aparente',
  round: 'Circular aparente',
  rect: 'Rectangular aparente',
};
const quantile = (values, q) => {
  const sorted = [...values].sort((a,b)=>a-b);
  return sorted[Math.min(sorted.length-1, Math.floor(q*(sorted.length-1)))];
};
const rounded = value => Math.round(value*1000)/1000;
const unknown = (reason, metrics={}) => ({status:'unidentifiable', candidates:[], reason, metrics});

function connectedComponents(mask, width, height) {
  const labels = new Int32Array(mask.length), queue = new Int32Array(mask.length);
  const components=[];
  for (let start=0;start<mask.length;start++) {
    if (!mask[start] || labels[start]) continue;
    const label=components.length+1;
    let begin=0,end=1,minX=width,minY=height,maxX=0,maxY=0;
    labels[start]=label;queue[0]=start;
    while(begin<end) {
      const at=queue[begin++],x=at%width,y=Math.floor(at/width);
      minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
      for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++) {
        if ((!dx&&!dy) || x+dx<0 || x+dx>=width || y+dy<0 || y+dy>=height) continue;
        const next=(y+dy)*width+x+dx;
        if(mask[next]&&!labels[next]) {labels[next]=label;queue[end++]=next;}
      }
    }
    components.push({label,area:end,minX,minY,maxX,maxY});
  }
  components.sort((a,b)=>b.area-a.area);
  return {labels,components};
}

function enclosedHoles(mask,width,height,box) {
  const visited=new Uint8Array(mask.length),queue=new Int32Array(mask.length);
  const holes=[];
  for(let y=box.minY;y<=box.maxY;y++)for(let x=box.minX;x<=box.maxX;x++) {
    const start=y*width+x;
    if(mask[start]||visited[start])continue;
    let begin=0,end=1,edge=false,sumX=0,sumY=0;
    visited[start]=1;queue[0]=start;
    while(begin<end) {
      const at=queue[begin++],px=at%width,py=Math.floor(at/width);
      sumX+=px;sumY+=py;
      if(px===box.minX||px===box.maxX||py===box.minY||py===box.maxY)edge=true;
      for(const [dx,dy] of [[-1,0],[1,0],[0,-1],[0,1]]) {
        const nx=px+dx,ny=py+dy;
        if(nx<box.minX||nx>box.maxX||ny<box.minY||ny>box.maxY)continue;
        const next=ny*width+nx;
        if(!mask[next]&&!visited[next]){visited[next]=1;queue[end++]=next;}
      }
    }
    if(!edge&&end>=Math.max(6,box.area*.005))holes.push({area:end,x:sumX/end,y:sumY/end});
  }
  return holes.sort((a,b)=>b.area-a.area);
}

/** Rotate the isolated foreground and sample its bounding box, retaining aspect. */
function normalizedMask(mask,width,height,points,angle) {
  const c=Math.cos(angle),s=Math.sin(angle);
  let minX=Infinity,minY=Infinity,maxX=-Infinity,maxY=-Infinity;
  for(let i=0;i<points.length;i+=2) {
    const x=points[i],y=points[i+1],rx=x*c+y*s,ry=-x*s+y*c;
    minX=Math.min(minX,rx);maxX=Math.max(maxX,rx);minY=Math.min(minY,ry);maxY=Math.max(maxY,ry);
  }
  const w=maxX-minX+1,h=maxY-minY+1,output=new Uint8Array(GRID*GRID);
  for(let y=0;y<GRID;y++)for(let x=0;x<GRID;x++) {
    const rx=minX-.5+(x+.5)*w/GRID,ry=minY-.5+(y+.5)*h/GRID;
    const ox=Math.round(rx*c-ry*s),oy=Math.round(rx*s+ry*c);
    if(ox>=0&&ox<width&&oy>=0&&oy<height)output[y*GRID+x]=mask[oy*width+ox];
  }
  return {mask:output,aspect:w/h};
}

function fit(mask, shape) {
  let intersection=0,union=0;
  for(let i=0;i<mask.length;i++) {
    const expected=shape[i];
    if(mask[i]&&expected)intersection++;
    if(mask[i]||expected)union++;
  }
  return union ? intersection/union : 0;
}
function template(predicate) {
  const result=new Uint8Array(GRID*GRID);
  for(let y=0;y<GRID;y++)for(let x=0;x<GRID;x++)result[y*GRID+x]=predicate((x+.5)/GRID,(y+.5)/GRID)?1:0;
  return result;
}
const solidRectangle=template(()=>true);
const solidCircle=template((x,y)=>(x-.5)**2+(y-.5)**2<=.25);
const iTemplates=[];
for(const flange of [.08,.12,.16,.20,.25,.30])for(const web of [.08,.12,.16,.20,.25,.30]) {
  iTemplates.push(template((x,y)=>y<flange||y>1-flange||Math.abs(x-.5)<web/2));
}
const circleRings=[];
for(const thickness of [.06,.09,.12,.16,.20,.25,.30,.36]) {
  circleRings.push(template((x,y)=>{
    const radius=(x-.5)**2+(y-.5)**2;
    return radius<=.25&&radius>=(.5-thickness)**2;
  }));
}

function classify(mask,width,height,points,holes) {
  const scores={rhs:0,i:0,round:0,rect:0},bestAngles={};
  // A 3-degree rotation search supports tilted sections without claiming perspective correction.
  for(let degrees=0;degrees<180;degrees+=3) {
    const normalized=normalizedMask(mask,width,height,points,degrees*Math.PI/180);
    if(normalized.aspect<.2||normalized.aspect>5)continue;
    const consider=(type,value)=>{if(value>scores[type]){scores[type]=value;bestAngles[type]=degrees;}};
    if(!holes.length) {
      consider('rect',fit(normalized.mask,solidRectangle));
      for(const shape of iTemplates)consider('i',fit(normalized.mask,shape));
      if(normalized.aspect>=.88&&normalized.aspect<=1.14)consider('round',fit(normalized.mask,solidCircle));
    }
    if(holes.length===1) {
      for(let wall=.04;wall<=.32;wall+=.01) {
        // Equal apparent wall width in the two physical axes, not equal normalized fractions.
        const wx=wall*Math.min(1,1/normalized.aspect),wy=wall*Math.min(1,normalized.aspect);
        const shape=template((x,y)=>x<wx||x>1-wx||y<wy||y>1-wy);
        consider('rhs',fit(normalized.mask,shape));
      }
      if(normalized.aspect>=.88&&normalized.aspect<=1.14)
        for(const shape of circleRings)consider('round',fit(normalized.mask,shape));
    }
  }
  return {scores,bestAngles};
}

export function analyzeSectionPixels({data,width,height}={}) {
  if(!Number.isInteger(width)||!Number.isInteger(height)||width<32||height<32||width>512||height>512||
      !(data instanceof Uint8ClampedArray||data instanceof Uint8Array)||data.length!==width*height*4)
    throw new TypeError('Se requiere una imagen RGBA de 32 a 512 píxeles por lado.');
  const n=width*height,strip=Math.max(2,Math.floor(Math.min(width,height)*.035));
  const border=[],channels=[[],[],[]];
  // Transparent pixels are composed over white, like an ordinary photograph preview.
  const rgb=new Uint8Array(n*3);
  for(let i=0;i<n;i++) {
    const alpha=data[i*4+3]/255;
    for(let ch=0;ch<3;ch++)rgb[i*3+ch]=Math.round(data[i*4+ch]*alpha+255*(1-alpha));
    const x=i%width,y=Math.floor(i/width);
    if(x<strip||x>=width-strip||y<strip||y>=height-strip) {
      border.push(i);for(let ch=0;ch<3;ch++)channels[ch].push(rgb[i*3+ch]);
    }
  }
  const background=channels.map(values=>quantile(values,.5));
  const distances=new Float32Array(n);
  for(let i=0;i<n;i++)distances[i]=Math.sqrt(((rgb[i*3]-background[0])**2+(rgb[i*3+1]-background[1])**2+(rgb[i*3+2]-background[2])**2)/3);
  const edgeDistances=border.map(i=>distances[i]);
  const spread=quantile(edgeDistances,.9),threshold=Math.max(28,quantile(edgeDistances,.98)+20);
  const metrics={method:'segmentación y ajuste de silueta 2D',scoreMeaning:'IoU geométrica, no probabilidad',width,height,
    backgroundSpread:rounded(spread),threshold:rounded(threshold),physicalDimensionsEstimated:false,
    semanticRecognition:false,requiresTransverseSectionConfirmation:true};
  if(spread>24)return unknown('Fondo no uniforme. Fotografía solo el corte transversal con fondo liso contrastante y margen.',metrics);
  const mask=new Uint8Array(n);
  let foreground=0,borderForeground=0;
  for(let i=0;i<n;i++)if(distances[i]>=threshold){mask[i]=1;foreground++;}
  for(const i of border)borderForeground+=mask[i];
  metrics.foregroundFraction=rounded(foreground/n);
  if(borderForeground>Math.max(2,border.length*.003))return unknown('La pieza o una sombra toca el borde. Deja margen visible alrededor de toda la sección.',metrics);
  if(foreground<n*.035)return unknown('No hay una sección suficientemente visible o contrastante. Acerca la cámara sin cortar sus bordes.',metrics);
  if(foreground>n*.72)return unknown('La imagen está demasiado llena. Aísla una sección completa y deja fondo visible alrededor.',metrics);
  const {labels,components}=connectedComponents(mask,width,height),largest=components[0];
  metrics.componentCount=components.length;metrics.mainComponentFraction=rounded(largest.area/foreground);
  if(largest.area/foreground<.96)return unknown('Hay varias formas, textura o ruido. Recorta una sola sección transversal, no la estructura completa.',metrics);
  if(largest.minX<strip||largest.minY<strip||largest.maxX>=width-strip||largest.maxY>=height-strip)
    return unknown('No se ve margen suficiente alrededor de la sección. Repite o amplía el recorte.',metrics);
  const boxWidth=largest.maxX-largest.minX+1,boxHeight=largest.maxY-largest.minY+1;
  if(Math.min(boxWidth,boxHeight)<16)return unknown('La sección ocupa muy pocos píxeles. Acerca la imagen manteniendo margen.',metrics);
  const points=[];
  for(let i=0;i<n;i++) {
    mask[i]=labels[i]===largest.label?1:0;
    if(mask[i])points.push(i%width,Math.floor(i/width));
  }
  const holes=enclosedHoles(mask,width,height,largest);
  metrics.visibleHoles=holes.length;
  if(holes.length>1)return unknown('Se observan múltiples huecos o una forma compleja. No puedo identificar una sección única con este método.',metrics);
  if(holes.length===1) {
    const hole=holes[0],cx=(largest.minX+largest.maxX)/2,cy=(largest.minY+largest.maxY)/2;
    if(Math.abs(hole.x-cx)>boxWidth*.12||Math.abs(hole.y-cy)>boxHeight*.12)
      return unknown('El hueco es muy excéntrico o la vista tiene perspectiva. Toma la sección de frente.',metrics);
  }
  const {scores,bestAngles}=classify(mask,width,height,points,holes);
  const ranked=TYPES.map(type=>({type,score:scores[type]})).sort((a,b)=>b.score-a.score);
  const first=ranked[0],second=ranked[1];
  metrics.bestFit=rounded(first.score);metrics.fitMargin=rounded(first.score-second.score);
  metrics.visibleHole=holes.length===1;
  // High fit and separation are required. These are heuristic gates, not validated accuracy claims.
  if(first.score<.88)return unknown('La silueta no coincide suficientemente con las formas admitidas. No identificable: confirma el perfil manualmente.',metrics);
  if(first.score-second.score<.075)return unknown('La imagen es ambigua entre varias formas. No identificable: mejora la foto o elige el perfil manualmente.',metrics);
  metrics.rotationDegrees=bestAngles[first.type];
  const explanations={
    rhs:'Contorno rectangular con un hueco visible. Confirma que sea un tubo y mide ambas paredes; sombras o fondo pueden engañar.',
    i:'Silueta con dos alas y un alma central. Confirma que la foto sea del corte transversal, no un marco u otra pieza con esa forma.',
    round:holes.length ? 'Contorno aproximadamente circular con un hueco visible. Confirma tubo, diámetro y espesor.' : 'Contorno aproximadamente circular. No permite distinguir macizo de tubo con interior oscuro: confirma material, diámetro y espesor.',
    rect:'Contorno rectangular. No demuestra que sea macizo: un tubo con interior oscuro puede verse igual. Confirma familia, ancho, alto y espesores.',
  };
  return {status:'candidates',candidates:[{type:first.type,label:LABELS[first.type],score:rounded(first.score),reason:explanations[first.type]}],
    reason:'Solo asistencia de sección transversal: confirma que la foto sea del corte de una pieza. No reconoce estructuras, uniones ni catálogos. Revisa la familia y mide antes de crear el modelo.',metrics};
}
