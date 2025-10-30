// (C) 2017 uchicom
package com.uchicom.smtp;

import com.uchicom.smtp.dkim.DkimBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * @author uchicom: Shigeki Uchiyama
 */
public class MailSender {

  private static final Logger logger = Logger.getLogger(MailSender.class.getCanonicalName());

  private String fromHost;
  private String host;
  private int port;
  private String user;
  private String enc;
  private String password;
  private boolean ssl;

  public MailSender(
      String fromHost,
      String host,
      int port,
      String enc,
      String user,
      String password,
      boolean ssl) {
    this.fromHost = fromHost;
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.enc = enc;
    this.ssl = ssl;
  }

  public Socket createSocket() throws Exception {
    if (ssl) {
      //			KeyStore ks = KeyStore.getInstance("JKS");
      //
      //			char[] keyStorePass = "changeit".toCharArray();
      //			ks.load(new FileInputStream("conf/client_keystore"), keyStorePass);
      //
      //			KeyManagerFactory tmf = KeyManagerFactory.getInstance("SunX509");
      //			tmf.init(ks, keyStorePass);
      //
      //			// ソケットを生成する
      //			SSLContext context = SSLContext.getInstance("TLS");
      //			context.init(tmf.getKeyManagers(), null, null);
      //			SSLSocketFactory sf = context.getSocketFactory();
      SSLSocketFactory sf = SSLContext.getDefault().getSocketFactory();
      SSLSocket socket = (SSLSocket) sf.createSocket(host, port);
      socket.startHandshake();
      return socket;
    } else {
      return new Socket(host, port);
    }
  }

  public void send(String from, String to, String data) throws Exception {

    logger.info("transfer:" + to);
    try (Socket socket = createSocket();
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        OutputStreamWriter writer2 = new OutputStreamWriter(socket.getOutputStream(), enc); ) {
      logger.info("t[" + reader.readLine() + "]");
      writer2.write("EHLO " + this.fromHost + "\r\n"); // EHLO
      writer2.flush();
      String rec = null;
      do {
        rec = reader.readLine();
        logger.info("t[" + rec + "]");
      } while (rec != null && rec.startsWith("250-"));
      writer2.write("AUTH LOGIN" + "\r\n"); // AUTH LOGIN
      writer2.flush();
      rec = reader.readLine();
      logger.info("t[" + rec + "]");
      writer2.write(new String(Base64.getEncoder().encode(user.getBytes())) + "\r\n"); // USER
      writer2.flush();
      rec = reader.readLine();
      logger.info("t[" + rec + "]");
      writer2.write(
          new String(Base64.getEncoder().encode(password.getBytes())) + "\r\n"); // PASSWORD
      writer2.flush();
      rec = reader.readLine();
      logger.info("t[" + rec + "]");
      logger.info("mailFrom:" + from);
      writer2.write("MAIL FROM: <" + from + ">\r\n"); // MAIL FROM:
      writer2.flush();
      rec = reader.readLine();
      logger.info("t[" + rec + "]");
      if (!rec.startsWith("250"))
        throw new Exception(from + "が迷惑メールに登録されている可能性があります。メールの設定を確認してください");

      writer2.write("RCPT TO: <" + to + ">\r\n"); // RCPT TO:
      writer2.flush();
      rec = reader.readLine();
      logger.info("t[" + rec + "]");
      if (!rec.startsWith("250")) throw new Exception(to + "のアドレスが存在しない可能性があります。メールの設定を確認してください");
      writer2.write("DATA\r\n"); // DATA
      writer2.flush();
      rec = reader.readLine();
      logger.info("t[" + rec + "]");
      if (!rec.startsWith("354")) throw new Exception("メールの送信に失敗しました。メールの管理者までご連絡ください");
      writer2.write(data);
      writer2.flush();
      writer2.write(".\r\n"); // DATA
      writer2.flush();
      rec = reader.readLine();
      logger.info("t[" + rec + "]");
      if (!rec.startsWith("250")) throw new Exception("メールの送信に失敗しました。申し訳ございませんが、メールの管理者までご連絡ください");
      writer2.write("QUIT\r\n");
      writer2.flush();
      rec = reader.readLine();
      logger.info("t[" + rec + "]");
    } catch (Exception e) {
      e.printStackTrace();
      logger.warning(e.getMessage());
      throw e;
    }
    logger.info("smtp host:" + host);
  }

