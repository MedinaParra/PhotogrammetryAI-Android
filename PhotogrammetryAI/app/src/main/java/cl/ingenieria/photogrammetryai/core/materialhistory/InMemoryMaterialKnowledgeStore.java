package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Deterministic store used by core tests and non-Android tooling. */
public final class InMemoryMaterialKnowledgeStore implements MaterialKnowledgeStore {
    private final Map<String, MaterialFamily> families = new LinkedHashMap<>();
    private final List<IdentificationAudit> audits = new ArrayList<>();

    @Override
    public synchronized MaterialPulleyKnowledgeBase snapshot() {
        MaterialPulleyKnowledgeBase result = new MaterialPulleyKnowledgeBase();
        for (MaterialFamily family : families()) result.register(family);
        return result;
    }

    @Override
    public synchronized void upsertFamily(MaterialFamily family) {
        MaterialFamily previous = families.get(family.materialCode());
        families.put(
                family.materialCode(),
                previous == null ? family : MaterialFamilyMerge.merge(previous, family)
        );
    }

    @Override
    public synchronized void replaceAll(MaterialPulleyKnowledgeBase knowledgeBase) {
        families.clear();
        for (MaterialFamily family : knowledgeBase.families()) families.put(family.materialCode(), family);
        audits.clear();
    }

    @Override
    public synchronized void saveIdentificationAudit(IdentificationAudit audit) {
        audits.removeIf(existing -> existing.sessionId().equals(audit.sessionId()));
        audits.add(audit);
        audits.sort(Comparator.comparingLong(IdentificationAudit::createdAtEpochMs).reversed());
    }

    @Override
    public synchronized List<IdentificationAudit> recentIdentificationAudits(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        int end = Math.min(limit, audits.size());
        return Collections.unmodifiableList(new ArrayList<>(audits.subList(0, end)));
    }

    @Override
    public synchronized Optional<MaterialFamily> findByMaterialCode(String materialCode) {
        if (materialCode == null || materialCode.trim().isEmpty()) return Optional.empty();
        return Optional.ofNullable(families.get(
                MaterialPulleyKnowledgeBase.normalizeMaterialCode(materialCode)
        ));
    }

    @Override
    public synchronized List<MaterialFamily> findByOt(String ot) {
        if (ot == null || ot.trim().isEmpty()) return Collections.emptyList();
        String normalized = MaterialPulleyKnowledgeBase.normalizeOt(ot);
        List<MaterialFamily> result = new ArrayList<>();
        for (MaterialFamily family : families.values()) {
            if (family.ots().contains(normalized)) result.add(family);
        }
        result.sort(Comparator.comparing(MaterialFamily::materialCode));
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized Set<String> materialCodesForOt(String ot) {
        Set<String> result = new LinkedHashSet<>();
        for (MaterialFamily family : findByOt(ot)) result.add(family.materialCode());
        return Collections.unmodifiableSet(result);
    }

    @Override
    public synchronized List<MaterialFamily> families() {
        List<MaterialFamily> result = new ArrayList<>(families.values());
        result.sort(Comparator.comparing(MaterialFamily::materialCode));
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized int size() {
        return families.size();
    }
}
