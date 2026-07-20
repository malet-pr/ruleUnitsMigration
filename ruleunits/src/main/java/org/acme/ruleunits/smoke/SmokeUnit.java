package org.acme.ruleunits.smoke;
import java.util.*;
import org.drools.ruleunits.api.*;
/**
 * Minimal build-time Rule Unit data contract proving that Maven-generated services can be
 * discovered and instantiated. It is not part of work-order processing.
 */
public final class SmokeUnit implements RuleUnitData {
 private final DataStore<SmokeMessage> messages=DataSource.createStore();
 private final List<String> results=new ArrayList<>();
 public DataStore<SmokeMessage> getMessages(){return messages;}
 public List<String> getResults(){return results;}
}
