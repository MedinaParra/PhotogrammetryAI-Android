package cl.skm.pulleyai;

import android.content.Context;

import java.lang.reflect.Field;

/** Transitional package-local bridge until capture persistence is moved behind a repository interface. */
final class CaptureStoreContextResolver {
    private CaptureStoreContextResolver() {
    }

    static Context resolve(CaptureStore store) {
        if (store == null) return null;
        try {
            Field field = CaptureStore.class.getDeclaredField("appContext");
            field.setAccessible(true);
            Object value = field.get(store);
            return value instanceof Context ? (Context) value : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
