// Real pixel engines in a real worker thread with a minimal Web Worker port shim.
// Does not validate browser CSP, phone latency or real-world photographs.
import assert from 'node:assert/strict';
import {Worker} from 'node:worker_threads';
import {once} from 'node:events';
import {analyzeStructurePixels} from './dist/structure-analysis.js';
import {analyzeSectionPixels} from './dist/photo-analysis.js';

const workerUrl=new URL('./dist/photo-worker.js',import.meta.url).href;
const worker=new Worker(`const {parentPort}=require('node:worker_threads');globalThis.self={postMessage:value=>parentPort.postMessage(value)};import(${JSON.stringify(workerUrl)}).then(()=>{parentPort.on('message',data=>self.onmessage({data}));parentPort.postMessage({ready:true});});`,{eval:true});
let id=0;
function image(shape){const width=128,height=128,data=new Uint8ClampedArray(width*height*4);for(let y=0;y<height;y++)for(let x=0;x<width;x++){const value=shape(x,y)?24:242;data.set([value,value,value,255],(y*width+x)*4);}return {data,width,height};}
async function request(kind,pixels){const response=once(worker,'message');const job=++id;worker.postMessage({id:job,kind,pixels},pixels?.data?[pixels.data.buffer]:[]);const [message]=await response;assert.equal(message.id,job);return message;}
try{
  assert.equal((await once(worker,'message'))[0].ready,true);
  for(const [kind,pixels,expectedStatus] of [
    ['structure',image((x,y)=>((x>=23&&x<=28)||(x>=100&&x<=105))&&y>=23&&y<=105||((y>=23&&y<=28)||(y>=100&&y<=105))&&x>=23&&x<=105),'proposal'],
    ['structure',image(()=>false),'unidentifiable'],
    ['section',image((x,y)=>x>30&&x<98&&y>38&&y<90),'candidates'],
  ]){
    const expected=(kind==='section'?analyzeSectionPixels:analyzeStructurePixels)(pixels);
    const reply=await request(kind,pixels);
    assert.equal(pixels.data.byteLength,0,'Input buffer ownership transferred, no copying into shared UI state');
    assert.deepEqual(reply.result,expected);assert.equal(reply.result.status,expectedStatus);
    if(kind==='structure'&&reply.result.status==='proposal')assert(reply.result.candidates.every(c=>['bastidor','portico'].includes(c.type)),'No invented atril identity');
  }
  assert((await request('family',image(()=>false))).error,'Unknown operation rejected');
  assert((await request('structure',{width:1,height:1,data:new Uint8ClampedArray(4)})).error,'Malformed pixels fail without a proposal');
  console.log('PASS photo worker: real local engines, transferable RGBA, proposal/unknown/error and no invented family; browser/device QA separate.');
}finally{await worker.terminate();}
