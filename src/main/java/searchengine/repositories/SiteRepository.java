package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    @Query(value = "SELECT * FROM `site` WHERE `status` = 'INDEXING'", nativeQuery = true)
    List<SiteEntity> findIndexing();

    @Query(value = "SELECT * FROM `site` WHERE `url` = :siteUrl LIMIT 1", nativeQuery = true)
    SiteEntity findByUrl(String siteUrl);
}