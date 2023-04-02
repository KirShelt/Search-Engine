package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Long> {

    @Query(value = "SELECT * FROM `page` WHERE `path` = :pageHref AND `site_id` = :siteId", nativeQuery = true)
    PageEntity findBySiteAndPath(int siteId, String pageHref);

    @Query(value = "SELECT count(*) FROM `page` WHERE `site_id` = :siteId", nativeQuery = true)
    int countBySite(int siteId);
}