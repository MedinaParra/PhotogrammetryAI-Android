import {analyzeStructurePixels} from './structure-analysis.js';
import {analyzeSectionPixels} from './photo-analysis.js';

// Local RGBA only. No network, persistence, dimensions or family labels invented.
self.onmessage=({data})=>{
  const {id,kind,pixels}=data||{};
  try{
    if(!Number.isSafeInteger(id)||!['structure','section'].includes(kind))throw Error('Solicitud inválida.');
    const result=(kind==='section'?analyzeSectionPixels:analyzeStructurePixels)(pixels);
    self.postMessage({id,result});
  }catch{
    self.postMessage({id,error:'No se pudo analizar la imagen local.'});
  }
};
