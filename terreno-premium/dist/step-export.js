import {validateProject} from './core.js';

/**
 * Offline ISO 10303-21 / AP214 STEP exporter: exact planar B-rep prisms, NOT STL,
 * triangle tessellation, an engineering approval, or a manufacturing model.
 * Rect/I/channel/RHS use FACE_SURFACE(PLANE) + POLY_LOOP + CLOSED_SHELL/FACETED_BREP.
 * RHS has one connected closed shell with an actual through-hole; not four
 * overlapping bars. Each surveyed member is a named assembly part containing
 * one separate solid, never fused. Geometry is already in survey coordinates;
 * child-to-parent placements are intentionally identity transformations.
 * Nominal sharp corners, no fillets, cuts, joints, welds or catalogue claims.
 * Coordinates/profile sizes remain mm regardless of the UI display unit.
 * References (implementation constraints, no copied source):
 * https://www.steptools.com/docs/stp_aim/html/t_faceted_brep_shape_representation.html
 * https://www.steptools.com/docs/stp_aim/html/t_face_surface.html
 * https://github.com/Open-Cascade-SAS/OCCT/blob/master/dox/user_guides/step/step.md
 */
export const STEP_LIMITS=Object.freeze({members:1000,bytes:24*1024*1024,minFeatureMm:.001});
const add=(a,b)=>a.map((v,i)=>v+b[i]);
const sub=(a,b)=>a.map((v,i)=>v-b[i]);
const mul=(a,s)=>a.map(v=>v*s);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const norm=a=>Math.hypot(...a);
const unit=a=>{const n=norm(a);if(!Number.isFinite(n)||n===0)throw Error('STEP: geometría degenerada.');return mul(a,1/n);};
const pos=n=>[n.x,n.y,n.z];
const num=n=>{if(!Number.isFinite(n))throw Error('STEP: coordenada no finita.');if(Object.is(n,-0)||n===0)return '0.';let s=n.toPrecision(17).replace(/e([+-]?)(\d+)/i,(_,sign,d)=>'E'+(sign||'+')+d);if(!s.includes('.')&&!s.includes('E'))s+='.';return s;};
const tuple=a=>'('+a.map(num).join(',')+')';
const str=value=>{const s=String(value).replace(/[\u0000-\u001f\u007f]/g,' ');if(/[^\x20-\x7e]|\\/.test(s))return "'\\X2\\"+Array.from({length:s.length},(_,i)=>s.charCodeAt(i).toString(16).padStart(4,'0').toUpperCase()).join('')+"\\X0\\'";return "'"+s.replace(/'/g,"''")+"'";};

function contours(profile){
  const {type,width:w,height:h,tw,tf}=profile;
  const box=[[-w/2,-h/2],[w/2,-h/2],[w/2,h/2],[-w/2,h/2]];
  if(type==='rect')return [box];
  if(type==='rhs')return [box,[[-w/2+tw,-h/2+tf],[-w/2+tw,h/2-tf],[w/2-tw,h/2-tf],[w/2-tw,-h/2+tf]]];
  if(type==='i')return [[[-w/2,-h/2],[w/2,-h/2],[w/2,-h/2+tf],[tw/2,-h/2+tf],[tw/2,h/2-tf],[w/2,h/2-tf],[w/2,h/2],[-w/2,h/2],[-w/2,h/2-tf],[-tw/2,h/2-tf],[-tw/2,-h/2+tf],[-w/2,-h/2+tf]]];
  if(type==='channel')return [[[-w/2,-h/2],[w/2,-h/2],[w/2,-h/2+tf],[-w/2+tw,-h/2+tf],[-w/2+tw,h/2-tf],[w/2,h/2-tf],[w/2,h/2],[-w/2,h/2]]];
  throw Error(`STEP: perfil «${type}» no compatible con sólidos exactos en esta versión. Circular y personalizado se bloquean; no se convierten a malla ni se aproximan.`);
}

export function serializeStep(project){
  validateProject(project);
  if(project.features?.length)throw new Error("STEP completo pendiente: este modelo contiene cunas u otros componentes preliminares no soportados. No se exportará una estructura incompleta.");
  if(!project.members.length)throw Error('STEP: el proyecto no tiene piezas para exportar.');
  if(project.members.length>STEP_LIMITS.members)throw Error(`STEP: máximo ${STEP_LIMITS.members} piezas por archivo; divide el proyecto.`);
  const nodes=new Map(project.nodes.map(n=>[n.id,n]));
  // Preflight ALL members: no partial exports or silently omitted profiles.
  for(const m of project.members){
    contours(m.profile);
    const {width:w,height:h,tw,tf,type}=m.profile;
    const features=[w,h,norm(sub(pos(nodes.get(m.endNode)),pos(nodes.get(m.startNode))))];
    if(type==='rhs')features.push(tw,tf,w-2*tw,h-2*tf);
    if(type==='i')features.push(tw,tf,(w-tw)/2,h-2*tf);
    if(type==='channel')features.push(tw,tf,w-tw,h-2*tf);
    if(features.some(n=>n<STEP_LIMITS.minFeatureMm))throw Error(`STEP: ${m.label}: un detalle es menor que ${STEP_LIMITS.minFeatureMm} mm. No se exporta una geometría degenerada.`);
  }
  const entities=[];let total=0;
  const entity=body=>{const ref='#'+(entities.length+1),line=`${ref}=${body};`;total+=line.length+1;if(total>STEP_LIMITS.bytes-8192)throw Error('STEP: el archivo supera 24 MB; divide el levantamiento.');entities.push(line);return ref;};
  const point=coords=>entity(`CARTESIAN_POINT('',${tuple(coords)})`);
  const direction=vector=>entity(`DIRECTION('',${tuple(unit(vector))})`);
  const application=entity("APPLICATION_CONTEXT('automotive design')");
  entity(`APPLICATION_PROTOCOL_DEFINITION('international standard','automotive_design',2000,${application})`);
  const productContext=entity(`PRODUCT_CONTEXT('',${application},'mechanical')`);
  const product=entity(`PRODUCT(${str(project.id)},${str(project.name)},'Survey geometry; estimated unless reviewed by operator; NOT engineering approval',(${productContext}))`);
  const formation=entity(`PRODUCT_DEFINITION_FORMATION('1.2','Nominal sharp-corner prisms; no joints, welds, fillets or Boolean fusion',${product})`);
  const definitionContext=entity(`PRODUCT_DEFINITION_CONTEXT('part definition',${application},'design')`);
  const definition=entity(`PRODUCT_DEFINITION('design','Geometry from recorded endpoints and section; not field accuracy or safety certification',${formation},${definitionContext})`);
  const definitionShape=entity(`PRODUCT_DEFINITION_SHAPE('','Independent member solids; overlaps intentionally retained',${definition})`);
  const lengthUnit=entity('(LENGTH_UNIT()NAMED_UNIT(*)SI_UNIT(.MILLI.,.METRE.))');
  const angleUnit=entity('(NAMED_UNIT(*)PLANE_ANGLE_UNIT()SI_UNIT($,.RADIAN.))');
  const solidAngle=entity('(NAMED_UNIT(*)SI_UNIT($,.STERADIAN.)SOLID_ANGLE_UNIT())');
  const tolerance=entity(`UNCERTAINTY_MEASURE_WITH_UNIT(LENGTH_MEASURE(1.E-7),${lengthUnit},'representation tolerance','Numerical CAD tolerance, not survey accuracy')`);
  const context=entity(`(GEOMETRIC_REPRESENTATION_CONTEXT(3)GLOBAL_UNCERTAINTY_ASSIGNED_CONTEXT((${tolerance}))GLOBAL_UNIT_ASSIGNED_CONTEXT((${lengthUnit},${angleUnit},${solidAngle}))REPRESENTATION_CONTEXT('','3D mm'))`);
  const origin=point([0,0,0]);
  const identity=entity(`AXIS2_PLACEMENT_3D('',${origin},${direction([0,0,1])},${direction([1,0,0])})`);
  const assembly=entity(`SHAPE_REPRESENTATION(${str(project.name)},(${identity}),${context})`);
  entity(`SHAPE_DEFINITION_REPRESENTATION(${definitionShape},${assembly})`);
  for(const m of project.members){
    const a=pos(nodes.get(m.startNode)),b=pos(nodes.get(m.endNode)),t=unit(sub(b,a));
    // Same right-handed section frame / roll convention as the Terreno viewer.
    let u=unit(cross(Math.abs(t[2])>.97?[0,1,0]:[0,0,1],t)),v=unit(cross(t,u));
    const roll=m.roll*Math.PI/180,c=Math.cos(roll),s=Math.sin(roll);
    [u,v]=[add(mul(u,c),mul(v,s)),add(mul(v,c),mul(u,-s))];
    const at=(end,q)=>add(end,add(mul(u,q[0]),mul(v,q[1])));
    const loops=contours(m.profile).map(loop=>({front:loop.map(q=>at(a,q)),back:loop.map(q=>at(b,q))}));
    for(const loop of loops){loop.frontRefs=loop.front.map(point);loop.backRefs=loop.back.map(point);}
    const faces=[];
    const face=(boundaries)=>{
      const outer=boundaries[0],origin=outer.coords[0],x=unit(sub(outer.coords[1],origin));
      // A first non-collinear triple can have the WRONG sign at a reflex
      // vertex of an I/channel cap. Sum the oriented polygon area instead.
      // Subtracting the origin first avoids cancellation at large coordinates.
      let area=[0,0,0];
      for(let i=1;i<outer.coords.length-1;i++)area=add(area,cross(sub(outer.coords[i],origin),sub(outer.coords[i+1],origin)));
      if(norm(area)<=1e-15)throw Error(`STEP: cara degenerada en ${m.label}.`);
      const normal=unit(area);
      const bounds=boundaries.map((loop,i)=>{
        const poly=entity(`POLY_LOOP('',(${loop.refs.join(',')}))`);
        return entity(`${i===0?'FACE_OUTER_BOUND':'FACE_BOUND'}('',${poly},.T.)`);
      });
      const axis=entity(`AXIS2_PLACEMENT_3D('',${outer.refs[0]},${direction(normal)},${direction(x)})`);
      const plane=entity(`PLANE('',${axis})`);
      faces.push(entity(`FACE_SURFACE('',(${bounds.join(',')}),${plane},.T.)`));
    };
    // Reverse BOTH outer and inner loops at the start cap. Inner ring winding is
    // opposite the outer boundary, so the hole remains a hole on each cap.
    face(loops.map(loop=>({coords:[...loop.front].reverse(),refs:[...loop.frontRefs].reverse()})));
    face(loops.map(loop=>({coords:loop.back,refs:loop.backRefs})));
    for(const loop of loops)for(let i=0;i<loop.front.length;i++){
      const j=(i+1)%loop.front.length;
      face([{coords:[loop.front[i],loop.front[j],loop.back[j],loop.back[i]],refs:[loop.frontRefs[i],loop.frontRefs[j],loop.backRefs[j],loop.backRefs[i]]}]);
    }
    const shell=entity(`CLOSED_SHELL('',(${faces.join(',')}))`);
    const description=`status=${m.status} | profile=${m.profile.type} | roll=${m.roll}deg | nominal solid, not certified`;
    const solid=entity(`FACETED_BREP(${str(`${m.id} | ${m.label}`)},${shell})`);
    const partProduct=entity(`PRODUCT(${str(m.id)},${str(m.label)},${str(description)},(${productContext}))`);
    const partFormation=entity(`PRODUCT_DEFINITION_FORMATION('1.2','Nominal sharp-corner prism',${partProduct})`);
    const partDefinition=entity(`PRODUCT_DEFINITION('design',${str(description)},${partFormation},${definitionContext})`);
    const partDefinitionShape=entity(`PRODUCT_DEFINITION_SHAPE('','One independent member solid',${partDefinition})`);
    const representation=entity(`FACETED_BREP_SHAPE_REPRESENTATION(${str(m.label)},(${identity},${solid}),${context})`);
    entity(`SHAPE_DEFINITION_REPRESENTATION(${partDefinitionShape},${representation})`);
    const placement=entity(`ITEM_DEFINED_TRANSFORMATION('','Survey coordinates; identity placement',${identity},${identity})`);
    const relation=entity(`(REPRESENTATION_RELATIONSHIP('','',${representation},${assembly})REPRESENTATION_RELATIONSHIP_WITH_TRANSFORMATION(${placement})SHAPE_REPRESENTATION_RELATIONSHIP())`);
    // OCCT prioritizes the occurrence description as its display name; leave
    // that optional field empty so CAD importers retain the operator's label.
    const occurrence=entity(`NEXT_ASSEMBLY_USAGE_OCCURRENCE(${str(m.id)},${str(m.label)},'',${definition},${partDefinition},${str(m.id)})`);
    const occurrenceShape=entity(`PRODUCT_DEFINITION_SHAPE('Placement','Assembly member placement',${occurrence})`);
    entity(`CONTEXT_DEPENDENT_SHAPE_REPRESENTATION(${relation},${occurrenceShape})`);
  }
  const result=`ISO-10303-21;\nHEADER;\nFILE_DESCRIPTION(('Terreno 1.2 - exact planar nominal B-rep solids in mm; no tessellation','Recorded survey data may be estimated; no engineering approval; no photos included'),'2;1');\nFILE_NAME('terreno.step',${str(project.updatedAt)},('Terreno operator'),(''),'Terreno offline STEP 1.2','Terreno','');\nFILE_SCHEMA(('AUTOMOTIVE_DESIGN'));\nENDSEC;\nDATA;\n${entities.join('\n')}\nENDSEC;\nEND-ISO-10303-21;\n`;
  if(new TextEncoder().encode(result).byteLength>STEP_LIMITS.bytes)throw Error('STEP: el archivo supera 24 MB.');
  return result;
}
