package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.List;

@Entity
@Table(name = "page")
@NoArgsConstructor
@Getter
@Setter
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "site_id")
    private SiteEntity siteId;

    @Column(columnDefinition = "TEXT NOT NULL, INDEX(path(50))")
    private String path;

    @Column(columnDefinition = "INT NOT NULL")
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT NOT NULL")
    private String content;

    @OneToMany (mappedBy = "pageId")//, cascade = CascadeType.REMOVE)
    private List<IndexEntity> indexEntitySet;
}