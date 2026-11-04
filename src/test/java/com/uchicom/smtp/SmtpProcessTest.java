// (C) 2017 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.mail.smtp.SMTPSendFailedException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        var reader =
            new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
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

  /** サイズは文字数ではなくバイト数で集計されること. */
  @Test
  public void messageTooLargeByBytes() throws Exception {
    session(
        new String[] {"-memory", "user", "-host", "localhost", "-maxSize", "20"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO test")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "DATA")).startsWith("354");
          // 5文字(15バイト)×2行=34バイト。文字数集計では14となり超過しない
          ps.write("あああああ\r\nあああああ\r\n".getBytes(StandardCharsets.UTF_8));
          ps.flush();
          assertThat(send(reader, ps, ".")).startsWith("552");
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
  public void unknownRecipientAfterValidIsRejected() throws Exception {
    session(
        new String[] {"-memory", "user", "-host", "localhost"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO test")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<nobody@localhost>")).startsWith("550");
        });
  }

  @Test
  public void duplicateRecipientDoesNotConsumeRcptCount() throws Exception {
    session(
        new String[] {"-memory", "user", "-host", "localhost", "-maxRcpt", "2"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO test")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<nobody@localhost>")).startsWith("550");
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
  public void copyFailureReturns451(@TempDir File dir) throws Exception {
    File box = new File(dir, "user");
    assertThat(box.mkdirs()).isTrue();
    session(
        new String[] {"-dir", dir.getAbsolutePath(), "-host", "localhost"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO test")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          // 宛先確認後にメールボックスを使用不可にして、コピーを失敗させる
          assertThat(box.delete()).isTrue();
          assertThat(box.createNewFile()).isTrue();
          assertThat(send(reader, ps, "DATA")).startsWith("354");
          ps.print("Subject: test\r\n\r\nbody\r\n");
          ps.flush();
          assertThat(send(reader, ps, ".")).startsWith("451");
        });
  }

  @Test
  public void traversalHeloDoesNotEscapeRcptDir(@TempDir File dir) throws Exception {
    File box = new File(dir, "user");
    assertThat(box.mkdirs()).isTrue();
    session(
        new String[] {"-dir", dir.getAbsolutePath(), "-host", "localhost"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "EHLO ../../x")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "DATA")).startsWith("354");
          ps.print("Subject: test\r\n\r\nbody\r\n");
          ps.flush();
          assertThat(send(reader, ps, ".")).startsWith("250");
        });
    // @rcpt の外(dir直下・親ディレクトリ)に .eml が作られていない
    assertThat(dir.listFiles((d, name) -> name.endsWith(".eml"))).isEmpty();
    assertThat(dir.getParentFile().listFiles((d, name) -> name.contains("example.com"))).isEmpty();
    // 配送先には届いている
    assertThat(box.listFiles()).hasSize(1);
  }

  @Test
  public void disconnectDuringDataDoesNotDeliver(@TempDir File dir) throws Exception {
    File box = new File(dir, "user");
    assertThat(box.mkdirs()).isTrue();
    session(
        new String[] {"-dir", dir.getAbsolutePath(), "-host", "localhost"},
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "HELO test")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "DATA")).startsWith("354");
          // 終端の.を送らずに切断する
          ps.print("Subject: test\r\n\r\nbody\r\n");
          ps.flush();
        });
    // 書きかけのメールは配送されず、@rcptにも残らない
    assertThat(box.listFiles()).isEmpty();
    File rcpt = new File(dir, "@rcpt");
    assertThat(rcpt.list()).isNullOrEmpty();
  }

  /** 認証済み状態にする. */
  private void authenticate(BufferedReader reader, PrintStream ps) throws Exception {
    assertThat(send(reader, ps, "AUTH LOGIN")).startsWith("334");
    assertThat(send(reader, ps, b64("auth"))).startsWith("334");
    assertThat(send(reader, ps, b64("secret"))).startsWith("235");
  }

  private static String b64(String s) {
    return java.util.Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  private String[] transferArgs(File dir) throws Exception {
    File auth = new File(dir, "auth");
    assertThat(auth.mkdirs()).isTrue();
    java.nio.file.Files.writeString(
        new File(auth, Constants.PASSWORD_FILE_NAME).toPath(), "secret");
    assertThat(new File(dir, "user").mkdirs()).isTrue();
    return new String[] {"-dir", dir.getAbsolutePath(), "-host", "localhost", "-transfer", "true"};
  }

  @Test
  public void rsetClearsTransferState(@TempDir File dir) throws Exception {
    session(
        transferArgs(dir),
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "EHLO test")).startsWith("250");
          authenticate(reader, ps);
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<x@remote.example>")).startsWith("250");
          assertThat(send(reader, ps, "RSET")).startsWith("250");
          // RSET後はローカル宛てが転送扱いにならず、ローカル配送される
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "DATA")).startsWith("354");
          ps.print("Subject: test\r\n\r\nbody\r\n");
          ps.flush();
          assertThat(send(reader, ps, ".")).startsWith("250");
        });
    assertThat(new File(dir, "user").listFiles()).hasSize(1);
  }

  @Test
  public void mixedRecipientsAreRejected(@TempDir File dir) throws Exception {
    session(
        transferArgs(dir),
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "EHLO test")).startsWith("250");
          authenticate(reader, ps);
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<x@remote.example>")).startsWith("452");
          assertThat(send(reader, ps, "RSET")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<x@remote.example>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("452");
        });
  }

  private SmtpProcess process() {
    return new SmtpProcess(new SmtpParameter(new String[0]), Logger.getAnonymousLogger(), null);
  }

  @Test
  public void sendToHostsFallsBackToNextHost() throws Exception {
    List<String> tried = new ArrayList<>();
    process()
        .sendToHosts(
            new String[] {"mx1", "mx2", "mx3"},
            host -> {
              tried.add(host);
              if (host.equals("mx1")) {
                throw new IOException("connection refused");
              }
            });
    assertThat(tried).containsExactly("mx1", "mx2");
  }

  @Test
  public void sendToHostsThrowsLastWhenAllFail() {
    List<String> tried = new ArrayList<>();
    assertThatThrownBy(
            () ->
                process()
                    .sendToHosts(
                        new String[] {"mx1", "mx2"},
                        host -> {
                          tried.add(host);
                          throw new IOException(host);
                        }))
        .isInstanceOf(IOException.class)
        .hasMessage("mx2");
    assertThat(tried).containsExactly("mx1", "mx2");
  }

  @Test
  public void sendToHostsStopsOnPermanentFailure() {
    List<String> tried = new ArrayList<>();
    assertThatThrownBy(
            () ->
                process()
                    .sendToHosts(
                        new String[] {"mx1", "mx2"},
                        host -> {
                          tried.add(host);
                          throw new RuntimeException(
                              new SMTPSendFailedException(
                                  "RCPT TO", 550, "no such user", null, null, null, null));
                        }))
        .isInstanceOf(RuntimeException.class);
    assertThat(tried).containsExactly("mx1");
  }

  @Test
  public void isPermanentFailure() {
    assertThat(SmtpProcess.isPermanentFailure(new IOException("x"))).isFalse();
    assertThat(
            SmtpProcess.isPermanentFailure(
                new SMTPSendFailedException("RCPT TO", 450, "busy", null, null, null, null)))
        .isFalse();
    assertThat(
            SmtpProcess.isPermanentFailure(
                new RuntimeException(
                    new SMTPSendFailedException(
                        "RCPT TO", 554, "rejected", null, null, null, null))))
        .isTrue();
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
