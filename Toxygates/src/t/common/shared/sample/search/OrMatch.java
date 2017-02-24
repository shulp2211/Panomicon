package t.common.shared.sample.search;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("serial")
public class OrMatch implements MatchCondition, Serializable {
  Collection<MatchCondition> conditions;
  
  //GWT constructor
  public OrMatch() {}
  
  public OrMatch(Collection<MatchCondition> conditions) {
    this.conditions = conditions;
  }
  
  @Override
  public Collection<String> neededParameters() {
    Set<String> r = new HashSet<String>();
    for (MatchCondition mc: conditions) {
      r.addAll(mc.neededParameters());
    }
    return r;
  }
  
  public Collection<MatchCondition> subConditions() { return conditions; }
}
