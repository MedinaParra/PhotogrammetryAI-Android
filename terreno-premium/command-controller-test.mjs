import assert from 'node:assert/strict';
import {createCommandController, COMMAND_MODES, TOUCH_CONTROL_LABEL} from './dist/command-controller.js';

let passed = 0;
const test = (label, fn) => { fn(); passed++; console.log(`✓ ${label}`); };
const targets = () => ({navigation:['azimuth','zoom'], editing:['selected-piece'], measurement:['manual-reading']});
function fixture(overrides = {}) {
  const calls = [];
  const handlers = Object.fromEntries(['onNavigate','onEdit','onAccept','onManualMeasurementAccept']
    .map(name => [name, command => { calls.push({name, command}); return `handled:${name}`; }]));
  return {calls, controller:createCommandController({targets:targets(), ...handlers, ...overrides})};
}
const event = (controller, changes = {}) => ({type:'step', ...controller.getState(), source:'touch', direction:1, ...changes});

test('Initial navigation has no focus and identifies tactile controls, not a connected instrument', () => {
  const {controller:c,calls} = fixture();
  assert.deepEqual(c.getState(), {mode:'navigation',focus:null});
  assert.match(TOUCH_CONTROL_LABEL,/táctiles; instrumento no conectado/);
  assert.equal(c.step(1).reason,'no-focus'); assert.equal(c.accept().reason,'no-focus'); assert.equal(calls.length,0);
  assert.deepEqual(COMMAND_MODES,['navigation','editing','measurement']);
});
test('Navigation +1/-1 goes only to navigation with selected camera focus', () => {
  const {controller:c,calls} = fixture(); c.setFocus('azimuth');
  assert.equal(c.step(1).ok,true); assert.equal(c.step(-1).ok,true);
  assert.deepEqual(calls.map(x=>x.name),['onNavigate','onNavigate']);
  assert.deepEqual(calls.map(x=>x.command.direction),[1,-1]);
  assert(calls.every(x=>x.command.focus==='azimuth' && x.command.mode==='navigation' && x.command.source==='touch'));
});
test('Navigation accept uses generic accept without creating a value', () => {
  const {controller:c,calls} = fixture(); c.setFocus('zoom'); const out=c.accept();
  assert.equal(out.result,'handled:onAccept'); assert.equal(calls[0].name,'onAccept');
  assert.deepEqual(Object.keys(calls[0].command),['type','mode','focus','source']);
});
test('Editing steps and accept are explicitly routed; controller does not own geometry', () => {
  const {controller:c,calls} = fixture(); c.setMode('editing'); c.setFocus('selected-piece');
  c.step(-1); c.step(1); c.accept();
  assert.deepEqual(calls.map(x=>x.name),['onEdit','onEdit','onAccept']);
  assert(calls.every(x=>x.command.mode==='editing' && x.command.focus==='selected-piece'));
});
test('Changing mode resets focus without invoking any handler', () => {
  const {controller:c,calls} = fixture(); c.setFocus('azimuth');
  assert.deepEqual(c.setMode('editing').state,{mode:'editing',focus:null});
  assert.equal(c.step(1).reason,'no-focus'); assert.equal(calls.length,0);
});
test('Re-selecting same mode preserves focus; explicit null clears it', () => {
  const {controller:c} = fixture(); c.setFocus('zoom'); c.setMode('navigation');
  assert.equal(c.getState().focus,'zoom'); assert.equal(c.setFocus(null).ok,true); assert.equal(c.getState().focus,null);
});
test('Measurement steps are blocked with or without a selected focus', () => {
  const {controller:c,calls} = fixture(); c.setMode('measurement');
  assert.equal(c.step(1).reason,'measurement-step-blocked');
  c.setFocus('manual-reading');
  for(const n of [-1,1,-1,1]) assert.equal(c.step(n).reason,'measurement-step-blocked');
  assert.equal(calls.length,0); assert.deepEqual(c.getState(),{mode:'measurement',focus:'manual-reading'});
});
test('Measurement steps cannot change simulated camera, selected piece, geometry or reading', () => {
  const model = {camera:20,selected:1,geometry:[0,1000],reading:null}; const before=structuredClone(model);
  const c=createCommandController({targets:targets(),onNavigate:()=>model.camera++,onEdit:()=>{model.selected++;model.geometry[1]++;},onAccept:()=>model.reading=999});
  c.setMode('measurement'); c.setFocus('manual-reading');
  for(let i=0;i<100;i++) c.step(i%2 ? 1 : -1);
  assert.equal(c.accept().reason,'handler-unavailable'); assert.deepEqual(model,before);
});
test('Measurement accept only invokes separately authorized manual-input callback', () => {
  const {controller:c,calls} = fixture(); c.setMode('measurement'); c.setFocus('manual-reading');
  const out=c.accept(); assert.equal(out.ok,true); assert.equal(out.result,'handled:onManualMeasurementAccept');
  assert.deepEqual(calls.map(x=>x.name),['onManualMeasurementAccept']);
  assert.deepEqual(calls[0].command,{type:'accept',mode:'measurement',focus:'manual-reading',source:'touch'});
});
test('Measurement accept requires manual focus, not implicit first target', () => {
  const {controller:c,calls} = fixture(); c.setMode('measurement');
  assert.equal(c.accept().reason,'no-focus'); assert.equal(calls.length,0);
});
test('Wrong mode and stale focus commands cannot cross contexts', () => {
  const {controller:c,calls} = fixture(); c.setFocus('azimuth'); const old=event(c);
  c.setFocus('zoom'); assert.equal(c.dispatch(old).reason,'stale-focus');
  c.setMode('editing'); c.setFocus('selected-piece'); assert.equal(c.dispatch(old).reason,'stale-mode');
  assert.equal(calls.length,0);
});
test('Rejected modes do not alter mode/focus', () => {
  const {controller:c} = fixture(); c.setFocus('azimuth'); const before=c.getState();
  for(const invalid of ['Navigation','bluetooth','',null,undefined,1,{},[]]) {
    assert.equal(c.setMode(invalid).reason,'invalid-mode'); assert.deepEqual(c.getState(),before);
  }
});
test('Focus is allowlisted for its mode and invalid focus leaves state unchanged', () => {
  const {controller:c} = fixture(); c.setFocus('azimuth'); const before=c.getState();
  for(const invalid of ['manual-reading','selected-piece','missing','',undefined,3,{},'__proto__']) {
    assert.equal(c.setFocus(invalid).reason,'invalid-focus'); assert.deepEqual(c.getState(),before);
  }
});
test('Directions must be numeric exact -1/+1, never magnitudes, strings, NaN or infinity', () => {
  const {controller:c,calls} = fixture(); c.setFocus('azimuth');
  for(const invalid of [0,2,-2,.5,'1','-1',NaN,Infinity,-Infinity,null,undefined,true,{}])
    assert.equal(c.step(invalid).reason,'invalid-direction');
  assert.equal(calls.length,0);
});
test('Hardware/Bluetooth/wheel and unknown sources are rejected, including manual accept', () => {
  const {controller:c,calls} = fixture(); c.setFocus('azimuth');
  for(const source of ['bluetooth','wheel','instrument','keyboard','',null,undefined])
    assert.equal(c.dispatch(event(c,{source})).reason,'unsupported-source');
  c.setMode('measurement'); c.setFocus('manual-reading');
  assert.equal(c.dispatch({type:'accept',...c.getState(),source:'bluetooth'}).reason,'unsupported-source');
  assert.equal(calls.length,0);
});
test('No reading/value/unit payload is accepted or inferred in any mode', () => {
  const {controller:c,calls} = fixture();
  for(const mode of COMMAND_MODES) {
    c.setMode(mode); c.setFocus(targets()[mode][0]);
    for(const key of ['reading','value','length','unit','timestamp']) {
      assert.equal(c.dispatch(event(c,{[key]:1234})).reason,'invalid-payload');
      assert.equal(c.dispatch({type:'accept',...c.getState(),source:'touch',[key]:1234}).reason,'invalid-payload');
    }
  }
  assert.equal(calls.length,0);
});
test('Unknown event types, primitives, missing fields and accept direction are rejected', () => {
  const {controller:c,calls} = fixture(); c.setFocus('azimuth');
  for(const invalid of [null,undefined,[],1,'step',()=>{},new Date()]) assert.equal(c.dispatch(invalid).reason,'invalid-event');
  for(const type of ['measure','reading','connect','STEP',null]) assert.equal(c.dispatch(event(c,{type})).reason,'invalid-type');
  const missing=event(c); delete missing.source; assert.equal(c.dispatch(missing).reason,'invalid-payload');
  assert.equal(c.dispatch(event(c,{type:'accept'})).reason,'invalid-payload'); assert.equal(calls.length,0);
});
test('Accessors, symbols and inherited payloads do not execute or bypass validation', () => {
  const {controller:c,calls} = fixture(); c.setFocus('azimuth'); let getterCalls=0;
  const accessor=event(c); Object.defineProperty(accessor,'direction',{get(){getterCalls++;return 1;}});
  assert.equal(c.dispatch(accessor).reason,'invalid-event'); assert.equal(getterCalls,0);
  const symbolic=event(c); symbolic[Symbol('value')]=1; assert.equal(c.dispatch(symbolic).reason,'invalid-event');
  assert.equal(c.dispatch(Object.create(event(c))).reason,'invalid-event'); assert.equal(calls.length,0);
});
test('Null-prototype records are accepted only with complete valid data fields', () => {
  const {controller:c,calls} = fixture(); c.setFocus('azimuth');
  assert.equal(c.dispatch(Object.assign(Object.create(null),event(c))).ok,true); assert.equal(calls.length,1);
});
test('Configuration lists are copied; caller mutation cannot add future targets', () => {
  const configured=targets(); const c=createCommandController({targets:configured});
  configured.navigation.push('injected'); configured.measurement[0]='different';
  assert.equal(c.setFocus('injected').reason,'invalid-focus');
  c.setMode('measurement'); assert.equal(c.setFocus('manual-reading').ok,true); assert.equal(c.setFocus('different').reason,'invalid-focus');
});
test('Controller, states and delivered commands are immutable snapshots', () => {
  const {controller:c,calls} = fixture(); const initial=c.getState(); c.setFocus('azimuth'); const out=c.step(1);
  assert(Object.isFrozen(c)); assert(Object.isFrozen(initial)); assert(Object.isFrozen(out)); assert(Object.isFrozen(calls[0].command));
  assert.throws(()=>{initial.mode='measurement';},TypeError); assert.throws(()=>{calls[0].command.direction=100;},TypeError);
  assert.deepEqual(initial,{mode:'navigation',focus:null});
});
test('Missing handlers reject explicitly rather than claiming an action occurred', () => {
  const c=createCommandController({targets:targets()}); c.setFocus('zoom');
  assert.equal(c.step(1).reason,'handler-unavailable'); assert.equal(c.accept().reason,'handler-unavailable');
});
test('Synchronous callback errors are surfaced and do not dispatch another mode handler', () => {
  const failure=new Error('UI rejected action'); const {controller:c,calls}=fixture({onNavigate:()=>{throw failure;}});
  c.setFocus('azimuth'); const result=c.step(1);
  assert.equal(result.ok,false); assert.equal(result.reason,'handler-error'); assert.equal(result.error,failure); assert.equal(calls.length,0);
});
test('Async completion is returned unchanged, never misrepresented as saved data', () => {
  const pending=Promise.resolve('manual UI completion'); const {controller:c}=fixture({onManualMeasurementAccept:()=>pending});
  c.setMode('measurement'); c.setFocus('manual-reading'); assert.equal(c.accept().result,pending);
});
test('Invalid developer configuration fails early', () => {
  for(const options of [null,[],3,{targets:{other:[]}},{targets:{navigation:['x','x']}},{targets:{editing:[null]}},{targets:{measurement:'manual'}},{onNavigate:true}])
    assert.throws(()=>createCommandController(options),TypeError);
});
test('Independent controllers do not share selected context or focus', () => {
  const a=fixture().controller,b=fixture().controller; a.setMode('measurement'); a.setFocus('manual-reading');
  assert.deepEqual(b.getState(),{mode:'navigation',focus:null});
});

console.log(`\n${passed} command-controller groups passed. Pure routing only; no hardware, physical measurement, persistence or Android QA.`);
