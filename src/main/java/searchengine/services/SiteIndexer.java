package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.model.SiteEntity;

import java.util.concurrent.ForkJoinPool;

@Component
@Getter
@Setter
@RequiredArgsConstructor
@Scope(value = "prototype")
public class SiteIndexer implements Runnable {
    private Site site;

    private final ObjectProvider<PageIndexerFJP> provider;

    @Autowired
    private RepositoriesMicroServices repositoriesMicroServices;

    @Override
    public void run() {
        repositoriesMicroServices.clearSiteIfExists(site.getUrl());
        SiteEntity newSite = repositoriesMicroServices.createSiteEntity(site);
//        PageIndexerFJP pageIndexerFJP = new PageIndexerFJP();
        PageIndexerFJP pageIndexerFJP = provider.getIfUnique();
        assert pageIndexerFJP !=null;
        pageIndexerFJP.setInitial(newSite, newSite.getUrl());
        new ForkJoinPool(12).invoke(pageIndexerFJP);
        repositoriesMicroServices.setSiteIndexedStatus(newSite);
    }
}