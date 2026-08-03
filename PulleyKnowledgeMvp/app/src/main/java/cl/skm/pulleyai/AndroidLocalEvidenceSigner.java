package cl.skm.pulleyai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/** Attempts StrongBox and Android Keystore first, then an app-private software key. */
public final class AndroidLocalEvidenceSigner {
    private static final String PREFS = "local_evidence_signer_v1";
    private static final String PUBLIC_KEY = "public_key";
    private static final String PRIVATE_KEY = "private_key";
    private static final String STRONGBOX_ALIAS = "skm.local.evidence.ed25519.strongbox.v1";
    private static final String KEYSTORE_ALIAS = "skm.local.evidence.ed25519.keystore.v1";

    private AndroidLocalEvidenceSigner() {}

    public static LocalEvidenceSignatureCore.Result sign(Context context, String payload) {
        StringBuilder failures = new StringBuilder();
        if (context == null) {
            return LocalEvidenceSignatureCore.unavailable(payload, "CONTEXT_MISSING");
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                KeyPair keyPair = androidKeyPair(STRONGBOX_ALIAS, true);
                boolean hardware = hardwareBacked(keyPair.getPrivate());
                return LocalEvidenceSignatureCore.sign(payload, keyPair,
                        "ANDROID_KEYSTORE_STRONGBOX", hardware, true);
            } catch (Exception error) {
                append(failures, "STRONGBOX:" + error.getClass().getSimpleName());
            }
        }
        try {
            KeyPair keyPair = androidKeyPair(KEYSTORE_ALIAS, false);
            boolean hardware = hardwareBacked(keyPair.getPrivate());
            return LocalEvidenceSignatureCore.sign(payload, keyPair,
                    "ANDROID_KEYSTORE", hardware, false);
        } catch (Exception error) {
            append(failures, "KEYSTORE:" + error.getClass().getSimpleName());
        }
        try {
            KeyPair keyPair = softwareKeyPair(context.getApplicationContext());
            return LocalEvidenceSignatureCore.sign(payload, keyPair,
                    "SOFTWARE_APP_PRIVATE", false, false);
        } catch (Exception error) {
            append(failures, "SOFTWARE:" + error.getClass().getSimpleName());
        }
        return LocalEvidenceSignatureCore.unavailable(payload,
                failures.length() == 0 ? "ED25519_UNAVAILABLE" : failures.toString());
    }

    private static KeyPair androidKeyPair(String alias, boolean strongBox) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(alias)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519", "AndroidKeyStore");
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setUserAuthenticationRequired(false);
            if (strongBox && Build.VERSION.SDK_INT >= 28) builder.setIsStrongBoxBacked(true);
            generator.initialize(builder.build());
            generator.generateKeyPair();
            store.load(null);
        }
        PrivateKey privateKey = (PrivateKey) store.getKey(alias, null);
        Certificate certificate = store.getCertificate(alias);
        if (privateKey == null || certificate == null) {
            throw new IllegalStateException("keystore key pair missing");
        }
        return new KeyPair(certificate.getPublicKey(), privateKey);
    }

    private static boolean hardwareBacked(PrivateKey privateKey) {
        try {
            KeyFactory factory = KeyFactory.getInstance(privateKey.getAlgorithm(), "AndroidKeyStore");
            KeyInfo info = factory.getKeySpec(privateKey, KeyInfo.class);
            return info != null && info.isInsideSecureHardware();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static KeyPair softwareKeyPair(Context context) throws Exception {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String publicEncoded = preferences.getString(PUBLIC_KEY, "");
        String privateEncoded = preferences.getString(PRIVATE_KEY, "");
        KeyFactory factory = keyFactory();
        if (!publicEncoded.isEmpty() && !privateEncoded.isEmpty()) {
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(
                    LocalEvidenceSignatureCore.decodeBase64(publicEncoded)));
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(
                    LocalEvidenceSignatureCore.decodeBase64(privateEncoded)));
            return new KeyPair(publicKey, privateKey);
        }
        KeyPair pair = generator().generateKeyPair();
        if (pair.getPrivate().getEncoded() == null || pair.getPublic().getEncoded() == null) {
            throw new IllegalStateException("software key is not exportable");
        }
        boolean stored = preferences.edit()
                .putString(PUBLIC_KEY, LocalEvidenceSignatureCore.encodeBase64(pair.getPublic().getEncoded()))
                .putString(PRIVATE_KEY, LocalEvidenceSignatureCore.encodeBase64(pair.getPrivate().getEncoded()))
                .commit();
        if (!stored) throw new IllegalStateException("software key persistence failed");
        return pair;
    }

    private static KeyPairGenerator generator() throws Exception {
        try { return KeyPairGenerator.getInstance("Ed25519"); }
        catch (Exception first) { return KeyPairGenerator.getInstance("EdDSA"); }
    }

    private static KeyFactory keyFactory() throws Exception {
        try { return KeyFactory.getInstance("Ed25519"); }
        catch (Exception first) { return KeyFactory.getInstance("EdDSA"); }
    }

    private static void append(StringBuilder builder, String value) {
        if (builder.length() > 0) builder.append(';');
        builder.append(value);
    }
}
