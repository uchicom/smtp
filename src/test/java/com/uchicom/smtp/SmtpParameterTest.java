// (C) 2023 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class SmtpParameterTest {

  @Test
  public void init0() {
    SmtpParameter parameter = new SmtpParameter(new String[0]);

    // test
    parameter.init();

    assertThat(parameter.is("transfer")).isFalse();
    assertThat(parameter.get("dkimSelector")).isEqualTo("dkim");
  }

  @Test
  public void init() {
    SmtpParameter parameter =
        new SmtpParameter(new String[] {"transfer", "-dkimSelector", "selector"});

    // test
    parameter.init();

    assertThat(parameter.is("transfer")).isTrue();
    assertThat(parameter.get("dkimSelector")).isEqualTo("selector");
  }
}
