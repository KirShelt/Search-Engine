package searchengine.dto.search;

import lombok.Data;

@Data
public class DetailedSearchItem {
    String site;
    String siteName;
    String uri;
    String title;
    String snippet;
    float relevance;
}
