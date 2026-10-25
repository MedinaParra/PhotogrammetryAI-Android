import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..');
for(const file of ['step-export.js','premium-viewer.js','app.js','core.js','viewer.js','compat.js','photo-analysis.js','structure-analysis.js','templates.js','command-controller.js']){const r=spawnSync(process.execPath,['--check',path.join(root,'dist',file)],{stdio:'inherit'});if(r.status!==0)process.exit(r.status||1);}
for(const file of ['step-export.js','premium.css','vendor/inter/InterVariable.woff2','vendor/inter/LICENSE.txt','vendor/three/three.module.js','vendor/three/three.core.js','vendor/three/LICENSE.txt','vendor/lucide/LICENSE.txt','index.html','style.css','app.js','core.js','viewer.js','compat.js','photo-analysis.js','structure-analysis.js','templates.js','command-controller.js'])if(!fs.statSync(path.join(root,'dist',file)).isFile())throw Error(`Falta ${file}`);
fs.mkdirSync(path.join(root,'build'),{recursive:true});
fs.cpSync(path.join(root,'dist'),path.join(root,'build','terreno'),{recursive:true});
console.log('Build estático verificado: build/terreno. Dependencias vendorizadas locales: Three MIT, Inter OFL, Lucide ISC/MIT.');