  public void send(String from, String to, String data, String enc) throws Exception {

    logger.info("transfer:" + to);
    String[] addresses = to.split("@");
    String[] hosts = SmtpProcess.lookupMailHosts(addresses[1]);
    boolean sent = false;
    Exception exception = null;

    for (String host : hosts) {
      if (sent) break;
      exception = null;
      try (Socket socket = new Socket(host, 8025);
          BufferedReader reader =
              new BufferedReader(new InputStreamReader(socket.getInputStream()));
          OutputStreamWriter writer2 = new OutputStreamWriter(socket.getOutputStream(), enc); ) {
        logger.info("t[" + reader.readLine() + "]");
        writer2.write("EHLO " + this.fromHost + "\r\n"); // EHLO
        writer2.flush();
        String rec = null;
        do {
          rec = reader.readLine();
          logger.info("t[" + rec + "]");
        } while (rec != null && rec.startsWith("250-"));
        logger.info("mailFrom:" + from);
        writer2.write("MAIL FROM: <" + from + ">\r\n"); // MAIL FROM:
        writer2.flush();
        rec = reader.readLine();
        logger.info("t[" + rec + "]");
        if (!rec.startsWith("250"))
          throw new Exception(from + "が迷惑メールに登録されている可能性があります。メールの設定を確認してください");

        writer2.write("RCPT TO: <" + to + ">\r\n"); // RCPT TO:
        writer2.flush();
        rec = reader.readLine();
        logger.info("t[" + rec + "]");
        if (!rec.startsWith("250")) throw new Exception(to + "のアドレスが存在しない可能性があります。メールの設定を確認してください");
        writer2.write("DATA\r\n"); // DATA
        writer2.flush();
        rec = reader.readLine();
        logger.info("t[" + rec + "]");
        if (!rec.startsWith("354")) throw new Exception("メールの送信に失敗しました。メールの管理者までご連絡ください");
        writer2.write(data);
        writer2.flush();
        writer2.write(".\r\n"); // DATA
        writer2.flush();
        rec = reader.readLine();
        logger.info("t[" + rec + "]");
        if (!rec.startsWith("250"))
          throw new Exception("メールの送信に失敗しました。申し訳ございませんが、メールの管理者mまでご連絡ください");
        sent = true;
        writer2.write("QUIT\r\n");
        writer2.flush();
        rec = reader.readLine();
        logger.info("t[" + rec + "]");
        break;
      } catch (Exception e) {
        e.printStackTrace();
        logger.warning(e.getMessage());
        exception = e;
      }
      logger.info("mx!:" + host);
    }
    // 送信していない場合はexceptionを送信
    if (!sent) {
      throw exception;
    }
  }

