/**
 * Local command router for the explicitly selected on-screen controls.
 * No DOM, camera, geometry, measurement acquisition, timers, persistence or Bluetooth.
 * The UI must label these controls "Controles táctiles; instrumento no conectado".
 *
 * createCommandController({
 *   targets: {navigation:['azimuth','zoom'], editing:['selected-piece'], measurement:['manual-reading']},
 *   onNavigate(command), onEdit(command), onAccept(command), onManualMeasurementAccept(command)
 * }) -> {getState, setMode, setFocus, dispatch, step, accept}
 *
 * dispatch({type:'step'|'accept', mode, focus, source:'touch', direction?:-1|1})
 * returns {ok, state, result?} or {ok:false, reason, state, error?}.
 * A successful dispatch means the handler was invoked, not that a save or measurement occurred.
 * Handler return values (including promises) are returned unchanged as result; callers own completion.
 * Handlers validate their own UI/field state. Measurement acceptance may ONLY open/confirm manual
 * input through its separately configured handler; this router never supplies a measured value.
 */

export const COMMAND_MODES = Object.freeze(['navigation', 'editing', 'measurement']);
export const TOUCH_CONTROL_LABEL = 'Controles táctiles; instrumento no conectado';
const isMode = value => typeof value === 'string' && COMMAND_MODES.includes(value);
const isTarget = value => typeof value === 'string' && /^[A-Za-z][A-Za-z0-9_.:-]{0,79}$/.test(value);
const plainRecord = value => value !== null && typeof value === 'object' && !Array.isArray(value)
  && [Object.prototype, null].includes(Object.getPrototypeOf(value));

export function createCommandController(options = {}) {
  if (!plainRecord(options)) throw new TypeError('Command controller options must be an object.');
  const targetsInput = options.targets ?? {};
  if (!plainRecord(targetsInput) || Object.keys(targetsInput).some(key => !isMode(key)))
    throw new TypeError('Targets must contain only navigation, editing and measurement lists.');
  const targets = new Map();
  for (const mode of COMMAND_MODES) {
    const list = Object.prototype.hasOwnProperty.call(targetsInput, mode) ? targetsInput[mode] : [];
    if (!Array.isArray(list) || list.some(target => !isTarget(target)) || new Set(list).size !== list.length)
      throw new TypeError(`Invalid or duplicate ${mode} targets.`);
    targets.set(mode, new Set(list)); // Copy configuration: later caller mutation cannot widen the allowlist.
  }
  const handlers = {};
  for (const name of ['onNavigate', 'onEdit', 'onAccept', 'onManualMeasurementAccept']) {
    if (options[name] !== undefined && typeof options[name] !== 'function')
      throw new TypeError(`${name} must be a function when provided.`);
    handlers[name] = options[name];
  }
  let mode = 'navigation';
  let focus = null;
  const getState = () => Object.freeze({mode, focus});
  const rejected = (reason, error) => Object.freeze({ok:false, reason, state:getState(), ...(error ? {error} : {})});
  const accepted = () => Object.freeze({ok:true, state:getState()});

  function setMode(next) {
    if (!isMode(next)) return rejected('invalid-mode');
    if (mode !== next) { mode = next; focus = null; }
    return accepted();
  }
  function setFocus(next) {
    if (next !== null && (!isTarget(next) || !targets.get(mode).has(next))) return rejected('invalid-focus');
    focus = next;
    return accepted();
  }
  function dispatch(event) {
    if (!plainRecord(event)) return rejected('invalid-event');
    // Reject accessors, symbols and hidden fields instead of reading arbitrary payload properties.
    const fields = Object.getOwnPropertyDescriptors(event);
    const keys = Reflect.ownKeys(fields);
    if (keys.some(key => typeof key !== 'string' || !Object.prototype.hasOwnProperty.call(fields[key], 'value')))
      return rejected('invalid-event');
    const type = fields.type?.value;
    if (type !== 'step' && type !== 'accept') return rejected('invalid-type');
    const required = type === 'step' ? ['type','mode','focus','source','direction'] : ['type','mode','focus','source'];
    if (keys.length !== required.length || required.some(key => !Object.prototype.hasOwnProperty.call(fields, key)))
      return rejected('invalid-payload');
    const requestedMode = fields.mode.value;
    const requestedFocus = fields.focus.value;
    if (!isMode(requestedMode)) return rejected('invalid-mode');
    if (fields.source.value !== 'touch') return rejected('unsupported-source');
    if (requestedMode !== mode) return rejected('stale-mode');
    if (requestedFocus !== focus) return rejected('stale-focus');
    if (type === 'step' && fields.direction.value !== -1 && fields.direction.value !== 1)
      return rejected('invalid-direction');
    // Hard isolation: a wheel-like step must NEVER reach UI/camera/editing callbacks while measuring.
    if (type === 'step' && mode === 'measurement') return rejected('measurement-step-blocked');
    if (focus === null) return rejected('no-focus');
    if (!targets.get(mode).has(focus)) return rejected('invalid-focus');
    const handlerName = type === 'step' ? (mode === 'navigation' ? 'onNavigate' : 'onEdit')
      : (mode === 'measurement' ? 'onManualMeasurementAccept' : 'onAccept');
    const handler = handlers[handlerName];
    if (!handler) return rejected('handler-unavailable');
    const command = Object.freeze({type, mode, focus, source:'touch',
      ...(type === 'step' ? {direction:fields.direction.value} : {})});
    try {
      const result = handler(command);
      return Object.freeze({ok:true, state:getState(), result});
    } catch (error) {
      // A handler may already have changed its own UI; this is not a transactional rollback.
      return rejected('handler-error', error);
    }
  }
  const step = direction => dispatch({type:'step', mode, focus, source:'touch', direction});
  const accept = () => dispatch({type:'accept', mode, focus, source:'touch'});
  return Object.freeze({getState, setMode, setFocus, dispatch, step, accept});
}
