package cl.ingenieria.photogrammetryai.core.foundation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Thread-safe repository used by tests and as a fallback before Room/file persistence is wired. */
public final class InMemoryCadReferenceRepository implements CorePorts.CadReferenceRepository {
    private final Map<String, CadReferenceModel> byId = new LinkedHashMap<>();
    private final Map<String, CadReferenceModel> bySha256 = new LinkedHashMap<>();

    @Override
    public synchronized void put(CadReferenceModel model) {
        Objects.requireNonNull(model, "model");
        byId.put(model.id(), model);
        bySha256.put(model.sourceSha256(), model);
    }

    @Override
    public synchronized Optional<CadReferenceModel> findById(String referenceId) {
        return Optional.ofNullable(byId.get(referenceId));
    }

    @Override
    public synchronized Optional<CadReferenceModel> findBySha256(String sourceSha256) {
        return Optional.ofNullable(bySha256.get(sourceSha256));
    }

    public synchronized int size() {
        return byId.size();
    }

    public synchronized void clear() {
        byId.clear();
        bySha256.clear();
    }
}
