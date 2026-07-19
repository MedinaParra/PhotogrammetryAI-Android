package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only port used by the identification engine.
 *
 * <p>The domain layer does not know whether the catalog is held in memory, SQLite, a test fixture
 * or a future synchronised package. Implementations must return complete immutable families and
 * preserve the bidirectional relation between material codes and OTs.</p>
 */
public interface MaterialKnowledgeCatalog {
    Optional<MaterialFamily> findByMaterialCode(String materialCode);

    List<MaterialFamily> findByOt(String ot);

    Set<String> materialCodesForOt(String ot);

    List<MaterialFamily> families();

    int size();
}
