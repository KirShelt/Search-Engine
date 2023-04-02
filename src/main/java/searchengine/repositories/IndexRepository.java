package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity,Long> {

    @Query(value = "SELECT `rank` FROM `index` WHERE `lemma_id` = :lemmaId AND `page_id` = :pageId", nativeQuery = true)
    float findContains(int lemmaId, int pageId);

}