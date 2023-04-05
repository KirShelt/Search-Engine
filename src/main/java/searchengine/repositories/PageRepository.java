package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Long> {

    PageEntity findFirstBySiteIdAndPath(SiteEntity siteId, String path);

    int countBySiteId(SiteEntity siteId);
}