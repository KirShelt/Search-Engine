package searchengine.services;

import searchengine.dto.search.SearchInputData;
import searchengine.dto.search.SearchResponse;

public interface SearchService {
    SearchResponse search(SearchInputData searchInputData);
}