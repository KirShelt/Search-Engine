package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.PageData;
import searchengine.model.*;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    private final ObjectProvider<SiteIndexer> provider;

    @Autowired
    private RepositoriesMicroServices repositoriesMicroServices;

    static volatile boolean indexing;

    @Override
    public IndexingResponse startIndexing() {
        IndexingResponse response = new IndexingResponse();
        response.setResult(!indexing);

        if (!indexing) {
            indexing = true;
            PageIndexerFJP.results.clear();
            List<Site> sitesList = sites.getSites();
            for (Site site : sitesList) {
                SiteIndexer indexer = provider.getIfUnique();
                assert indexer != null;
                indexer.setSite(site);
                new Thread(indexer).start();
            }
        }
        response.setError(!response.isResult() ? "Индексация уже запущена" : null);
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();
        defineIndexingStatus();
        response.setResult(indexing);
        if (indexing) {
            indexing = false;
            List<SiteEntity> currentIndexingSite = siteRepository.findByStatus(IndexStatus.INDEXING);
            for (SiteEntity e : currentIndexingSite) {
                e.setStatus(IndexStatus.FAILED);
                e.setLastError("Индексация остановлена пользователем");
            }
            siteRepository.saveAll(currentIndexingSite);
        }
        response.setError(!response.isResult() ? "Индексация не запущена" : null);
        return response;
    }

    @Override
    public IndexingResponse indexPage(String pageUrl) {
        IndexingResponse response = new IndexingResponse();
        defineIndexingStatus();
        if (indexing) {
            response.setError("Индексация уже запущена");
            return response;
        }
        Site validSite = pageValidation(pageUrl);
        if (validSite == null) {
            response.setError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
            return response;
        }
        indexing = true;
        SiteEntity siteEntity = siteRepository.findFirstByUrl(validSite.getUrl());
        if (siteEntity == null) {
            siteEntity = repositoriesMicroServices.createSiteEntity(validSite);
            siteEntity.setStatus(IndexStatus.FAILED);
            siteEntity.setLastError("Полная индексация не проводилась");
            siteRepository.save(siteEntity);
        }
        PageEntity pageEntity = pageRepository.findFirstBySiteIdAndPath(siteEntity,
                pageUrl.replaceFirst(siteEntity.getUrl(), "/"));

        if (pageEntity != null) {
            List<IndexEntity> pageIndexes = pageEntity.getIndexEntitySet();
            List<LemmaEntity> lemmaIndexes = new ArrayList<>();
            pageIndexes.forEach(pi -> lemmaIndexes.add(pi.getLemmaId()));
            lemmaIndexes.forEach(li -> li.setFrequency(li.getFrequency() - 1));
            lemmaRepository.saveAll(lemmaIndexes);
            pageRepository.delete(pageEntity);
        }
        PageData pageData = repositoriesMicroServices.collectPageDataAndSave(pageUrl, siteEntity);
        pageEntity = pageData.getPageEntity();
        if (pageEntity.getCode() >= 400) {
            response.setError("Ошибка открытия страницы");
            indexing = false;
            return response;
        }
        try {
            repositoriesMicroServices.lemmatizePage(pageEntity);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        indexing = false;
        response.setResult(true);
        return response;
    }

    private Site pageValidation(String url) {
        List<Site> sitesList = sites.getSites();
        for (Site site : sitesList) {
            String currentSiteUrl = site.getUrl();
            if (url.substring(0, Math.min(currentSiteUrl.length(), url.length())).equals(currentSiteUrl)) {
                return site;
            }
        }
        return null;
    }

    private void defineIndexingStatus() {
        List<SiteEntity> currentIndexingSite = siteRepository.findByStatus(IndexStatus.INDEXING);
        indexing = currentIndexingSite.size() != 0;
    }
}