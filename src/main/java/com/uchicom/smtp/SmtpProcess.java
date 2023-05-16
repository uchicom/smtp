// (C) 2014 uchicom
package com.uchicom.smtp;

import com.uchicom.server.ServerProcess;
import com.uchicom.smtp.type.AuthenticationStatus;
import com.uchicom.util.Parameter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * SMTP処理クラス. 送信処理認証後は、他サーバに直接接続してプロキシ―のような処理をする。
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class SmtpProcess implements ServerProcess {

  private static final Logger logger = Logger.getLogger(SmtpProcess.class.getCanonicalName());

  private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss.SSS");
  private Parameter parameter;
  private Socket socket;

  private boolean bHelo;
  private boolean bMailFrom;
  private boolean bRcptTo;
  private boolean bData;
  private boolean bAuth;
  /** 外のメールサーバに転送する */
  private boolean bTransfer;

  private AuthenticationStatus authStatus;
  private boolean bSpam;

  private String senderAddress;
  private String helo;
  private String mailFrom;
  private Mail mail;
  private String authName;

  private List<MailBox> boxList = new ArrayList<>();

  /** 転送アドレス一覧 */
  private List<String> transferList = new ArrayList<>();

  private long startTime = System.currentTimeMillis();

  /**
   * コンストラクタ.
   *
   * @param parameter パラメータ情報
   * @param socket ソケット
   */
  public SmtpProcess(Parameter parameter, Socket socket) {
    this.parameter = parameter;
    this.socket = socket;
  }

  /** SMTP処理を実行 */
  public void execute() {
    this.senderAddress = socket.getInetAddress().getHostAddress();
    logger.log(Level.INFO, String.valueOf(senderAddress));
    Writer writer = null;
    BufferedReader br = null;
    PrintStream ps = null;
    try {
      br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      ps = new PrintStream(socket.getOutputStream());
      SmtpUtil.recieveLine(ps, "220 ", parameter.get("host"), " SMTP");
      String line = br.readLine();
      // HELO
      // MAIL FROM:
      // RCPT TO:
      // DATA
      // MAIL FROM:
      // RCPT TO:
      // DATAの順で処理する
      // 認証状態、非認証状態で、判定できるコマンドを分ける、状態遷移もつける
      while (line != null) {
        logger.log(Level.INFO, "[" + line + "]");
        logger.log(Level.INFO, "status:" + authStatus);
        if (authStatus == AuthenticationStatus.USER
            || authStatus == AuthenticationStatus.PASSWORD) {
          switch (authStatus) {
            case USER:
              String name = new String(Base64.getDecoder().decode(line));
              File dir = new File(parameter.getFile("dir"), name);
              if (dir.exists() && dir.isDirectory()) {
                authStatus = AuthenticationStatus.PASSWORD;
                authName = name;

                SmtpUtil.recieveLine(ps, Constants.RECV_334, " UGFzc3dvcmQ6");
              }
              break;
            case PASSWORD:
              String pass = new String(Base64.getDecoder().decode(line));
              File passwordFile =
                  new File(
                      new File(parameter.getFile("dir"), authName), Constants.PASSWORD_FILE_NAME);
              if (passwordFile.exists() && passwordFile.isFile()) {
                try (BufferedReader passReader =
                    new BufferedReader(
                        new InputStreamReader(new FileInputStream(passwordFile))); ) {
                  String password = passReader.readLine();
                  while ("".equals(password)) {
                    password = passReader.readLine();
                  }
                  if (pass.equals(password)) {

                    SmtpUtil.recieveLine(ps, Constants.RECV_235);
                    bAuth = true;
                    authStatus = AuthenticationStatus.AUTH;
                  } else {
                    // パスワード不一致エラー
                    SmtpUtil.recieveLine(ps, Constants.RECV_535);
                    authStatus = AuthenticationStatus.NOAUTH;
                  }
                }
              }
              break;
            default:
          }
        } else if (bData) {
          if (".".equals(line)) {
            // メッセージ終了
            writer.close();
            if (bSpam) {
              // 迷惑メールフォルダに移動
              try {
                boxList.stream()
                    .forEach(
                        (mailBox) -> {
                          mailBox.setDir(new File(parameter.getFile("dir"), Constants.SPAM_DIR));
                        });
                mail.copy(
                    boxList,
                    socket.getLocalAddress().getHostName(),
                    socket.getInetAddress().getHostName());
              } catch (Exception e) {
                e.printStackTrace();
              }
            } else if (bTransfer) {
              logger.log(Level.INFO, "transferList:" + transferList);
              for (String address : transferList) {
                // 転送処理を実行する。
                logger.log(Level.INFO, "transfer:" + address);
                String[] addresses = address.split("@");
                String[] hosts = lookupMailHosts(addresses[1]);
                boolean send = false;
                for (String mxHost : hosts) {

                  MailSender.sendMail(
                      parameter.get("host"),
                      mxHost,
                      address,
                      mail.getData(),
                      parameter.getFile("dkim"),
                      parameter.get("dkimSelector"));
                  send = true;
                  break;
                }
                // MXで送信できなかった場合は、ホスト名に送信する
                if (!send) {
                  MailSender.sendMail(
                      parameter.get("host"),
                      addresses[1],
                      address,
                      mail.getData(),
                      parameter.getFile("dkim"),
                      parameter.get("dkimSelector"));
                }
              }
            } else {
              // メッセージコピー処理
              try {
                mail.copy(
                    boxList,
                    socket.getLocalAddress().getHostName(),
                    socket.getInetAddress().getHostName());
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
            mail.delete();
            mail = null;
            transferList.clear();

            if (bSpam) {
              SmtpUtil.recieveLine(ps, "550");
            } else {
              SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
            }
            init();
          } else if (!line.isEmpty() && line.charAt(0) == '.') {
            writer.write(line.substring(1));
            writer.write("\r\n");
          } else {
            // メッセージ本文
            // 禁止文字が含まれる場合は、迷惑メールに追加
            Matcher matcher = Constants.pattern.matcher(line);
            if (matcher.find()) {
              bSpam = true;
            }
            writer.write(line);
            writer.write("\r\n");
          }
        } else if ((SmtpUtil.isEhlo(line) || SmtpUtil.isHelo(line))) {
          bHelo = true;
          String[] lines = line.split(" +");
          helo = lines[1];
          if (parameter.is("transfer")) {
            SmtpUtil.recieveLine(
                ps, Constants.RECV_250, "-", parameter.get("host"), " Hello ", senderAddress);
            if (!(socket instanceof SSLSocket) && hasKeyStore()) {
              SmtpUtil.recieveLine(ps, Constants.RECV_250, "-STARTTLS");
            }
            SmtpUtil.recieveLine(ps, Constants.RECV_250, " AUTH LOGIN"); // TODO CRAM-MD5も追加したい
          } else {

            if ((socket instanceof SSLSocket) || !hasKeyStore()) {
              SmtpUtil.recieveLine(
                  ps, Constants.RECV_250, " ", parameter.get("host"), " Hello ", senderAddress);
            } else {
              SmtpUtil.recieveLine(
                  ps, Constants.RECV_250, "-", parameter.get("host"), " Hello ", senderAddress);
              SmtpUtil.recieveLine(ps, Constants.RECV_250, " STARTTLS");
            }
          }
          init();
        } else if (SmtpUtil.isStartTls(line)) {
          SmtpUtil.recieveLine(ps, Constants.RECV_220, " Go ahead");
          socket = startTls();
          br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
          ps = new PrintStream(socket.getOutputStream());
        } else if (SmtpUtil.isAuthLogin(line) && parameter.is("transfer")) {
          authStatus = AuthenticationStatus.USER;
          SmtpUtil.recieveLine(ps, Constants.RECV_334, " VXNlcm5hbWU6");
        } else if (SmtpUtil.isRset(line)) {
          SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
          init();
        } else if (SmtpUtil.isMailFrom(line)) {
          if (bHelo) {
            mailFrom = line.substring(10).trim().replaceAll("[<>]", "");
            logger.log(Level.INFO, mailFrom);
            SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
            bMailFrom = true;
          } else {
            // エラー500
            SmtpUtil.recieveLine(ps, "451 ERROR");
          }
        } else if (SmtpUtil.isRcptTo(line)) {
          if (bMailFrom) {
            String[] heads = line.split(":");
            String address = heads[1].trim().replaceAll("[<>]", "");
            String[] addresses = address.split("@");
            logger.log(Level.INFO, addresses[0]);
            logger.log(Level.INFO, addresses[1]);
            if (addresses[1].equals(parameter.get("host"))) {
              // 宛先チェック
              if (parameter.is("memory")) {
                for (String user : Context.singleton().getUsers()) {
                  if (addresses[0].equals(user)) {
                    boxList.add(new MailBox(address, Context.singleton().getMailList(user)));
                    break;
                  }
                }

              } else {
                for (File box : parameter.getFile("dir").listFiles()) {
                  if (box.isDirectory()) {
                    if (addresses[0].equals(box.getName())) {
                      if (mailFromCheck(box)) {
                        boxList.add(new MailBox(address, box));
                      }
                      break;
                    }
                  }
                }
              }
              if (boxList.size() > 0) {
                SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
                bRcptTo = true;
              } else {
                // エラーユーザー存在しない
                SmtpUtil.recieveLine(ps, "550 Failure reply");
              }
            } else if (bAuth && parameter.is("transfer")) { // 念のためチェック
              // 認証済みなので転送OKする

              SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
              bTransfer = true;
              transferList.add(address);
              bRcptTo = true;
            } else {
              // エラーホストが違う
              SmtpUtil.recieveLine(ps, "554 <" + address + ">: Relay access denied");
            }
          } else {
            // エラー500
            SmtpUtil.recieveLine(ps, "500");
          }
        } else if (SmtpUtil.isData(line)) {
          if (bRcptTo) {
            if (bTransfer) {
              mail = new MemoryMail();
            } else if (parameter.is("memory")) {
              mail = new MemoryMail();
            } else {
              mail =
                  new FileMail(
                      new File(
                          new File(parameter.getFile("dir"), "@rcpt"),
                          helo.replaceAll(":", "_")
                              + "_"
                              + mailFrom
                              + "~"
                              + senderAddress.replaceAll(":", "_")
                              + "_"
                              + LocalDateTime.now().format(dateTimeFormatter) // 日付とuuid スレッド番号
                              + "_"
                              + Thread.currentThread().getId()
                              + ".eml"));
            }
            writer = mail.getWriter();
            SmtpUtil.recieveLine(ps, Constants.RECV_354);
            bData = true;
          } else {
            // エラー
            SmtpUtil.recieveLine(ps, "500");
          }
        } else if (SmtpUtil.isHelp(line)) {
          SmtpUtil.recieveLine(ps, "250");
        } else if (SmtpUtil.isQuit(line)) {
          SmtpUtil.recieveLine(ps, "221 ", parameter.get("host"));
          break;
        } else if (SmtpUtil.isNoop(line)) {
          SmtpUtil.recieveLine(ps, "250");
        } else if (line.length() == 0) {
          // 何もしない
        } else {
          // 対応コマンドなし
          SmtpUtil.recieveLine(ps, "500 Syntax error, command unrecognized");
        }
        startTime = System.currentTimeMillis();
        line = br.readLine();
      }

    } catch (IOException e) {
      e.printStackTrace();
    } catch (Throwable e) {
      e.printStackTrace();
    } finally {
      if (br != null) {
        try {
          br.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (ps != null) {
        ps.close();
      }
      if (mail != null) {
        // メッセージコピー処理
        try {
          mail.copy(
              boxList,
              socket.getInetAddress().getHostName(),
              InetAddress.getLocalHost().getHostName());
          mail.delete();
        } catch (Exception e) {
          e.printStackTrace();
        }
        mail = null;
      }
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException e) {
          // TODO 自動生成された catch ブロック
          e.printStackTrace();
        }
      }
      if (socket != null) {
        try {
          socket.close();
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          socket = null;
        }
      }
    }
  }

  /** 初期化. */
  private void init() {
    mail = null;
    bMailFrom = false;
    bRcptTo = false;
    bData = false;
    boxList.clear();
  }

  public long getStartTime() {
    return startTime;
  }

  public void forceClose() {
    System.out.println("forceClose!");
    if (socket != null && socket.isConnected()) {
      try {
        socket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      socket = null;
    }
  }

  public List<Mail> getMailList(String user) {
    if (parameter.is("memory")) {
      return Context.singleton().getMailList(user);
    } else {
      List<Mail> mailList = new ArrayList<>();
      File box = new File(parameter.getFile("dir"), user);
      if (box.exists()) {
        for (File file : box.listFiles()) {
          try {
            mailList.add(new FileMail(file));
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
      return mailList;
    }
  }

  @Override
  public long getLastTime() {
    return System.currentTimeMillis();
  }

  /**
   * DNS問合せでMXレコードを抽出します.
   *
   * @param domainName ドメイン名
   * @return MXホスト一覧
   * @throws NamingException エラーが初声死した場合
   */
  public static String[] lookupMailHosts(String domainName) throws NamingException {

    InitialDirContext idc = new InitialDirContext();
    Attributes attributes = idc.getAttributes("dns:/" + domainName, new String[] {"MX"});
    Attribute attributeMX = attributes.get("MX");

    if (attributeMX == null) {
      return (new String[] {domainName});
    }

    String[][] pvhn = new String[attributeMX.size()][2];
    for (int i = 0; i < attributeMX.size(); i++) {
      pvhn[i] = ("" + attributeMX.get(i)).split("\\s+");
    }

    Arrays.sort(
        pvhn,
        new Comparator<String[]>() {
          public int compare(String[] o1, String[] o2) {
            return (Integer.parseInt(o1[0]) - Integer.parseInt(o2[0]));
          }
        });

    String[] sortedHostNames = new String[pvhn.length];
    for (int i = 0; i < pvhn.length; i++) {
      if (pvhn[i][1].endsWith(".")) {
        sortedHostNames[i] = pvhn[i][1].substring(0, pvhn[i][1].length() - 1);
      } else {
        sortedHostNames[i] = pvhn[i][1];
      }
    }
    return sortedHostNames;
  }

  /**
   * MAIL FROM で制御する
   *
   * @param box
   * @param logStream
   * @return
   */
  boolean mailFromCheck(File box) {
    boolean add = true;
    File mailFromFile = new File(box, Constants.IGNORE_FILE_NAME);
    if (mailFromFile.exists() && mailFromFile.isFile()) {
      Properties prop = new Properties();
      try (FileInputStream fis = new FileInputStream(mailFromFile)) {
        prop.load(fis);
        String all = prop.getProperty("*");
        if (all != null) {
          add = Boolean.parseBoolean(all);
        }
        String topLevelDomain =
            prop.getProperty("*" + mailFrom.substring(mailFrom.lastIndexOf(".")));
        if (topLevelDomain != null) {
          add = Boolean.parseBoolean(topLevelDomain);
        }
        String domain = prop.getProperty("*" + mailFrom.substring(mailFrom.indexOf('@')));
        if (domain != null) {
          add = Boolean.parseBoolean(domain);
        }
        String ignore = prop.getProperty(mailFrom);
        if (ignore != null) {
          add = Boolean.parseBoolean(ignore);
        }
      } catch (Exception e) {
        logger.log(Level.WARNING, e.getMessage(), e);
      }
      prop.clear();
      if (!add) {
        File mailFromResultFile = new File(box, Constants.IGNORE_RESULT_FILE_NAME);
        int cnt = 1;
        try {
          mailFromResultFile.createNewFile();
          try (FileInputStream fis = new FileInputStream(mailFromResultFile)) {
            prop.load(fis);
          }
          String ignore = prop.getProperty(mailFrom);
          if (ignore != null) {
            cnt = Integer.parseInt(ignore);
            cnt++;
          }
          prop.setProperty(mailFrom, String.valueOf(cnt));
          try (FileOutputStream fos = new FileOutputStream(mailFromResultFile)) {
            prop.store(fos, "");
          }
        } catch (IOException e) {
          logger.log(Level.WARNING, e.getMessage(), e);
        } catch (Exception e) {
          logger.log(Level.WARNING, e.getMessage(), e);
        }
      }
    }
    return add;
  }

  SSLSocket startTls()
      throws KeyStoreException,
          NoSuchAlgorithmException,
          CertificateException,
          FileNotFoundException,
          IOException,
          UnrecoverableKeyException,
          KeyManagementException {
    String password = parameter.get("keyStorePass");
    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(new FileInputStream(parameter.get("keyStoreName")), password.toCharArray());
    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(ks, password.toCharArray());
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), null, null);

    SSLSocket sslSocket =
        (SSLSocket)
            ((SSLSocketFactory) sslContext.getSocketFactory())
                .createSocket(
                    socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);

    sslSocket.setUseClientMode(false);
    sslSocket.startHandshake();

    return sslSocket;
  }

  boolean hasKeyStore() {
    return parameter.get("keyStoreName") != null && parameter.get("keyStorePass") != null;
  }
}
