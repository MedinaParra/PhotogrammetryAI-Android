// Small compatibility shims; do not substitute Math.random for UUID generation.
if (!Object.hasOwn) Object.hasOwn = (object, key) => Object.prototype.hasOwnProperty.call(object, key);
if (globalThis.crypto && !crypto.randomUUID) crypto.randomUUID = () => {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
};
if (!globalThis.HTMLDialogElement || !HTMLDialogElement.prototype.showModal) {
  document.body.innerHTML = '<main style="padding:30px"><h1>Actualiza Android System WebView</h1><p>Terreno necesita una versión moderna de Android System WebView (Chromium 100 o posterior). Actualiza ese componente o Chrome desde la tienda del teléfono y vuelve a abrir la aplicación.</p><p>No borres los datos de Terreno para actualizar WebView.</p></main>';
}
