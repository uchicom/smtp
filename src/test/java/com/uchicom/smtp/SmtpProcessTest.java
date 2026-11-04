// (C) 2017 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

public class SmtpProcessTest {

  @Test
  public void mailFromCheck() {
    Properties prop = new Properties();
    prop.setProperty("*@domain.com", "true");
    String mailFrom = "test@domain.com";
    String domain = prop.getProperty("*" + mailFrom.substring(mailFrom.indexOf('@')));
    assertThat(Boolean.parseBoolean(domain)).isTrue();
  }

  /** テスト用SMTPプロセスを起動し、クライアント側のやり取りを実行する. */
  private void session(String[] args, ClientScript script) throws Exception {
    SmtpParameter parameter = new SmtpParameter(args);
    parameter.init();
    try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
      Thread thread =
          new Thread(
              () -> {
                try {
                  new SmtpProcess(parameter, Logger.getAnonymousLogger(), server.accept())
                      .execute();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
      thread.start();
      try (Socket client = new Socket(server.getInetAddress(), server.getLocalPort())) {
        client.setSoTimeout(5000);
        var reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        var ps = new PrintStream(client.getOutputStream());
        script.run(reader, ps);
      }
      thread.join(5000);
    }
  }

  private interface ClientScript {
    void run(BufferedReader reader, PrintStream ps) throws Exception;
  }

  private static String send(BufferedReader reader, PrintStream ps, String line) throws Exception {
    ps.print(line + "\r\n");
    ps.flush();
    // 複数行応答(250-)は最終行まで読み、改行区切りで連結して返す
    StringBuilder sb = new StringBuilder();
    String received = reader.readLine();
    while (received != null) {
      sb.append(received);
      if (received.length() < 4 || received.charAt(3) != '-') {
        break;
      }
      sb.append('\n');
      received = reader.readLine();
    }
    return received == null && sb.length() == 0 ? null : sb.toString();
  }

  @Test
  public void invalidAddress() throws Exception {
    session(
        new String[] {"-memory", "user", "-host", "localhost"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO test")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:invalid")).startsWith("501");
          assertThat(send(reader, ps, "MAIL FROM:")).startsWith("501");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:invalid")).startsWith("501");
          assertThat(send(reader, ps, "RCPT TO:<@>")).startsWith("501");
          assertThat(send(reader, ps, "QUIT")).startsWith("221");
        });
  }

  @Test
  public void lineTooLong() throws Exception {
    session(
        new String[] {"-memory", "user", "-host", "localhost", "-maxCommandLine", "20"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO " + "a".repeat(30))).startsWith("500");
          assertThat(reader.readLine()).isNull();
        });
  }

  @Test
  public void messageTooLarge() throws Exception {
    session(
        new String[] {"-memory", "user", "-host", "localhost", "-maxSize", "20"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO test")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "DATA")).startsWith("354");
          ps.print("0123456789\r\n0123456789\r\n0123456789\r\n");
          ps.flush();
          assertThat(send(reader, ps, ".")).startsWith("552");
          assertThat(send(reader, ps, "NOOP")).startsWith("250");
        });
  }

  @Test
  public void ehloAdvertisesSize() throws Exception {
    session(
        new String[] {"-memory", "user", "-host", "localhost", "-maxSize", "12345"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "EHLO test")).contains("250 SIZE 12345");
        });
  }

  @Test
  public void nullReversePath() throws Exception {
    session(
        new String[] {"-memory", "user", "-host", "localhost"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO test")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<>")).startsWith("501");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
        });
  }

  @Test
  public void declaredSizeTooLarge() throws Exception {
    session(
        new String[] {"-memory", "user", "-host", "localhost", "-maxSize", "1000"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO test")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com> SIZE=1001")).startsWith("552");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com> SIZE=1000")).startsWith("250");
        });
  }

  @Test
  public void tooManyRecipients() throws Exception {
    session(
        new String[] {"-memory", "user", "-host", "localhost", "-maxRcpt", "1"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO test")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("452");
        });
  }

  @Test
  public void longBodyLineIsFolded() throws Exception {
    session(
        new String[] {"-memory", "user", "-host", "localhost", "-maxLine", "5"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO test")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "DATA")).startsWith("354");
          // 折り返し後の先頭が"."でも、終端として扱わない
          ps.print("abcde.\r\nabcdefghijklmnop\r\n");
          ps.flush();
          assertThat(send(reader, ps, ".")).startsWith("250");
          assertThat(send(reader, ps, "NOOP")).startsWith("250");
        });
  }

  @Test
  public void tooManyConnections() throws Exception {
    assertThat(ConnectionLimiter.singleton().acquire("127.0.0.1", 100, 1)).isTrue();
    try {
      session(
          new String[] {"-memory", "user", "-host", "localhost", "-maxConnPerIp", "1"},
          (reader, ps) -> {
            assertThat(reader.readLine()).startsWith("421");
          });
    } finally {
      ConnectionLimiter.singleton().release("127.0.0.1");
    }
  }
}
