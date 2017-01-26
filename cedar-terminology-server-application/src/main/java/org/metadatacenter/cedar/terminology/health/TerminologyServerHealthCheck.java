package org.metadatacenter.cedar.terminology.health;

import com.codahale.metrics.health.HealthCheck;

public class TerminologyServerHealthCheck extends HealthCheck {

  public TerminologyServerHealthCheck() {
  }

  @Override
  protected Result check() throws Exception {
    if (2 * 2 == 5) {
      return Result.unhealthy("Unhealthy, because 2 * 2 == 5");
    }
    return Result.healthy();
  }
}