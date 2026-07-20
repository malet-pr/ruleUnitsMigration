package org.acme.ruleunits.oracle.definition;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA mapping of one named rule and its ordered conditions and actions within a stage. The entity
 * is persistence configuration and never enters a Rule Unit as a fact.
 */
@Entity
@Table(name = "CT_RULE_DEFINITION")
public class RuleDefinitionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ruleDefinitionSeq")
    @SequenceGenerator(name = "ruleDefinitionSeq", sequenceName = "CTS_RULE_DEFINITION", allocationSize = 1)
    @Column(name = "ID_RULE_DEFINITION")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_RULE_STAGE", nullable = false)
    private RuleStageEntity stage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_RULE_TEMPLATE", nullable = false)
    private RuleTemplateEntity template;

    @Column(name = "RULE_NAME", nullable = false)
    private String name;

    @Column(name = "RULE_ORDER", nullable = false)
    private int ruleOrder;

    @Column(name = "WORK_ORDER_TYPE", length = 20)
    private String workOrderType;

    @Column(name = "JOB_TYPE")
    private String jobType;

    @Column(name = "ACTIVE", nullable = false, length = 1)
    private String active;

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private final List<RuleConditionEntity> conditions = new ArrayList<>();

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private final List<RuleActionEntity> actions = new ArrayList<>();

    protected RuleDefinitionEntity() {}

    public RuleDefinitionEntity(RuleTemplateEntity template, String name, int ruleOrder,
            String workOrderType, String jobType) {
        this.template = template;
        this.name = name;
        this.ruleOrder = ruleOrder;
        this.workOrderType = workOrderType;
        this.jobType = jobType;
        this.active = "S";
    }

    void attachTo(RuleStageEntity owner) { stage = owner; }

    public void addCondition(RuleConditionEntity condition) {
        conditions.add(condition);
        condition.attachTo(this);
    }

    public void addAction(RuleActionEntity action) {
        actions.add(action);
        action.attachTo(this);
    }

    public Long getId() { return id; }
    public RuleTemplateEntity getTemplate() { return template; }
    public String getName() { return name; }
    public int getRuleOrder() { return ruleOrder; }
    public String getWorkOrderType() { return workOrderType; }
    public String getJobType() { return jobType; }
    public boolean isActive() { return "S".equals(active); }
    public List<RuleConditionEntity> getConditions() { return List.copyOf(conditions); }
    public List<RuleActionEntity> getActions() { return List.copyOf(actions); }
}
