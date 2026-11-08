// (C) 2017 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uchicom.smtp.auth.ChannelBinding;
import com.uchicom.smtp.auth.ScramAlgorithm;
import com.uchicom.smtp.auth.ScramCredential;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
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
    session(args, (reader, ps, client) -> script.run(reader, ps));
  }

  /** クライアントのソケットも使えるセッション(STARTTLSなど). */
  private void session(String[] args, SocketScript script) throws Exception {
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
        script.run(reader, ps, client);
      }
      thread.join(5000);
    }
  }

  private interface SocketScript {
    void run(BufferedReader reader, PrintStream ps, Socket client) throws Exception;
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
  public void duplicateTransferRecipientDoesNotConsumeRcptCount(@TempDir File dir)
      throws Exception {
    String[] base = transferArgs(dir);
    String[] args = Arrays.copyOf(base, base.length + 2);
    args[base.length] = "-maxRcpt";
    args[base.length + 1] = "2";
    session(
        args,
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "EHLO test")).startsWith("250");
          authenticate(reader, ps);
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<x@remote.example>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<x@remote.example>")).startsWith("250");
          // 重複は宛先数に数えないため、2件目の別宛先も受理される
          assertThat(send(reader, ps, "RCPT TO:<y@remote.example>")).startsWith("250");
        });
  }

  @Test
  public void mixedRecipientsAreAccepted(@TempDir File dir) throws Exception {
    session(
        transferArgs(dir),
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "EHLO test")).startsWith("250");
          authenticate(reader, ps);
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
          assertThat(send(reader, ps, "RCPT TO:<x@remote.example>")).startsWith("250");
          // 外部宛ての後にローカル宛て(BCC想定)を指定しても受理される
          assertThat(send(reader, ps, "RCPT TO:<user@localhost>")).startsWith("250");
          assertThat(send(reader, ps, "DATA")).startsWith("354");
          ps.print("Subject: test\r\n\r\nbody\r\n");
          ps.flush();
          // 外部宛ては配送できないため250にはならないが、ローカル宛ては配送される
          assertThat(send(reader, ps, ".")).doesNotStartWith("250");
        });
    assertThat(new File(dir, "user").listFiles()).hasSize(1);
  }

  @Test
  public void strictFromRejectsForeignSender(@TempDir File dir) throws Exception {
    String[] base = transferArgs(dir);
    String[] args = java.util.Arrays.copyOf(base, base.length + 2);
    args[base.length] = "-strictFrom";
    args[base.length + 1] = "true";
    session(
        args,
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "EHLO test")).startsWith("250");
          authenticate(reader, ps);
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("553");
          assertThat(send(reader, ps, "MAIL FROM:<other@localhost>")).startsWith("553");
          assertThat(send(reader, ps, "MAIL FROM:<>")).startsWith("553");
          assertThat(send(reader, ps, "MAIL FROM:<auth@localhost>")).startsWith("250");
        });
  }

  @Test
  public void withoutStrictFromAnySenderIsAccepted(@TempDir File dir) throws Exception {
    session(
        transferArgs(dir),
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "EHLO test")).startsWith("250");
          authenticate(reader, ps);
          assertThat(send(reader, ps, "MAIL FROM:<>")).startsWith("250");
          assertThat(send(reader, ps, "MAIL FROM:<a@example.com>")).startsWith("250");
        });
  }

  @Test
  public void getLastTimeIsNotCurrentTime() throws Exception {
    SmtpProcess process = process();
    long first = process.getLastTime();
    Thread.sleep(20);

    assertThat(process.getLastTime()).isEqualTo(first);
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

  @Test
  public void ehloAdvertisesSaslMechanisms(@TempDir File dir) throws Exception {
    session(
        transferArgs(dir),
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "EHLO test"))
              .endsWith(
                  "250 AUTH SCRAM-SHA-512 SCRAM-SHA-384 SCRAM-SHA-256 SCRAM-SHA-224"
                      + " SCRAM-SHA-1 PLAIN LOGIN");
        });
  }

  @Test
  public void authPlain(@TempDir File dir) throws Exception {
    session(
        transferArgs(dir),
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "AUTH PLAIN " + b64("\0auth\0wrong"))).startsWith("535");
          // 初期応答なし
          assertThat(send(reader, ps, "AUTH PLAIN")).isEqualTo("334 ");
          assertThat(send(reader, ps, b64("\0auth\0secret"))).startsWith("235");
          assertThat(send(reader, ps, "AUTH PLAIN " + b64("\0auth\0secret"))).startsWith("503");
        });
  }

  /** keytoolでテスト用のキーストアを作る. */
  private File createKeyStore(File dir) throws Exception {
    File keyStore = new File(dir, "keystore");
    Process process =
        new ProcessBuilder(
                new File(System.getProperty("java.home"), "bin/keytool").getAbsolutePath(),
                "-genkeypair",
                "-alias",
                "smtp",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-dname",
                "CN=localhost",
                "-validity",
                "2",
                "-storetype",
                "JKS",
                "-keystore",
                keyStore.getAbsolutePath(),
                "-storepass",
                "changeit",
                "-keypass",
                "changeit")
            .redirectErrorStream(true)
            .start();
    process.getInputStream().readAllBytes();
    assertThat(process.waitFor()).isZero();
    return keyStore;
  }

  /** STARTTLSを完了した状態でクライアントのやり取りを実行する. 証明書は検証しない. */
  private void tlsSession(File dir, TlsScript script) throws Exception {
    File keyStore = createKeyStore(dir);
    String[] base = transferArgs(dir);
    String[] args = Arrays.copyOf(base, base.length + 4);
    args[base.length] = "-keyStoreName";
    args[base.length + 1] = keyStore.getAbsolutePath();
    args[base.length + 2] = "-keyStorePass";
    args[base.length + 3] = "changeit";
    session(
        args,
        (reader, ps, client) -> {
          assertThat(reader.readLine()).startsWith("220");
          // STARTTLS前はAUTHを広告しない
          assertThat(send(reader, ps, "EHLO test")).doesNotContain("AUTH");
          assertThat(send(reader, ps, "STARTTLS")).startsWith("220");
          SSLContext context = SSLContext.getInstance("TLS");
          context.init(
              null,
              new javax.net.ssl.TrustManager[] {
                new X509TrustManager() {
                  public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                  public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                  public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                  }
                }
              },
              null);
          try (SSLSocket tls =
              (SSLSocket)
                  context
                      .getSocketFactory()
                      .createSocket(
                          client,
                          client.getInetAddress().getHostAddress(),
                          client.getPort(),
                          true)) {
            tls.startHandshake();
            var tlsReader =
                new BufferedReader(
                    new InputStreamReader(tls.getInputStream(), StandardCharsets.UTF_8));
            var tlsPs = new PrintStream(tls.getOutputStream());
            script.run(tlsReader, tlsPs, tls);
          }
        });
  }

  private interface TlsScript {
    void run(BufferedReader reader, PrintStream ps, SSLSocket tls) throws Exception;
  }

  @Test
  public void tlsAdvertisesScramPlus(@TempDir File dir) throws Exception {
    tlsSession(
        dir,
        (reader, ps, tls) -> {
          assertThat(send(reader, ps, "EHLO test"))
              .endsWith(
                  "250 AUTH SCRAM-SHA-512-PLUS SCRAM-SHA-384-PLUS SCRAM-SHA-256-PLUS"
                      + " SCRAM-SHA-224-PLUS SCRAM-SHA-1-PLUS SCRAM-SHA-512 SCRAM-SHA-384"
                      + " SCRAM-SHA-256 SCRAM-SHA-224 SCRAM-SHA-1 PLAIN LOGIN");
        });
  }

  @Test
  public void tlsScramPlus(@TempDir File dir) throws Exception {
    tlsSession(
        dir,
        (reader, ps, tls) -> {
          assertThat(send(reader, ps, "EHLO test")).contains("SCRAM-SHA-256-PLUS");
          // クライアント側はピア(サーバー)証明書から求める
          byte[] binding =
              ChannelBinding.serverEndPoint(
                  (X509Certificate) tls.getSession().getPeerCertificates()[0]);
          assertThat(binding).isNotNull();
          String gs2 = "p=tls-server-end-point,,";

          // バインディングデータ不一致
          assertThat(
                  scram(
                      reader,
                      ps,
                      "SCRAM-SHA-256-PLUS",
                      ScramAlgorithm.SHA_256,
                      "secret",
                      gs2,
                      new byte[] {1, 2, 3}))
              .startsWith("535");
          // 成功
          assertThat(
                  scram(
                      reader,
                      ps,
                      "SCRAM-SHA-256-PLUS",
                      ScramAlgorithm.SHA_256,
                      "secret",
                      gs2,
                      binding))
              .startsWith("235");
        });
  }

  @Test
  public void tlsScramPlusRequiresChannelBinding(@TempDir File dir) throws Exception {
    tlsSession(
        dir,
        (reader, ps, tls) -> {
          assertThat(send(reader, ps, "EHLO test")).contains("SCRAM-SHA-256-PLUS");
          // -PLUS機構でバインディングなし(n)
          assertThat(send(reader, ps, "AUTH SCRAM-SHA-256-PLUS")).isEqualTo("334 ");
          assertThat(send(reader, ps, b64("n,,n=auth,r=abc"))).startsWith("535");
          // 非PLUS機構でyはダウングレードとして拒否
          assertThat(send(reader, ps, "AUTH SCRAM-SHA-256")).isEqualTo("334 ");
          assertThat(send(reader, ps, b64("y,,n=auth,r=abc"))).startsWith("535");
          // 非PLUS機構でnは受け付ける
          assertThat(scram(reader, ps, ScramAlgorithm.SHA_256, "secret")).startsWith("235");
        });
  }

  @Test
  public void authErrors(@TempDir File dir) throws Exception {
    session(
        transferArgs(dir),
        (reader, ps) -> {
          assertThat(reader.readLine()).startsWith("220");
          assertThat(send(reader, ps, "AUTH UNKNOWN")).startsWith("504");
          // PLUS系は未対応
          assertThat(send(reader, ps, "AUTH SCRAM-SHA-256-PLUS")).startsWith("504");
          assertThat(send(reader, ps, "AUTH PLAIN !!!")).startsWith("501");
          // 中断
          assertThat(send(reader, ps, "AUTH LOGIN")).startsWith("334");
          assertThat(send(reader, ps, "*")).startsWith("501");
          // 存在しないユーザー
          assertThat(send(reader, ps, "AUTH LOGIN")).startsWith("334");
          assertThat(send(reader, ps, b64("nobody"))).startsWith("334");
          assertThat(send(reader, ps, b64("secret"))).startsWith("535");
          // 中断後は通常のコマンドに戻る
          assertThat(send(reader, ps, "NOOP")).startsWith("250");
        });
  }

  @Test
  public void authScram(@TempDir File dir) throws Exception {
    String[] args = transferArgs(dir);
    for (ScramAlgorithm algorithm : ScramAlgorithm.values()) {
      session(
          args,
          (reader, ps) -> {
            assertThat(reader.readLine()).startsWith("220");
            assertThat(scram(reader, ps, algorithm, "secret")).startsWith("235");
          });
      session(
          args,
          (reader, ps) -> {
            assertThat(reader.readLine()).startsWith("220");
            assertThat(scram(reader, ps, algorithm, "wrong")).startsWith("535");
          });
    }
  }

  /** SCRAMのクライアント側. 最後の応答を返す. */
  private static String scram(
      BufferedReader reader, PrintStream ps, ScramAlgorithm algorithm, String password)
      throws Exception {
    return scram(reader, ps, algorithm.getMechanismName(), algorithm, password, "n,,", new byte[0]);
  }

  /**
   * SCRAMのクライアント側(チャネルバインディング指定可). 最後の応答を返す.
   *
   * @param mechanism AUTHで指定する機構名
   * @param gs2Header gs2ヘッダ
   * @param binding 最終メッセージのc=に付加するバインディングデータ
   */
  private static String scram(
      BufferedReader reader,
      PrintStream ps,
      String mechanism,
      ScramAlgorithm algorithm,
      String password,
      String gs2Header,
      byte[] binding)
      throws Exception {
    String clientFirstBare = "n=auth,r=clientnonce";
    String reply = send(reader, ps, "AUTH " + mechanism);
    assertThat(reply).isEqualTo("334 ");
    reply = send(reader, ps, b64(gs2Header + clientFirstBare));
    assertThat(reply).startsWith("334 ");
    String serverFirst = unb64(reply.substring(4));
    String[] attrs = serverFirst.split(",");
    String nonce = attrs[0].substring(2);
    byte[] salt = java.util.Base64.getDecoder().decode(attrs[1].substring(2));
    int iterations = Integer.parseInt(attrs[2].substring(2));
    ScramCredential credential = algorithm.derive(password, salt, iterations);
    byte[] saltedPassword =
        algorithm.hi(password.getBytes(StandardCharsets.UTF_8), salt, iterations);
    byte[] clientKey =
        algorithm.hmac(saltedPassword, "Client Key".getBytes(StandardCharsets.US_ASCII));
    byte[] cbind = new byte[gs2Header.length() + binding.length];
    System.arraycopy(
        gs2Header.getBytes(StandardCharsets.US_ASCII), 0, cbind, 0, gs2Header.length());
    System.arraycopy(binding, 0, cbind, gs2Header.length(), binding.length);
    String withoutProof =
        "c=" + java.util.Base64.getEncoder().encodeToString(cbind) + ",r=" + nonce;
    byte[] authMessage =
        (clientFirstBare + "," + serverFirst + "," + withoutProof).getBytes(StandardCharsets.UTF_8);
    byte[] signature = algorithm.hmac(credential.storedKey(), authMessage);
    byte[] proof = new byte[clientKey.length];
    for (int i = 0; i < proof.length; i++) {
      proof[i] = (byte) (clientKey[i] ^ signature[i]);
    }
    reply =
        send(
            reader,
            ps,
            b64(withoutProof + ",p=" + java.util.Base64.getEncoder().encodeToString(proof)));
    if (!reply.startsWith("334 ")) {
      return reply;
    }
    byte[] expected = algorithm.hmac(credential.serverKey(), authMessage);
    assertThat(unb64(reply.substring(4)))
        .isEqualTo("v=" + java.util.Base64.getEncoder().encodeToString(expected));
    return send(reader, ps, "");
  }

  private static String unb64(String s) {
    return new String(java.util.Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
  }
}
