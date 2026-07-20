package org.acme.ruleunits.oracle.definition;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA aggregate root for one immutable, versioned rule set. Activation requires prior validation,
 * and the database permits only one active version per rule-set name.
 */
@Entity
@Table(name = "CT_RULE_SET")
public class RuleSetEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ruleSetSeq")
    @SequenceGenerator(name = "ruleSetSeq", sequenceName = "CTS_RULE_SET", allocationSize = 1)
    @Column(name = "ID_RULE_SET")
    private Long id;

    @Column(name = "RULE_SET_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "RULE_SET_VERSION", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private RuleSetStatus status;

    @Column(name = "VALIDATED_AT")
    private LocalDateTime validatedAt;

    @Column(name = "ACTIVATED_AT")
    private LocalDateTime activatedAt;

    @Column(name = "VALIDATION_MESSAGE", length = 1000)
    private String validationMessage;

    @OneToMany(mappedBy = "ruleSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stageOrder ASC")
    private final List<RuleStageEntity> stages = new ArrayList<>();

    protected RuleSetEntity() {}

    public RuleSetEntity(String name, long version) {
        this.name = name;
        this.version = version;
        this.status = RuleSetStatus.DRAFT;
    }

    public void markValid(LocalDateTime at, String message) {
        status = RuleSetStatus.VALID;
        validatedAt = at;
        validationMessage = message;
    }

    public void activate(LocalDateTime at) {
        if (validatedAt == null) {
            throw new IllegalStateException("Rule set must be validated before activation");
        }
        status = RuleSetStatus.ACTIVE;
        activatedAt = at;
    }

    public void retire() {
        status = RuleSetStatus.RETIRED;
    }

    public void addStage(RuleStageEntity stage) {
        stages.add(stage);
        stage.attachTo(this);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public long getVersion() { return version; }
    public RuleSetStatus getStatus() { return status; }
    public LocalDateTime getValidatedAt() { return validatedAt; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public String getValidationMessage() { return validationMessage; }
    public List<RuleStageEntity> getStages() { return List.copyOf(stages); }
}
