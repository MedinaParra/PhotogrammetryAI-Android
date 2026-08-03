package cl.skm.pulleyai;

/** Cooperative cancellation and monotonic deadline control for bounded runtime work. */
public final class RuntimeExecutionControlCore {
    private RuntimeExecutionControlCore() {}

    public enum State { RUNNING, CANCELLED, TIMED_OUT }

    public static Token start(long budgetMs) {
        return startAt(monotonicMs(), budgetMs);
    }

    public static Token startAt(long startedMonotonicMs, long budgetMs) {
        long bounded = Math.max(1_000L, Math.min(10L * 60L * 1_000L, budgetMs));
        return new Token(startedMonotonicMs, startedMonotonicMs + bounded, bounded);
    }

    public static final class Token {
        public final long startedMonotonicMs;
        public final long deadlineMonotonicMs;
        public final long budgetMs;
        private volatile State state = State.RUNNING;
        private volatile String reason = "RUNNING";
        private volatile String stage = "CREATED";

        private Token(long startedMonotonicMs, long deadlineMonotonicMs, long budgetMs) {
            this.startedMonotonicMs = startedMonotonicMs;
            this.deadlineMonotonicMs = deadlineMonotonicMs;
            this.budgetMs = budgetMs;
        }

        public void cancel(String cancellationReason) {
            if (state != State.RUNNING) return;
            state = State.CANCELLED;
            reason = clean(cancellationReason, "USER_CANCELLED");
        }

        public void checkpoint(String currentStage) {
            checkpointAt(monotonicMs(), currentStage);
        }

        public void checkpointAt(long nowMonotonicMs, String currentStage) {
            stage = clean(currentStage, "UNSPECIFIED");
            if (state == State.CANCELLED) throw new AbortedException(state, reason, stage);
            if (state == State.TIMED_OUT || nowMonotonicMs > deadlineMonotonicMs) {
                state = State.TIMED_OUT;
                reason = "RUNTIME_DEADLINE_EXCEEDED";
                throw new AbortedException(state, reason, stage);
            }
            if (Thread.currentThread().isInterrupted()) {
                state = State.CANCELLED;
                reason = "THREAD_INTERRUPTED";
                throw new AbortedException(state, reason, stage);
            }
        }

        public State state() { return state; }
        public String reason() { return reason; }
        public String stage() { return stage; }
        public boolean running() { return state == State.RUNNING; }
        public boolean aborted() { return state != State.RUNNING; }
        public long remainingMs(long nowMonotonicMs) {
            return Math.max(0L, deadlineMonotonicMs - nowMonotonicMs);
        }

        public String canonicalJson(long nowMonotonicMs) {
            return "{\"state\":\"" + state + "\",\"reason\":\"" + escape(reason)
                    + "\",\"stage\":\"" + escape(stage) + "\",\"budgetMs\":" + budgetMs
                    + ",\"elapsedMs\":" + Math.max(0L, nowMonotonicMs - startedMonotonicMs)
                    + ",\"remainingMs\":" + remainingMs(nowMonotonicMs) + "}";
        }
    }

    public static final class AbortedException extends RuntimeException {
        public final State state;
        public final String reason;
        public final String stage;

        AbortedException(State state, String reason, String stage) {
            super(reason + " @ " + stage);
            this.state = state;
            this.reason = reason;
            this.stage = stage;
        }
    }

    private static long monotonicMs() {
        return System.nanoTime() / 1_000_000L;
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}