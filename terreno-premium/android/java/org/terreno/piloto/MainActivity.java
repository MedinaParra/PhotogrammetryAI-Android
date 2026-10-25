package org.terreno.piloto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Offline application shell. No remote content, account, analytics or network permission. */
public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String HOME = ORIGIN + "/assets/index.html";
    // AOSP Settings.ACTION_ZEN_MODE_SETTINGS is not exposed in the public SDK stubs.
    // Send its action string without reflection; OEMs may omit the activity, so always fall back.
    private static final String ACTION_ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS";
    private static final int PICK_FILE = 10, TAKE_PHOTO = 11, SAVE_EXPORT = 12;
    private static final int MAX_EXPORT_BYTES = 40 * 1024 * 1024;
    private static final String STEP_START = "ISO-10303-21;", STEP_END = "END-ISO-10303-21;";
    private WebView web;
    private FrameLayout root;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private File cameraFile, exportFile;
    private String exportKind = "json";
    private boolean exporting, awaitingPicker, backPending;
    // Activity-local presentation only. Never saved to Bundle/preferences or applied to system settings.
    private boolean fieldMode = true;
    private boolean legacyKeyboardVisible, dndSettingsOpened, normalBarsBehaviorCaptured;
    private int legacyUiFlags, normalBarsBehavior;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        fieldMode = true; // Every fresh Activity starts in real immersive presentation, never a saved false state.
        if (saved != null) {
            String name = saved.getString("exportFile");
            String kind = saved.getString("exportKind", "json");
            if (saved.getBoolean("awaitingPicker", false) && validExportKind(kind) && name != null
                    && name.matches("export-[a-f0-9-]{36}\\."+kind)) {
                File pending = new File(getCacheDir(), name);
                if (pending.isFile() && pending.length() > 0 && pending.length() <= MAX_EXPORT_BYTES) {
                    exportFile = pending; exportKind = kind; exporting = true; awaitingPicker = true;
                }
            }
        }
        File[] stale = getCacheDir().listFiles();
        if (stale != null) for (File file: stale)
            if (file.getName().matches("export-[a-f0-9-]{36}\\.(json|step)") && !file.equals(exportFile)) file.delete();
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(245,244,239));
        root.setOnApplyWindowInsetsListener(this::applyContentInsets);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1,-1)); setContentView(root);
        legacyUiFlags = getWindow().getDecorView().getSystemUiVisibility();
        // Own all padding on modern Android, including Android 15's enforced edge-to-edge layout.
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false); settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false); settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true); settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.addJavascriptInterface(new ExportBridge(), "TerrenoAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return "GET".equals(request.getMethod()) ? localAsset(request.getUrl()) : blocked();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !(ORIGIN.equals(uri.getScheme()+"://"+uri.getAuthority()) && "/assets/index.html".equals(uri.getPath()));
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (HOME.equals(url)) notifyFieldMode();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                boolean image = Arrays.stream(params.getAcceptTypes()).anyMatch(t -> t.startsWith("image/"));
                if (params.isCaptureEnabled() && image) takePhoto(); else pickFile(image);
                return true;
            }
        });
        applyFieldMode();
        root.post(this::applyFieldMode); // Reapply after attachment, when an insets controller is available.
        web.loadUrl(HOME);
    }

    private WebResourceResponse localAsset(Uri uri) {
        String name = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/assets/", "");
        String mime;
        switch (name) {
            case "index.html": mime="text/html"; break;
            case "app.js": case "core.js": case "viewer.js": case "compat.js":
            case "premium-viewer.js": case "vendor/three/three.module.js": case "vendor/three/three.core.js":
            case "photo-analysis.js": case "structure-analysis.js": case "templates.js": case "command-controller.js": case "step-export.js":
                mime="text/javascript"; break;
            case "style.css": case "premium.css": mime="text/css"; break;
            case "vendor/inter/InterVariable.woff2": mime="font/woff2"; break;
            default: return blocked();
        }
        if (!"https".equals(uri.getScheme()) || !"appassets.androidplatform.net".equals(uri.getAuthority()) ||
                !("/assets/"+name).equals(uri.getEncodedPath()) || uri.getQuery() != null || uri.getFragment() != null) return blocked();
        try {
            InputStream bytes = getAssets().open(name);
            Map<String,String> headers = new HashMap<>();
            headers.put("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self'; worker-src 'none'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'");
            headers.put("X-Content-Type-Options", "nosniff");
            headers.put("Cache-Control", "no-store");
            return new WebResourceResponse(mime, "font/woff2".equals(mime) ? null : "UTF-8", 200, "OK", headers, bytes);
        } catch (Exception e) { return blocked(); }
    }
    private WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
    }
    private void pickFile(boolean image) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(image ? "image/*" : "*/*"); // JSON provider MIME support varies; schema is validated in JS.
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try { startActivityForResult(intent, PICK_FILE); }
        catch (ActivityNotFoundException | SecurityException e) { finishFile(null); message("No hay selector de archivos disponible en este teléfono."); }
    }
    private void takePhoto() {
        try {
            File directory = new File(getCacheDir(), "camera");
            if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException();
            File[] old = directory.listFiles();
            if (old != null) for (File file: old) if (file.getName().matches("[a-f0-9-]{36}\\.jpg")) file.delete();
            cameraFile = new File(directory, UUID.randomUUID()+".jpg");
            cameraUri = Uri.parse("content://org.terreno.piloto.camera/capture/"+cameraFile.getName());
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.setClipData(ClipData.newRawUri("Foto Terreno", cameraUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, TAKE_PHOTO);
        } catch (Exception e) {
            cameraUri=null; cameraFile=null; finishFile(null);
            message("No se pudo abrir la cámara. Usa Galería o toma una foto con la cámara del teléfono.");
        }
    }
    private void finishFile(Uri uri) {
        if (fileCallback != null) { fileCallback.onReceiveValue(uri==null ? null : new Uri[]{uri}); fileCallback=null; }
    }
    private WindowInsets applyContentInsets(View view, WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            // Hidden bars use zero insets; cutouts and the keyboard remain protected in either mode.
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsets.CONSUMED;
        }
        // API 28-29: do not fight the IME with legacy fullscreen flags while a text field is active.
        boolean keyboard = insets.getSystemWindowInsetBottom()
                > Math.round(120 * getResources().getDisplayMetrics().density);
        if (keyboard != legacyKeyboardVisible) {
            legacyKeyboardVisible = keyboard;
            view.post(this::applyFieldMode);
        }
        boolean immersive = fieldMode && !keyboard;
        int left = immersive ? 0 : insets.getSystemWindowInsetLeft();
        int top = immersive ? 0 : insets.getSystemWindowInsetTop();
        int right = immersive ? 0 : insets.getSystemWindowInsetRight();
        int bottom = immersive ? 0 : insets.getSystemWindowInsetBottom();
        DisplayCutout cutout = insets.getDisplayCutout();
        if (cutout != null) {
            left = Math.max(left, cutout.getSafeInsetLeft()); top = Math.max(top, cutout.getSafeInsetTop());
            right = Math.max(right, cutout.getSafeInsetRight()); bottom = Math.max(bottom, cutout.getSafeInsetBottom());
        }
        view.setPadding(left, top, right, bottom);
        return insets.consumeSystemWindowInsets().consumeDisplayCutout();
    }
    private void applyFieldMode() {
        if (isFinishing() || isDestroyed() || root == null) return;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (fieldMode) {
                    if (!normalBarsBehaviorCaptured) {
                        normalBarsBehavior = controller.getSystemBarsBehavior(); normalBarsBehaviorCaptured = true;
                    }
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    controller.hide(WindowInsets.Type.systemBars());
                } else {
                    if (normalBarsBehaviorCaptured) controller.setSystemBarsBehavior(normalBarsBehavior);
                    controller.show(WindowInsets.Type.systemBars());
                }
            }
        } else {
            int flags = legacyUiFlags;
            if (fieldMode && !legacyKeyboardVisible) flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        root.requestApplyInsets();
    }
    private void notifyFieldMode() {
        if (web != null && !isFinishing() && !isDestroyed())
            web.evaluateJavascript("window.terrenoNativeFieldModeChanged && window.terrenoNativeFieldModeChanged("+fieldMode+")", null);
    }
    private void openDndSettingsScreen() {
        if (isFinishing() || isDestroyed()) return;
        // Open settings only: no notification-policy access, DND change, airplane mode, or wake lock.
        try {
            startActivity(new Intent(ACTION_ZEN_MODE_SETTINGS));
            dndSettingsOpened = true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            try {
                startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
                dndSettingsOpened = true;
            } catch (ActivityNotFoundException | SecurityException noSettings) {
                message("No se pudieron abrir los ajustes. Abre Ajustes del teléfono y busca No molestar; Terreno no lo ha cambiado.");
                return;
            }
        }
        message("Terreno no cambia No molestar. Si lo activas, restáuralo luego desde Ajustes; salir del modo terreno no lo desactiva.");
    }
    private final class ExportBridge {
        @JavascriptInterface public void setFieldMode(boolean enabled) {
            runOnUiThread(()-> {
                if (isFinishing() || isDestroyed()) return;
                if (enabled && !fieldMode && Build.VERSION.SDK_INT < 30)
                    legacyUiFlags = getWindow().getDecorView().getSystemUiVisibility();
                fieldMode = enabled;
                applyFieldMode(); notifyFieldMode();
            });
        }
        @JavascriptInterface public void openDndSettings() {
            runOnUiThread(MainActivity.this::openDndSettingsScreen);
        }
        @JavascriptInterface public void exportJson(String name, String json) {
            prepareTextExport("json", name, json);
        }
        @JavascriptInterface public void exportStep(String name, String step) {
            prepareTextExport("step", name, step);
        }
    }
    private static boolean validExportKind(String kind) { return "json".equals(kind) || "step".equals(kind); }
    private static String exportLabel(String kind) { return "step".equals(kind) ? "STEP" : "JSON"; }
    private static String exportMime(String kind) { return "step".equals(kind) ? "application/step" : "application/json"; }
    private static boolean validStepEnvelope(String text) {
        // Transport guard only: CAD/schema/topology correctness is validated by the STEP generator/tests.
        return text.startsWith(STEP_START) && text.endsWith(STEP_END);
    }
    private void prepareTextExport(String kind, String name, String content) {
        if (!validExportKind(kind) || content == null || content.length() == 0
                || content.length() > MAX_EXPORT_BYTES || name == null || name.length() > 512) {
            runOnUiThread(()->exportResult(false,"No se pudo exportar: texto/nombre inválido o límite de 40 MiB excedido.",kind));
            return;
        }
        final String payload = "step".equals(kind) ? content.trim() : content;
        if ("step".equals(kind) && !validStepEnvelope(payload)) {
            runOnUiThread(()->exportResult(false,"STEP inválido: faltan los marcadores ISO-10303-21 de inicio o fin.",kind));
            return;
        }
        runOnUiThread(()-> {
            if (isFinishing() || isDestroyed()) return;
            if (exporting) { exportResult(false,"Ya hay una exportación en curso; termina o cancela el selector abierto.",kind); return; }
            exporting = true; exportKind = kind;
            String safe = name.replaceAll("[^\\p{L}\\p{N}_.-]", "_").replaceFirst("(?i)\\.(json|step|stp)$", "");
            if (safe.isEmpty()) safe = "terreno";
            final String filename = safe.substring(0, Math.min(safe.length(),120))+"."+kind;
            exportFile = new File(getCacheDir(), "export-"+UUID.randomUUID()+"."+kind);
            final File pending = exportFile;
            new Thread(()-> {
                try {
                    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                    if (bytes.length > MAX_EXPORT_BYTES) {
                        runOnUiThread(()-> { clearExport(); exportResult(false,"El archivo "+exportLabel(kind)+" excede 40 MiB en UTF-8. No se guardó.",kind); });
                        return;
                    }
                    try (FileOutputStream output = new FileOutputStream(pending)) {
                        output.write(bytes); output.getFD().sync();
                    }
                } catch (Exception e) {
                    runOnUiThread(()-> { clearExport(); exportResult(false,"No se pudo preparar el archivo "+exportLabel(kind)+". Revisa el espacio disponible.",kind); });
                    return;
                }
                runOnUiThread(()-> {
                    if (isFinishing() || isDestroyed() || exportFile != pending) { pending.delete(); return; }
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(exportMime(kind)); intent.putExtra(Intent.EXTRA_TITLE, filename);
                    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                    try { awaitingPicker=true; startActivityForResult(intent,SAVE_EXPORT); }
                    catch (Exception e) { clearExport(); exportResult(false,"No se pudo abrir el selector para guardar "+exportLabel(kind)+".",kind); }
                });
            },"terreno-export-prepare").start();
        });
    }
    private void clearExport() { if(exportFile!=null)exportFile.delete(); exportFile=null; exporting=false; awaitingPicker=false; }
    private void exportResult(boolean success, String text) {
        exportResult(success,text,exportKind);
    }
    private void exportResult(boolean success, String text, String kind) {
        if (web != null && !isDestroyed()) web.evaluateJavascript("window.terrenoNativeExportResult && window.terrenoNativeExportResult("+success+","+JSONObject.quote(text)+","+JSONObject.quote(kind)+")",null);
        message(text);
    }
    private boolean writeExportToDocument(File pending, Uri destination) {
        try (InputStream input=new FileInputStream(pending); OutputStream output=getContentResolver().openOutputStream(destination,"wt")) {
            if(output==null) throw new java.io.IOException("No stream");
            byte[] buffer=new byte[65536]; int n;
            while((n=input.read(buffer))!=-1) output.write(buffer,0,n);
            output.flush();
        } catch(Exception e) { return false; }
        // True only after BOTH streams close successfully, including provider close/flush errors.
        return true;
    }
    private void message(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request == PICK_FILE) {
            Uri uri = result == RESULT_OK && data != null ? data.getData() : null;
            if (uri != null && !"content".equals(uri.getScheme())) uri=null;
            finishFile(uri);
        } else if (request == TAKE_PHOTO) {
            if (fileCallback == null) message("La captura se interrumpió al reiniciarse la aplicación; la foto no se adjuntó. Vuelve a tomarla.");
            Uri chosen = result==RESULT_OK && cameraFile!=null && cameraFile.length()>0 ? cameraUri : null;
            if(cameraUri!=null) revokeUriPermission(cameraUri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            finishFile(chosen); cameraUri=null;
        } else if (request == SAVE_EXPORT) {
            final String kind = exportKind;
            if (!exporting || !awaitingPicker) {
                exportResult(false,"El selector no corresponde a una exportación pendiente. Vuelve a exportar.",kind); return;
            }
            awaitingPicker=false;
            Uri uri=result==RESULT_OK && data!=null ? data.getData() : null;
            if (uri==null) { clearExport(); exportResult(false,"Exportación "+exportLabel(kind)+" cancelada: no se confirmó un archivo guardado.",kind); return; }
            if (!"content".equals(uri.getScheme()) || exportFile==null || !exportFile.exists()) {
                clearExport(); exportResult(false,"No se pudo recuperar la exportación "+exportLabel(kind)+" pendiente. Vuelve a exportar.",kind); return;
            }
            final File pending=exportFile;
            new Thread(()-> {
                final boolean saved=writeExportToDocument(pending,uri);
                runOnUiThread(()-> { clearExport(); exportResult(saved,saved?"Archivo "+exportLabel(kind)+" guardado. Comprueba la carpeta elegida.":"No se completó la exportación "+exportLabel(kind)+". Puede haber un archivo parcial; vuelve a exportar.",kind); });
            },"terreno-export-write").start();
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if(exportFile!=null && awaitingPicker) {
            out.putString("exportFile",exportFile.getName()); out.putString("exportKind",exportKind); out.putBoolean("awaitingPicker",true);
        }
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        if(backPending) return;
        if(exporting) {
            if(awaitingPicker) new AlertDialog.Builder(this).setTitle("Respaldo pendiente")
                .setMessage("Si el selector se interrumpió, puedes cancelar y volver a exportar. No se confirmará un respaldo guardado.")
                .setNegativeButton("Volver",null).setPositiveButton("Cancelar respaldo",(d,w)-> { clearExport(); exportResult(false,"Respaldo pendiente cancelado. Vuelve a exportar."); }).show();
            else message("Espera a que termine de escribirse el respaldo.");
            return;
        }
        backPending=true;
        web.evaluateJavascript("window.terrenoHandleBack ? window.terrenoHandleBack() : false",handled -> {
            if (isFinishing() || isDestroyed()) { backPending=false; return; }
            if ("true".equals(handled)) { backPending=false; return; }
            new AlertDialog.Builder(this).setTitle("¿Cerrar Terreno?")
                .setMessage("Los cambios guardados quedan en este teléfono. Exporta un JSON con fotos al terminar la jornada.")
                .setNegativeButton("Seguir trabajando",null).setPositiveButton("Cerrar",(d,w)->finish())
                .setOnDismissListener(d->backPending=false).show();
        });
    }
    @Override protected void onPause() { super.onPause(); if(web!=null)web.onPause(); }
    @Override protected void onResume() {
        super.onResume(); if(web!=null)web.onResume();
        if (root != null) root.post(()-> { applyFieldMode(); notifyFieldMode(); });
        if (dndSettingsOpened) {
            dndSettingsOpened = false;
            message("Si cambiaste No molestar, restáuralo desde Ajustes cuando termines. Terreno solo restaura su pantalla completa.");
        }
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyFieldMode();
    }
    @Override protected void onDestroy() {
        finishFile(null);
        if(web!=null) { web.removeJavascriptInterface("TerrenoAndroid"); web.destroy(); web=null; }
        super.onDestroy();
    }
}
