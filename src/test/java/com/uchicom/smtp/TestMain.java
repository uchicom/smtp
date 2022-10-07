// (C) 2022 uchicom
package com.uchicom.smtp;

import java.util.Properties;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class TestMain {

  public static void main(String[] args) {

    String from = "test@localhost";
    String fromName = "from";
    String to = "test@localhost";
    String toName = "to";
    String subject = "test";
    String body = "test";

    final String charset = "ISO-2022-JP";

    Properties props = new Properties();
    props.put("mail.smtp.host", "localhost");
    props.put("mail.smtp.port", "8025");
    props.put("mail.smtp.auth", "false");
    props.put("mail.smtp.starttls.enable", true);
    props.put("mail.smtp.ssl.trust", "localhost");
    props.put("mail.smtp.ssl.protocols", "TLSv1.2");
    props.put("mail.smtp.connectiontimeout", "10000");
    props.put("mail.smtp.timeout", "10000");
    props.put("mail.debug", "true");

    Session session = session(props);
    MimeMessage message = new MimeMessage(session);
    if (from.contains(".@")) {
      from = "\"" + from.replace(".@", ".\"@");
    }
    if (to.contains(".@")) {
      to = "\"" + to.replace(".@", ".\"@");
    }
    try {
      // From:
      message.setFrom(new InternetAddress(from, fromName));
      // ReplyTo:
      message.setReplyTo(new Address[] {new InternetAddress(from)});
      InternetAddress toAddress = new InternetAddress(to, toName);
      // 送信アドレスチェック
      toAddress.validate();
      // To:
      message.setRecipient(Message.RecipientType.TO, toAddress);

      // Subject:
      message.setSubject(subject, charset); // タイトル

      // 本文
      message.setText(body, charset);

      send(message, 3);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static Session session(Properties props) {
    return Session.getInstance(props);
  }

  static void send(MimeMessage mimeMessage, int retry) {
    int retryCount = 1;
    while (true) {
      try {
        Transport.send(mimeMessage);
        return;
      } catch (MessagingException e) {
        if (retryCount > retry) {
          throw new RuntimeException(e);
        }
        retryCount++;
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e1) {
          e1.printStackTrace();
        }
      }
    }
  }
}
