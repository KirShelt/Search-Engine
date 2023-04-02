package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Entity
@Table(name = "site")
@NoArgsConstructor
@Getter
@Setter
public class SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM ('INDEXING', 'INDEXED', 'FAILED') NOT NULL")
    private IndexStatus status;

    @Column(name = "status_time", columnDefinition = "DATETIME NOT NULL")
    private Date statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    String lastError;

    @Column(columnDefinition = "VARCHAR(255) NOT NULL UNIQUE")
    String url;

    @Column(columnDefinition = "VARCHAR(255) NOT NULL UNIQUE")
    String name;

    @OneToMany (mappedBy = "siteId", cascade = CascadeType.REMOVE)
    private List<PageEntity> pageEntitySet;

    @OneToMany (mappedBy = "siteId", cascade = CascadeType.REMOVE)
    private List<LemmaEntity> lemmaEntitySet;
}