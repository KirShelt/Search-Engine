package searchengine.dto.indexing;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import searchengine.model.PageEntity;
import org.jsoup.nodes.Document;

@Data
@Getter
@Setter
public class PageData {
    private PageEntity pageEntity;
    private Document document;
}
