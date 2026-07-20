package org.acme.ruleunits.smoke;
import static org.assertj.core.api.Assertions.assertThat;
import org.drools.ruleunits.api.*;
import org.junit.jupiter.api.Test;
class SmokeUnitTest {
 @Test void discoversGeneratedUnitAndFiresTraditionalPattern(){
  SmokeUnit data=new SmokeUnit(); data.getMessages().add(new SmokeMessage("ping"));
  try(RuleUnitInstance<SmokeUnit> instance=RuleUnitProvider.get().createRuleUnitInstance(data)){
   assertThat(instance.fire()).isEqualTo(1);
  }
  assertThat(data.getResults()).containsExactly("pong");
 }
}
