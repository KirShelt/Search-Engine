package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

    @Query(value = "SELECT * FROM `lemma` WHERE `lemma` = :lemma AND `site_id` = :siteId", nativeQuery = true)
    LemmaEntity findBySiteAndLemma(int siteId, String lemma);

    @Query(value = "SELECT count(*) FROM `lemma` WHERE `site_id` = :siteId", nativeQuery = true)
    int countBySite(int siteId);
}