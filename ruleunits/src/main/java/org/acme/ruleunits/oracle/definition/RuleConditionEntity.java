package org.acme.ruleunits.oracle.definition;

import jakarta.persistence.*;

/**
 * JPA mapping of one ordered, structured condition row. Multiple rows allow a shared rule shape to
 * express a variable number of required activities.
 */
@Entity
@Table(name = "CT_RULE_CONDITION")
public class RuleConditionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ruleConditionSeq")
    @SequenceGenerator(name = "ruleConditionSeq", sequenceName = "CTS_RULE_CONDITION", allocationSize = 1)
    @Column(name = "ID_RULE_CONDITION")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_RULE_DEFINITION", nullable = false)
    private RuleDefinitionEntity definition;

    @Column(name = "POSITION", nullable = false)
    private int position;

    @Column(name = "CONDITION_TYPE", nullable = false, length = 100)
    private String type;

    @Column(name = "OPERATOR", nullable = false, length = 50)
    private String operator;

    @Column(name = "CONDITION_VALUE", length = 1000)
    private String value;

    @Column(name = "NUMERIC_VALUE")
    private Long numericValue;

    protected RuleConditionEntity() {}

    public RuleConditionEntity(int position, String type, String operator, String value,
            Long numericValue) {
        this.position = position;
        this.type = type;
        this.operator = operator;
        this.value = value;
        this.numericValue = numericValue;
    }

    void attachTo(RuleDefinitionEntity owner) { definition = owner; }
    public int getPosition() { return position; }
    public String getType() { return type; }
    public String getOperator() { return operator; }
    public String getValue() { return value; }
    public Long getNumericValue() { return numericValue; }
}
