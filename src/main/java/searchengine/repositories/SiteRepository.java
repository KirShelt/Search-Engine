package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import searchengine.model.IndexStatus;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    List<SiteEntity> findByStatus(IndexStatus status);

    SiteEntity findFirstByUrl(String url);
}