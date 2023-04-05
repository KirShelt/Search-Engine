package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import searchengine.config.ConnectionSettings;
import searchengine.config.Site;
import searchengine.dto.indexing.PageData;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RepositoriesMicroServices {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final ConnectionSettings connectionSettings;

    SiteEntity createSiteEntity(Site site) {
        SiteEntity newSite = new SiteEntity();
        newSite.setUrl(site.getUrl());
        newSite.setName(site.getName());
        newSite.setStatus(IndexStatus.INDEXING);
        newSite.setStatusTime(new Date());
        newSite.setLastError("");
        siteRepository.save(newSite);
        return newSite;
    }

    void clearSiteIfExists(String url) {
        SiteEntity currentIndexingSite = siteRepository.findFirstByUrl(url);
        if (currentIndexingSite != null) siteRepository.delete(currentIndexingSite);
    }

    void setSiteIndexedStatus(SiteEntity newSite) {
        if (IndexingServiceImpl.indexing) {
            newSite.setStatus(IndexStatus.INDEXED);
            siteRepository.save(newSite);
        }
    }

    PageEntity createPageEntityAndSave(int statusCode, String pageContent,
                                       SiteEntity parentSite, String fullPath) {
        PageEntity newPage = new PageEntity();
        newPage.setCode(statusCode);
        newPage.setContent(pageContent);
        String domainUrl = parentSite.getUrl();
        newPage.setPath(fullPath.replaceFirst(domainUrl, "/"));
        newPage.setSiteId(parentSite);
        pageRepository.save(newPage);
        parentSite.setStatusTime(new Date());
        siteRepository.save(parentSite);
        return newPage;
    }

    PageData collectPageDataAndSave(String fullUrl, SiteEntity site) {
        PageData pageResponse = new PageData();
        try {
            Connection.Response response = Jsoup.connect(fullUrl).userAgent(connectionSettings.getUserAgent()).
                    referrer(connectionSettings.getReferrer()).ignoreHttpErrors(true).timeout(15000).execute();
            Document doc = response.parse();
            pageResponse.setDocument(doc);
            int statusCode = response.statusCode();
            String pageContent = (statusCode < 400) ? doc.toString() : "";
            if (IndexingServiceImpl.indexing)
                pageResponse.setPageEntity(createPageEntityAndSave(statusCode, pageContent, site, fullUrl));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return pageResponse;
    }

    void lemmatizePage(PageEntity pageId) throws IOException {
        if (!IndexingServiceImpl.indexing) return;
        LemmaFinder lemmaFinder = LemmaFinder.getInstance();

        Document doc = Jsoup.parse(pageId.getContent());
        LemmaUtils util = new LemmaUtils();
        String text = util.removeScriptsAndStyles(doc).text();
        HashMap<String, Integer> map = lemmaFinder.collectLemmas(text);
        List<IndexEntity> indexEntityList = new ArrayList<>();
        for (String key : map.keySet()) {
            if (!IndexingServiceImpl.indexing) break;
            synchronized (lemmaRepository) {
                LemmaEntity lemmaEntity = lemmaRepository.findFirstBySiteIdAndLemma(pageId.getSiteId(), key);
                if (lemmaEntity == null) {
                    lemmaEntity = new LemmaEntity();
                    lemmaEntity.setLemma(key);
                    lemmaEntity.setSiteId(pageId.getSiteId());
                    lemmaEntity.setFrequency(1);
                } else {
                    lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
                }
                lemmaRepository.save(lemmaEntity);

                IndexEntity indexEntity = new IndexEntity();
                indexEntity.setPageId(pageId);
                indexEntity.setLemmaId(lemmaEntity);
                indexEntity.setRank(map.get(key));
                indexEntityList.add(indexEntity);
            }
        }
        indexRepository.saveAll(indexEntityList);
    }
}