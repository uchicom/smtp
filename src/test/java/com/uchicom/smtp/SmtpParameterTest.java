// (C) 2023 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uchicom.server.VirtualSocketServer;
import org.junit.jupiter.api.Test;

public class SmtpParameterTest {

  @Test
  public void init0() {
    SmtpParameter parameter = new SmtpParameter(new String[0]);

    // test
    parameter.init();

    assertThat(parameter.is("transfer")).isFalse();
    assertThat(parameter.get("dkimSelector")).isEqualTo("dkim");
    assertThat(parameter.getInt("maxLine")).isEqualTo(998);
    assertThat(parameter.getInt("maxSize")).isEqualTo(35882577);
    assertThat(parameter.getInt("soTimeout")).isEqualTo(300000);
    assertThat(parameter.getInt("timeout")).isEqualTo(300000);
    assertThat(parameter.getInt("maxConn")).isEqualTo(100);
    assertThat(parameter.getInt("maxConnPerIp")).isEqualTo(10);
  }

  @Test
  public void createServerVirtual() {
    SmtpParameter parameter = new SmtpParameter(new String[] {"-type", "virtual"});
    parameter.init();

    // test
    var server = parameter.createServer();

    assertThat(server).isInstanceOf(VirtualSocketServer.class);
  }

  @Test
  public void createServerUnknown() {
    SmtpParameter parameter = new SmtpParameter(new String[] {"-type", "unknown"});
    parameter.init();

    // test
    assertThatThrownBy(parameter::createServer).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void initTimeoutFollowsSoTimeout() {
    SmtpParameter parameter = new SmtpParameter(new String[] {"-soTimeout", "1234"});

    parameter.init();

    assertThat(parameter.getInt("timeout")).isEqualTo(1234);
  }

  @Test
  public void initTimeoutSpecified() {
    SmtpParameter parameter = new SmtpParameter(new String[] {"-timeout", "99"});

    parameter.init();

    assertThat(parameter.getInt("timeout")).isEqualTo(99);
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
