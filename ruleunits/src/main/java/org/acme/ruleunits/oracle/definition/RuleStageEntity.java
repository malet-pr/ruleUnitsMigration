package org.acme.ruleunits.oracle.definition;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA mapping of an ordered RA stage and its generated Rule Unit identity. Stage order is
 * configuration data that validation constrains to RA1 → RA2 → RA3.
 */
@Entity
@Table(name = "CT_RULE_STAGE")
public class RuleStageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ruleStageSeq")
    @SequenceGenerator(name = "ruleStageSeq", sequenceName = "CTS_RULE_STAGE", allocationSize = 1)
    @Column(name = "ID_RULE_STAGE")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_RULE_SET", nullable = false)
    private RuleSetEntity ruleSet;

    @Column(name = "STAGE_CODE", nullable = false, length = 20)
    private String code;

    @Column(name = "STAGE_ORDER", nullable = false)
    private int stageOrder;

    @Column(name = "UNIT_PACKAGE", nullable = false)
    private String unitPackage;

    @Column(name = "UNIT_NAME", nullable = false)
    private String unitName;

    @Column(name = "ACTIVE", nullable = false, length = 1)
    private String active;

    @OneToMany(mappedBy = "stage", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ruleOrder ASC")
    private final List<RuleDefinitionEntity> definitions = new ArrayList<>();

    protected RuleStageEntity() {}

    public RuleStageEntity(String code, int stageOrder, String unitPackage, String unitName) {
        this.code = code;
        this.stageOrder = stageOrder;
        this.unitPackage = unitPackage;
        this.unitName = unitName;
        this.active = "S";
    }

    void attachTo(RuleSetEntity owner) { ruleSet = owner; }

    public void addDefinition(RuleDefinitionEntity definition) {
        definitions.add(definition);
        definition.attachTo(this);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public int getStageOrder() { return stageOrder; }
    public String getUnitPackage() { return unitPackage; }
    public String getUnitName() { return unitName; }
    public boolean isActive() { return "S".equals(active); }
    public List<RuleDefinitionEntity> getDefinitions() { return List.copyOf(definitions); }
}