  public static void send(
      String fromHost, String fromMailAddress, String toHost, String toMailAddress, String data)
      throws UnknownHostException, IOException {

    try (Socket transferSocket = new Socket(toHost, 25);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(transferSocket.getInputStream()));
        BufferedWriter writer =
            new BufferedWriter(new OutputStreamWriter(transferSocket.getOutputStream())); ) {

      logger.log(Level.INFO, "t[" + reader.readLine() + "]");

      writer.write("EHLO ");
      writer.write(fromHost);
      writer.write("\r\n"); // EHLO

      writer.flush();

      String rec = null;
      //			boolean starttls = false;
      do {
        rec = reader.readLine();
        logger.log(Level.INFO, "t[" + rec + "]");
        if (rec.contains("STARTTLS")) {
          //					starttls = true;
        }
      } while (rec != null && rec.startsWith("250-"));

      //			if (starttls) {
      //				writer2.write("STARTTLS\r\n");// STARTTLS
      //				writer2.flush();
      //
      //				logger.log(Level.INFO,"t[" + reader.readLine() + "]");
      //				writer2.close();
      //				reader.close();
      //				//SSL処理開始
      //				SSLSocket sslSocket = (SSLSocket) ((SSLSocketFactory)
      // SSLSocketFactory.getDefault()).createSocket(
      //	                       socket,
      //	                       socket.getInetAddress().getHostAddress(),
      //	                       socket.getPort(),
      //	                       true);
      ////				sslSocket.setEnabledProtocols(sslSocket.getSupportedProtocols());
      ////
      ////				sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());
      //				sslSocket.setEnableSessionCreation(true);
      //				sslSocket.setUseClientMode(true);
      //				logger.log(Level.INFO,"startHandshake");
      //
      //				sslSocket.startHandshake();
      //
      //				logger.log(Level.INFO,"reader2");
      //				BufferedReader reader2 = new BufferedReader(new
      // InputStreamReader(sslSocket.getInputStream()));
      //
      //				logger.log(Level.INFO,"writer3");
      //				BufferedWriter writer3 = new BufferedWriter(new
      // OutputStreamWriter(sslSocket.getOutputStream()));
      //
      //
      //
      //				logger.log(Level.INFO,"ehlo");
      //				writer3.write("EHLO uchicom.com\r\n");//EHLO
      //				logger.log(Level.INFO,"flush");
      //				writer3.flush();
      //
      //				rec = null;
      //				do {
      //					logger.log(Level.INFO,"readline");
      //					rec = reader2.readLine();
      //					logger.log(Level.INFO,"t[" + rec + "]");
      //				}while (rec != null && rec.startsWith("250-"));
      //				logger.log(Level.INFO,"mailFrom3:" + mailFrom);
      //				writer3.write("MAIL FROM: <" + mailFrom + ">\r\n");// MAIL FROM:
      //				writer3.flush();
      //
      //				logger.log(Level.INFO,"t[" + reader2.readLine() + "]");
      //
      //				writer3.write("RCPT TO: <" + address + ">\r\n");// RCPT TO:
      //				writer3.flush();
      //
      //				logger.log(Level.INFO,"t[" + reader2.readLine() + "]");
      //
      //				writer3.write("DATA\r\n");// DATA
      //				writer3.flush();
      //
      //				logger.log(Level.INFO,"t[" + reader2.readLine() + "]");
      //
      //				writer3.write(mail.getTitle());
      //				writer3.flush();
      //
      //				writer3.write(".\r\n");
      //				writer3.flush();
      //				logger.log(Level.INFO,"t[" + reader2.readLine() + "]");
      //
      //				writer3.write("QUIT\r\n");
      //				writer3.flush();
      //				logger.log(Level.INFO,"t[" + reader2.readLine() + "]");
      //
      //				reader2.close();
      //				writer3.close();
      //			} else {
      logger.log(Level.INFO, "mailFrom:" + fromMailAddress);
      writer.write("MAIL FROM: <" + fromMailAddress + ">\r\n"); // MAIL FROM:
      writer.flush();

      logger.log(Level.INFO, "t[" + reader.readLine() + "]");

      writer.write("RCPT TO: <" + toMailAddress + ">\r\n"); // RCPT TO:
      writer.flush();

      logger.log(Level.INFO, "t[" + reader.readLine() + "]");

      writer.write("DATA\r\n"); // DATA
      writer.flush();

      logger.log(Level.INFO, "t[" + reader.readLine() + "]");

      writer.write(data);
      writer.flush();

      writer.write(".\r\n");
      writer.flush();
      logger.log(Level.INFO, "t[" + reader.readLine() + "]");

      writer.write("QUIT\r\n");
      writer.flush();
      logger.log(Level.INFO, "t[" + reader.readLine() + "]");

      //			}
    }
    logger.log(Level.INFO, "mx!:" + toHost);
  }

  static final String charset = "UTF-8";

  static final String encoding = "base64";

  /**
   * 既存のメールデータを送信する.
   *
   * @param fromHost 送信元ホスト名
   * @param toHost 送信先ホスト名
   * @param toMailAddress 送信先Eメールアドレス
   * @param data 送信データ
   * @throws Exception
   */
  public static void sendMail(
      String fromHost,
      String toHost,
      String toMailAddress,
      String data,
      File dkimPrivateKeyFile,
      String selector)
      throws Exception {

    try {
      logger.log(Level.INFO, "fromHost[" + fromHost + "]");
      logger.log(Level.INFO, "toHost[" + toHost + "]");
      logger.log(Level.INFO, "toMailAddress[" + toMailAddress + "]");
      Properties props = new Properties();
      props.put("mail.smtp.host", toHost);
      props.put("mail.smtp.port", 25);
      props.put("mail.smtp.auth", "false");
      props.put("mail.smtp.starttls.enable", true);
      props.put("mail.smtp.ssl.trust", toHost);
      props.put("mail.smtp.ssl.protocols", "TLSv1.2");
      props.put("mail.smtp.connectiontimeout", "20000");
      props.put("mail.smtp.timeout", "200000");

      Session session = Session.getInstance(props);

      MimeMessage message =
          new MimeMessage(session, new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));

      if (dkimPrivateKeyFile != null) {
        addDKIMHeader(message, fromHost, dkimPrivateKeyFile, selector);
      }
      Transport.send(message, new Address[] {new InternetAddress(toMailAddress)});

    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  static void addDKIMHeader(
      MimeMessage message, String fromHost, File privateKeyFile, String selector) throws Exception {
    message.addHeader(
        "DKIM-Signature",
        new DkimBuilder(logger)
            .message(message)
            .fromHost(fromHost)
            .privateKeyFile(privateKeyFile)
            .selector(selector)
            .build());
  }

  /**
   * STARTTLSを許可してメールを送信する.
   *
   * @param fromHost 送信元ホスト名
   * @param fromMailAddress 送信元Eメールアドレス
   * @param toHost 送信先ホスト名
   * @param toMailAddress 送信先Eメールアドレス
   * @param subject 件名
   * @param content 本文
   * @throws UnknownHostException ホストが見つからない場合
   * @throws IOException 通信エラーの場合
   */
  public static void sendMail(
      String fromHost,
      String fromMailAddress,
      String toHost,
      String toMailAddress,
      String subject,
      String content)
      throws UnknownHostException, IOException {

    try {
      logger.log(Level.INFO, "fromHost[" + fromHost + "]");
      logger.log(Level.INFO, "fromMailAddress[" + fromMailAddress + "]");
      logger.log(Level.INFO, "toHost[" + toHost + "]");
      logger.log(Level.INFO, "toMailAddress[" + toMailAddress + "]");
      logger.log(Level.INFO, "subject[" + subject + "]");
      logger.log(Level.INFO, "content[" + content + "]");
      Properties props = new Properties();
      props.put("mail.smtp.host", toHost);
      props.put("mail.smtp.port", 25);
      props.put("mail.smtp.auth", "false");
      props.put("mail.smtp.starttls.enable", true);
      props.put("mail.smtp.ssl.trust", toHost);
      props.put("mail.smtp.connectiontimeout", "20000");
      props.put("mail.smtp.timeout", "200000");

      Session session = Session.getInstance(props);

      MimeMessage message = new MimeMessage(session);

      message.setFrom(new InternetAddress(fromMailAddress));
      message.setReplyTo(new Address[] {new InternetAddress(fromMailAddress)});
      message.setRecipient(Message.RecipientType.TO, new InternetAddress(toMailAddress));

      message.setSubject(subject, charset);
      message.setText(content, charset);

      message.setHeader("Content-Transfer-Encoding", encoding);

      Transport.send(message);

    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }
}
