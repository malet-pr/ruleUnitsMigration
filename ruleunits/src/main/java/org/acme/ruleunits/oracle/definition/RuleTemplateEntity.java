package org.acme.ruleunits.oracle.definition;

import jakarta.persistence.*;

/**
 * JPA mapping of a versioned shared traditional DRL template. Templates define supported rule
 * shape while rule rows supply conditions and actions.
 */
@Entity
@Table(name = "CT_RULE_TEMPLATE")
public class RuleTemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ruleTemplateSeq")
    @SequenceGenerator(name = "ruleTemplateSeq", sequenceName = "CTS_RULE_TEMPLATE", allocationSize = 1)
    @Column(name = "ID_RULE_TEMPLATE")
    private Long id;

    @Column(name = "TEMPLATE_KEY", nullable = false, length = 100)
    private String key;

    @Column(name = "TEMPLATE_VERSION", nullable = false)
    private long version;

    @Column(name = "SHAPE", nullable = false, length = 100)
    private String shape;

    @Lob
    @Column(name = "DRL_TEMPLATE", nullable = false)
    private String drlTemplate;

    @Column(name = "ACTIVE", nullable = false, length = 1)
    private String active;

    protected RuleTemplateEntity() {}

    public RuleTemplateEntity(String key, long version, String shape, String drlTemplate) {
        this.key = key;
        this.version = version;
        this.shape = shape;
        this.drlTemplate = drlTemplate;
        this.active = "S";
    }

    public Long getId() { return id; }
    public String getKey() { return key; }
    public long getVersion() { return version; }
    public String getShape() { return shape; }
    public String getDrlTemplate() { return drlTemplate; }
    public boolean isActive() { return "S".equals(active); }
}
