package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import searchengine.dto.indexing.PageData;
import searchengine.model.SiteEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;

@RequiredArgsConstructor
@Component
@Scope(value = "prototype")
public class PageIndexerFJP extends RecursiveAction {
    public static ConcurrentHashMap<String, Integer> resultsMap = new ConcurrentHashMap<>();
    public static Set<String> results = resultsMap.newKeySet();
    private SiteEntity siteEntity;

    private final ObjectProvider<PageIndexerFJP> provider;
    private String node;

    @Autowired
    private RepositoriesMicroServices repositoriesMicroServices;

    public void setInitial(SiteEntity siteEntity, String node) {
        this.siteEntity = siteEntity;
        this.node = node;
        results.add(node);
    }

    @Override
    protected void compute() {
        if (!IndexingServiceImpl.indexing) return;
        HashSet<PageIndexerFJP> tasks = new HashSet<>();
        try {
            Thread.sleep((int) (Math.random() * 500 + 500));
            PageData currentPage = repositoriesMicroServices.collectPageDataAndSave(node, siteEntity);
            if (currentPage==null) return;
            repositoriesMicroServices.lemmatizePage(currentPage.getPageEntity());
            Elements hrefElements = currentPage.getDocument().select("a");

            for (Element element : hrefElements) {

                String href = element.attr("href").toLowerCase();
                String fullurl = href;

                String domainUrl = siteEntity.getUrl();

                if ((href.length() > 0) && (href.charAt(0) == '/')) {
                    fullurl = href.replaceFirst("/", domainUrl);
                }
                href = href.replaceFirst(domainUrl, "");
                String regex = "[a-zа-яё0-9_\\-/]+";

                if (IndexingServiceImpl.indexing && href.matches(regex) && (!results.contains(fullurl))) {
                    PageIndexerFJP task = provider.getIfUnique();
                    assert task != null;
                    task.setInitial(siteEntity, fullurl);
                    task.fork();
                    tasks.add(task);
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        for (PageIndexerFJP task : tasks) {
            task.join();
        }
    }
}