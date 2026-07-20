package org.acme.ruleunits.oracle.definition;

import jakarta.persistence.*;

/**
 * JPA mapping of one ordered, structured action row in an immutable rule-set version. It stores
 * parameters separately from shared DRL templates.
 */
@Entity
@Table(name = "CT_RULE_ACTION")
public class RuleActionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ruleActionSeq")
    @SequenceGenerator(name = "ruleActionSeq", sequenceName = "CTS_RULE_ACTION", allocationSize = 1)
    @Column(name = "ID_RULE_ACTION")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_RULE_DEFINITION", nullable = false)
    private RuleDefinitionEntity definition;

    @Column(name = "POSITION", nullable = false)
    private int position;

    @Column(name = "ACTION_TYPE", nullable = false, length = 100)
    private String type;

    @Column(name = "OLD_ACTIVITY_CODE")
    private String oldActivityCode;

    @Column(name = "NEW_ACTIVITY_CODE")
    private String newActivityCode;

    @Column(name = "CATEGORY")
    private String category;

    protected RuleActionEntity() {}

    public RuleActionEntity(int position, String type, String oldActivityCode,
            String newActivityCode, String category) {
        this.position = position;
        this.type = type;
        this.oldActivityCode = oldActivityCode;
        this.newActivityCode = newActivityCode;
        this.category = category;
    }

    void attachTo(RuleDefinitionEntity owner) { definition = owner; }
    public int getPosition() { return position; }
    public String getType() { return type; }
    public String getOldActivityCode() { return oldActivityCode; }
    public String getNewActivityCode() { return newActivityCode; }
    public String getCategory() { return category; }
}
