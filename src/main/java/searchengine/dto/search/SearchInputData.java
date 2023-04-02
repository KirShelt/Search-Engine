package searchengine.dto.search;

import lombok.Data;

@Data
public class SearchInputData {
   String query;
   String site;
   int offset;
   int limit;
}
