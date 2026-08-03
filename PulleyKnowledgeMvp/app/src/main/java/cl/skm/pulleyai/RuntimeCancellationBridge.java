package cl.skm.pulleyai;

/** Thread-local bridge allowing dependency-free geometry cores to expose optional runtime checkpoints. */
public final class RuntimeCancellationBridge {
    private static final ThreadLocal<RuntimeExecutionControlCore.Token> ACTIVE =
            new ThreadLocal<RuntimeExecutionControlCore.Token>();

    private RuntimeCancellationBridge() {}

    public static void install(RuntimeExecutionControlCore.Token token) {
        if (token == null) ACTIVE.remove();
        else ACTIVE.set(token);
    }

    public static void clear() {
        ACTIVE.remove();
    }

    public static void checkpoint(String stage) {
        RuntimeExecutionControlCore.Token token = ACTIVE.get();
        if (token != null) token.checkpoint(stage);
    }

    public static boolean active() {
        return ACTIVE.get() != null;
    }
}