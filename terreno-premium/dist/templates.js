import {createProject, validateProject, validateProfile, LIMITS} from './core.js';

/**
 * Explicitly selected, manual geometric starting points. These templates are NOT
 * photo recognition, measured geometry, structural designs, or engineering advice.
 * All input dimensions are axis-to-axis distances in millimetres.
 */
const LABELS = Object.freeze({bastidor:'Bastidor rectangular', portico:'Pórtico', atril:'Atril'});
const dimension = (value, label) => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0.001 || value > LIMITS.coordinate)
    throw new Error(`${label}: ingresa una dimensión positiva entre 0,001 y ${LIMITS.coordinate} mm.`);
  return value;
};

export function createTemplateProject(options = {}) {
  if (!options || typeof options !== 'object' || Array.isArray(options))
    throw new Error('Se esperan las opciones de la plantilla.');
  const {type, name, profile, bracing = false} = options;
  if (!Object.hasOwn(LABELS, type)) throw new Error('Plantilla no válida: elige bastidor, pórtico o atril.');
  if (typeof bracing !== 'boolean') throw new Error('La opción de diagonales debe ser verdadera o falsa.');
  const width = dimension(options.width, 'Ancho X');
  const height = dimension(options.height, 'Altura Z');
  let depth = 0;
  if (type === 'portico') {
    if (options.depth !== undefined && options.depth !== 0)
      throw new Error('El pórtico es plano: omite la profundidad o usa 0 mm.');
  } else depth = dimension(options.depth, 'Profundidad Y');
  validateProfile(profile, 'Perfil de la plantilla');
  const section = Object.fromEntries(['type','height','width','tw','tf'].map(key => [key, profile[key]]));
  const project = createProject(name === undefined ? `${LABELS[type]} · plantilla manual` : name, `${LABELS[type]} · plantilla manual por verificar`);
  project.unit = 'mm';
  project.example = false;
  // Preserve provenance through JSON without making a claim that these are field readings.
  project.template = {
    version:1, origin:'manual-template', type, bracing,
    dimensionsMm:{width, depth, height},
    dimensionsReference:'axis-to-axis', parametersAtCreation:true,
    warning:'Parámetros iniciales de creación; no se recalculan al editar nodos. Plantilla elegida manualmente; no derivada de foto ni validada por ingeniería.',
  };
  const note = 'Origen: plantilla manual elegida por el operador, NO derivada de una foto. '
    + 'Geometría y perfil iniciales pendientes de verificar en terreno; no son lecturas confirmadas. '
    + 'Las dimensiones se definen entre ejes. No es cálculo, diseño ni certificación estructural.';
  const node = (id, label, x, y, z) => { project.nodes.push({id, label, x, y, z}); return id; };
  const member = (label, startNode, endNode) => project.members.push({
    id:`tpl-m${project.members.length+1}`, label, startNode, endNode,
    profile:{...section}, roll:0, status:'estimated', note,
    lengthMeasured:null, photos:[],
  });

  if (type === 'portico') {
    const a=node('tpl-a','Pie izquierdo',0,0,0), b=node('tpl-b','Pie derecho',width,0,0);
    const c=node('tpl-c','Cabeza izquierda',0,0,height), d=node('tpl-d','Cabeza derecha',width,0,height);
    member('Columna izquierda',a,c); member('Columna derecha',b,d); member('Travesaño superior',c,d);
    if (bracing) member('Diagonal del pórtico',a,d);
  } else if (type === 'bastidor') {
    const a=node('tpl-a','Base frontal izquierda',0,0,0), b=node('tpl-b','Base frontal derecha',width,0,0);
    const c=node('tpl-c','Base posterior izquierda',0,depth,0), d=node('tpl-d','Base posterior derecha',width,depth,0);
    const e=node('tpl-e','Coronación frontal izquierda',0,0,height), f=node('tpl-f','Coronación frontal derecha',width,0,height);
    const g=node('tpl-g','Coronación posterior izquierda',0,depth,height), h=node('tpl-h','Coronación posterior derecha',width,depth,height);
    member('Pilar frontal izquierdo',a,e); member('Pilar frontal derecho',b,f);
    member('Pilar posterior izquierdo',c,g); member('Pilar posterior derecho',d,h);
    member('Base frontal',a,b); member('Base posterior',c,d);
    member('Base lateral izquierda',a,c); member('Base lateral derecha',b,d);
    member('Travesaño superior frontal',e,f); member('Travesaño superior posterior',g,h);
    member('Travesaño superior izquierdo',e,g); member('Travesaño superior derecho',f,h);
    if (bracing) {
      member('Diagonal frontal',a,f); member('Diagonal posterior',c,h);
      member('Diagonal lateral izquierda',a,g); member('Diagonal lateral derecha',b,h);
    }
  } else {
    const a=node('tpl-a','Pie frontal izquierdo',0,0,0), b=node('tpl-b','Pie frontal derecho',width,0,0);
    const c=node('tpl-c','Pie posterior izquierdo',0,depth,0), d=node('tpl-d','Pie posterior derecho',width,depth,0);
    const e=node('tpl-e','Coronación izquierda',0,depth/2,height), f=node('tpl-f','Coronación derecha',width,depth/2,height);
    member('Pata frontal izquierda',a,e); member('Pata frontal derecha',b,f);
    member('Pata posterior izquierda',c,e); member('Pata posterior derecha',d,f);
    member('Base frontal',a,b); member('Base posterior',c,d); member('Travesaño de coronación',e,f);
    member('Travesaño inferior izquierdo',a,c); member('Travesaño inferior derecho',b,d);
    if (bracing) { member('Diagonal frontal',a,f); member('Diagonal posterior',d,e); }
  }
  return validateProject(project);
}

