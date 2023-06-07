// (C) 2017 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * @author uchicom: Shigeki Uchiyama
 */
public class SmtpProcessTest {

  @Test
  public void mailFromCheck() {
    Properties prop = new Properties();
    prop.setProperty("*@domain.com", "true");
    String mailFrom = "test@domain.com";
    String domain = prop.getProperty("*" + mailFrom.substring(mailFrom.indexOf('@')));
    assertThat(Boolean.parseBoolean(domain)).isTrue();
  }
}
