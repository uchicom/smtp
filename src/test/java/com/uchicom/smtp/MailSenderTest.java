// (C) 2026 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class MailSenderTest {

  @Test
  public void envelopeFromIsMailFrom() {
    assertThat(
            MailSender.createTransferProperties("mx.example.com", "a@example.com")
                .getProperty("mail.smtp.from"))
        .isEqualTo("a@example.com");
  }

  @Test
  public void nullSenderIsPreserved() {
    assertThat(
            MailSender.createTransferProperties("mx.example.com", "").getProperty("mail.smtp.from"))
        .isEqualTo("<>");
    assertThat(
            MailSender.createTransferProperties("mx.example.com", null)
                .getProperty("mail.smtp.from"))
        .isEqualTo("<>");
  }

  @Test
  public void serverIdentityIsCheckedByDefault() {
    assertThat(
            MailSender.createTransferProperties("mx.example.com", "a@example.com")
                .getProperty("mail.smtp.ssl.checkserveridentity"))
        .isEqualTo("true");
  }

  @Test
  public void serverIdentityCheckCanBeDisabled() {
    assertThat(
            MailSender.createTransferProperties("mx.example.com", "a@example.com", false)
                .getProperty("mail.smtp.ssl.checkserveridentity"))
        .isEqualTo("false");
  }
}