/** Generic synthetic family seed with independently chosen round dimensions; never a field measurement.
 * Curved saddles are semantic preview features, not mesh beams or STEP solids.
 * Their dimensions stay in the JSON record; STEP for these features is pending.
 */
export function createSupportTemplate({name='Atril con cunas · ejemplo ajustable',length=2000,width=1000,radius=800,saddles=true,forks=true}={}) {
  dimension(length,'Largo exterior'); dimension(width,'Ancho exterior'); dimension(radius,'Radio de cuna');
  if(length<1200||width<500||width>radius*1.8)throw Error('Para esta familia: largo ≥ 1200 mm, ancho ≥ 500 mm y ancho ≤ 1,8 × radio. Revisa las dimensiones.');
  if(typeof saddles!=='boolean'||typeof forks!=='boolean')throw Error('Opciones de familia inválidas.');
  const p=createProject(name,'Atril bajo · familia inicial por verificar');p.example=true;
  p.template={version:2,origin:'family-seed',type:'support-saddles',dimensionsMm:{length,width,radius},saddles,forks,dimensionsReference:'outside-envelope',warning:'Ejemplo sintético ajustable, no corresponde a un levantamiento ni a un diseño aprobado. Cunas: vista aproximada, exportación STEP pendiente.'};
  const note='Modelo inicial de familia. Dimensiones sintéticas, no medidas. Verificar existencia, sección, posición y conexión de cada componente. Sin cálculo ni certificación.';
  const node=(x,y,z)=>{const n={id:`family-n${p.nodes.length+1}`,label:`Punto ${p.nodes.length+1}`,x,y,z};p.nodes.push(n);return n.id;};
  const member=(label,a,b,profile,role)=>p.members.push({id:`family-m${p.members.length+1}`,label,startNode:node(...a),endNode:node(...b),profile,roll:0,status:'estimated',note,lengthMeasured:null,photos:[],role,source:'synthetic-family'});
  const channel={type:'channel',height:200,width:100,tw:10,tf:10};
  member('Larguero frontal',[0,50,100],[length,50,100],{...channel},'frame');
  member('Larguero posterior',[0,width-50,100],[length,width-50,100],{...channel},'frame');
  for(const x of [100,length-100])member('Travesaño de cierre',[x,100,100],[x,width-100,100],{type:'rhs',height:100,width:100,tw:5,tf:5},'frame');
  if(forks)for(const x of [length*.3,length*.7])member('Tomauñas · sección preliminar',[x,0,125],[x,width,125],{type:'rhs',height:150,width:200,tw:5,tf:5},'fork-pocket');
  p.features=saddles?[length*.2,length*.8].map((x,i)=>({id:`saddle-${i+1}`,type:'saddle-preview',label:`Cuna ${i+1} · vista preliminar`,x,y:width/2,z:250,width:width*.8,length:200,radius,thickness:10,status:'estimated',source:'synthetic-family',exportable:false})):[];
  return validateProject(p);
}
