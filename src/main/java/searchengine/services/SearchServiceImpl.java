package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.search.DetailedSearchItem;
import searchengine.dto.search.SearchInputData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;

    private static final int SNIPPETLENGTH = 250;

    @Override
    public SearchResponse search(SearchInputData searchInputData) {
        SearchResponse searchResponse = new SearchResponse();
        if (searchInputData.getQuery().equals("")) {
            searchResponse.setError("Пустой поисковый запрос.");
            return searchResponse;
        }

        List<SiteEntity> siteList = new ArrayList<>();
        if (searchInputData.getSite() != null) {
            siteList.add(siteRepository.findByUrl(searchInputData.getSite()));
        } else siteList.addAll(siteRepository.findAll());

        try {
            LemmaFinder lemmaFinder = LemmaFinder.getInstance();
            Set<String> lemmaList = lemmaFinder.collectLemmas(searchInputData.getQuery()).keySet();

            HashMap<PageEntity, Float> absoluteRelevancyMap = new HashMap<>();
            for (SiteEntity siteN : siteList) {
                Map<PageEntity, Float> relevancyOnSite = getAbsoluteRelevancyMapOnSite(siteN.getId(), lemmaList);
                if (relevancyOnSite != null) absoluteRelevancyMap.putAll(relevancyOnSite);
            }
            LinkedHashMap<PageEntity, Float> relativeRelevancyMap = getSortedRelativeRelevancy(absoluteRelevancyMap);

            return createSearchResponse(searchInputData.getOffset(), searchInputData.getLimit(),
                    relativeRelevancyMap, lemmaList);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return searchResponse;
    }

    private Map<PageEntity, Float> getAbsoluteRelevancyMapOnSite(int siteId, Set<String> lemmas) {
        List<LemmaEntity> lemmaEntityList = new ArrayList<>();
        for (String lemma : lemmas) {
            LemmaEntity lemmaEntity = lemmaRepository.findBySiteAndLemma(siteId, lemma);
            if (lemmaEntity == null) {
                return null;
            } else lemmaEntityList.add(lemmaEntity);
        }

        LemmaFrequencyComparator lemmaFrequencyComparator = new LemmaFrequencyComparator();
        lemmaEntityList.sort(lemmaFrequencyComparator);

        List<PageEntity> pageEntitySet = new ArrayList<>();
        lemmaEntityList.get(0).getIndexEntitySet().forEach(index ->
                pageEntitySet.add(index.getPageId()));

        List<PageEntity> tempPageEntitySet = new ArrayList<>();
        for (int i = 1; i < lemmaEntityList.size(); i++) {
            lemmaEntityList.get(i).getIndexEntitySet().forEach(index ->
                    tempPageEntitySet.add(index.getPageId()));
            pageEntitySet.retainAll(tempPageEntitySet);
        }
//                pageEntitySet!!!
//                lemmaEntityList!!!
        if (pageEntitySet.size() < 1) {
            return null;
        }

        HashMap<PageEntity, Float> pageRankingMap = new HashMap<>();
        pageEntitySet.forEach(p -> {
            pageRankingMap.put(p, 0f);
            lemmaEntityList.forEach(l -> {
                float rank = indexRepository.findContains(l.getId(), p.getId());
                pageRankingMap.put(p, pageRankingMap.get(p) + rank);
            });
        });
        return pageRankingMap;
    }

    private LinkedHashMap<PageEntity, Float> getSortedRelativeRelevancy(
            Map<PageEntity, Float> unsortedAbsoluteRelevancyMap) {

        if (unsortedAbsoluteRelevancyMap == null || unsortedAbsoluteRelevancyMap.size() == 0) return null;

        float maxRank = Collections.max(unsortedAbsoluteRelevancyMap.values());
        if (maxRank == 0) return null;

        unsortedAbsoluteRelevancyMap.keySet().forEach(p ->
                unsortedAbsoluteRelevancyMap.replace(p, unsortedAbsoluteRelevancyMap.get(p) / maxRank));

        return unsortedAbsoluteRelevancyMap.entrySet().stream()
                .sorted(Comparator.comparingDouble(e -> -e.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> {
                            throw new AssertionError();
                        },
                        LinkedHashMap::new));
    }

    private SearchResponse createSearchResponse(int offset, int limit,
                                                Map<PageEntity, Float> relativeRelevancyMap, Set<String> queryLemmaList) {
        SearchResponse response = new SearchResponse();
        response.setResult(true);
        if (relativeRelevancyMap != null) {
            response.setCount(relativeRelevancyMap.size());
            List<DetailedSearchItem> items = new ArrayList<>();
            List<PageEntity> resultsList = relativeRelevancyMap.keySet().stream().skip(offset).limit(limit).toList();
            for (PageEntity page : resultsList) {
                DetailedSearchItem item = new DetailedSearchItem();
                SiteEntity site = page.getSiteId();
                item.setSite(site.getUrl().substring(0, site.getUrl().length() - 1));
                item.setSiteName(site.getName());
                item.setUri(page.getPath());
                Document doc = Jsoup.parse(page.getContent());
                item.setTitle(doc.title());
                item.setSnippet(snippetFormation(doc, queryLemmaList));
                item.setRelevance(relativeRelevancyMap.get(page));
                items.add(item);
            }
            response.setData(items);
        } else {
            response.setCount(0);
            response.setData(new ArrayList<>());
        }
        return response;
    }

    private String snippetFormation(Document doc, Set<String> queryLemmaList) {
        LemmaFinder lemmaFinder;
        try {
            lemmaFinder = LemmaFinder.getInstance();
//            doc = lemmaFinder.removeScriptsAndStyles(doc);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LemmaUtils util = new LemmaUtils();
        String text = util.removeScriptsAndStyles(doc).text();
//        String text = "Мама мыла раму, 1234баба мыла маму.";
        if (queryLemmaList.size() < 1 || text.length() < 1) return "";
        HashMap<Integer, String> words = findRussianWordsMap(text);
        TreeMap<Integer, String> pageUsefulWordMap = new TreeMap<>();
        for (int i : words.keySet()) {
            String word = words.get(i);
            if (word.isBlank()) continue;
            Set<String> currentWordLemmas = lemmaFinder.collectLemmas(word).keySet();
            currentWordLemmas.retainAll(queryLemmaList);
            if (currentWordLemmas.size() == 0) continue;
            pageUsefulWordMap.put(i, word);
        }
        return findBestUsefulFragment(pageUsefulWordMap, text);
    }

    private String findBestUsefulFragment(TreeMap<Integer, String> words, String text) {
        List<Integer> wordsPositions = new ArrayList<>(words.keySet());
        int startCurPosition = words.firstKey();
        int endCurPosition = startCurPosition + words.get(startCurPosition).length();
        int startBestPosition = startCurPosition;
        int endBestPosition = endCurPosition;
        int usefulCurRank = 1;
        int usefulBestRank = 1;
        int starti = 0;
        for (int endi = 1; endi < wordsPositions.size(); endi++) {
            int position = wordsPositions.get(endi);
            endCurPosition = position + words.get(position).length();
            usefulCurRank++;
            while (endCurPosition - startCurPosition > SNIPPETLENGTH) {
                starti++;
                startCurPosition = wordsPositions.get(starti);
                usefulCurRank--;
            }
            if (usefulCurRank > usefulBestRank) {
                usefulBestRank = usefulCurRank;
                startBestPosition = startCurPosition;
                endBestPosition = endCurPosition;
            }
        }
        startBestPosition = Math.max(0, (startBestPosition + endBestPosition - SNIPPETLENGTH) / 2);
        endBestPosition = Math.min(startBestPosition + SNIPPETLENGTH, text.length() - 1);
        StringBuilder resultString = new StringBuilder();
        resultString.append("...");
        Set<Integer> wordsSnippetPosition = words.subMap(startBestPosition, endBestPosition).keySet();
        startCurPosition = startBestPosition;
        for (Integer p : wordsSnippetPosition) {
            resultString.append(text, startCurPosition, p).append("<B>");
            startCurPosition = p + words.get(p).length();
            resultString.append(text, p, startCurPosition).append("</B>");
        }
        resultString.append(text, startCurPosition, endBestPosition).append("...");
        return resultString.toString();
    }

    private HashMap<Integer, String> findRussianWordsMap(String text) {
        text = text.toLowerCase();
        HashMap<Integer, String> matches = new HashMap<>();
        Matcher m = Pattern.compile("[ёа-я]+").matcher(text);
        while (m.find()) {
            matches.put(m.start(), m.group());
        }
        return matches;
    }
}

class LemmaFrequencyComparator implements Comparator<LemmaEntity> {
    public int compare(LemmaEntity a, LemmaEntity b) {
        return a.getFrequency() - b.getFrequency();
    }
}