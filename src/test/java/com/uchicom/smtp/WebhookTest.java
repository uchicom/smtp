// (C) 2022 uchicom
package com.uchicom.smtp;

import java.io.File;

public class WebhookTest {
  public static void main(String[] args) {
    try {
      MailBox mailBox = new MailBox("test@localhost", new File("mailbox/test"));
      if (mailBox.hasWebhook()) {
        mailBox.webhook(new File("test.eml"));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
