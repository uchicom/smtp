// (C) 2014 uchicom
package com.uchicom.smtp;

import com.sun.mail.smtp.SMTPAddressFailedException;
import com.sun.mail.smtp.SMTPSendFailedException;
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
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import javax.mail.MessagingException;
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

  private final Logger logger;

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

  /** メッセージサイズが上限を超えた */
  private boolean bOverSize;

  /** 受信済みのメッセージサイズ(バイト) */
  private long dataSize;

  /** 本文行長上限(CRLFを除く文字数) */
  private int maxLine;

  /** コマンド行長上限(CRLFを除く文字数) */
  private int maxCommandLine;

  /** 1通あたりの宛先数上限 */
  private int maxRcpt;

  /** メッセージサイズ上限(バイト) */
  private long maxSize;

  /** 受け付けた宛先数 */
  private int rcptCount;

  private LineReader lineReader;

  /** 直前に読んだ行が、折り返された行の続きか */
  private boolean bContinuation;

  private String senderAddress;
  private String helo;
  private String mailFrom;
  private Mail mail;
  private String authName;

  private Set<MailBox> boxSet = new HashSet<>();

  /** 転送アドレス一覧 */
  private List<String> transferList = new ArrayList<>();

  private long startTime = System.currentTimeMillis();

  /**
   * コンストラクタ.
   *
   * @param parameter パラメータ情報
   * @param logger ロガー
   * @param socket ソケット
   */
  public SmtpProcess(Parameter parameter, Logger logger, Socket socket) {
    this.parameter = parameter;
    this.logger = logger;
    this.socket = socket;
  }

  /** SMTP処理を実行 */
  public void execute() {
    this.senderAddress = socket.getInetAddress().getHostAddress();
    logger.log(Level.INFO, "From IP:" + String.valueOf(senderAddress));
    Writer writer = null;
    BufferedReader br = null;
    PrintStream ps = null;
    boolean acquired = false;
    maxLine = parameter.getInt("maxLine", Integer.parseInt(Constants.DEFAULT_MAX_LINE));
    maxCommandLine =
        parameter.getInt("maxCommandLine", Integer.parseInt(Constants.DEFAULT_MAX_COMMAND_LINE));
    maxRcpt = parameter.getInt("maxRcpt", Integer.parseInt(Constants.DEFAULT_MAX_RCPT));
    maxSize = parameter.getInteger("maxSize", Integer.parseInt(Constants.DEFAULT_MAX_SIZE));
    try {
      socket.setSoTimeout(
          parameter.getInt("soTimeout", Integer.parseInt(Constants.DEFAULT_SO_TIMEOUT)));
      br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      lineReader = new LineReader(br);
      ps = new PrintStream(socket.getOutputStream());
      acquired =
          ConnectionLimiter.singleton()
              .acquire(
                  senderAddress,
                  parameter.getInt("maxConn", Integer.parseInt(Constants.DEFAULT_MAX_CONN)),
                  parameter.getInt(
                      "maxConnPerIp", Integer.parseInt(Constants.DEFAULT_MAX_CONN_PER_IP)));
      if (!acquired) {
        logger.log(Level.WARNING, "Too many connections from IP:" + senderAddress);
        SmtpUtil.recieveLine(ps, Constants.RECV_421_BUSY);
        return;
      }
      SmtpUtil.recieveLine(ps, "220 ", parameter.get("host"), " SMTP");
      String line = nextLine();
      // HELO
      // MAIL FROM:
      // RCPT TO:
      // DATA
      // MAIL FROM:
      // RCPT TO:
      // DATAの順で処理する
      // 認証状態、非認証状態で、判定できるコマンドを分ける、状態遷移もつける
      while (line != null) {
        if (authStatus == AuthenticationStatus.USER
            || authStatus == AuthenticationStatus.PASSWORD) {
          switch (authStatus) {
            case USER:
              String name;
              try {
                name = new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8);
              } catch (IllegalArgumentException e) {
                SmtpUtil.recieveLine(ps, "501 Invalid base64 data");
                authStatus = AuthenticationStatus.NOAUTH;
                break;
              }
              File dir = SmtpUtil.resolveUserDir(parameter.getFile("dir"), name);
              if (dir == null) {
                SmtpUtil.recieveLine(ps, Constants.RECV_535);
                authStatus = AuthenticationStatus.NOAUTH;
              } else {
                authStatus = AuthenticationStatus.PASSWORD;
                authName = name;

                SmtpUtil.recieveLine(ps, Constants.RECV_334, " UGFzc3dvcmQ6");
              }
              break;
            case PASSWORD:
              String pass;
              try {
                pass = new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8);
              } catch (IllegalArgumentException e) {
                SmtpUtil.recieveLine(ps, "501 Invalid base64 data");
                authStatus = AuthenticationStatus.NOAUTH;
                break;
              }
              if (SmtpUtil.verifyPassword(parameter.getFile("dir"), authName, pass)) {
                SmtpUtil.recieveLine(ps, Constants.RECV_235);
                bAuth = true;
                authStatus = AuthenticationStatus.AUTH;
              } else {
                // ユーザー・パスワードファイルなし、パスワード不一致エラー
                SmtpUtil.recieveLine(ps, Constants.RECV_535);
                authStatus = AuthenticationStatus.NOAUTH;
              }
              break;
            default:
          }
        } else if (bData) {
          if (!bContinuation && ".".equals(line) && bOverSize) {
            // サイズ超過のメッセージは破棄する
            writer.close();
            mail.delete();
            mail = null;
            transferList.clear();
            SmtpUtil.recieveLine(ps, Constants.RECV_552);
            init();
          } else if (!bContinuation && ".".equals(line)) {
            // メッセージ終了
            writer.close();
            Exception failure = null;
            if (bSpam) {
              // 迷惑メールフォルダに移動
              try {
                boxSet.stream()
                    .forEach(
                        (mailBox) -> {
                          mailBox.setDir(new File(parameter.getFile("dir"), Constants.SPAM_DIR));
                        });
                mail.copy(
                    boxSet,
                    socket.getLocalAddress().getHostName(),
                    socket.getInetAddress().getHostName());
              } catch (Exception e) {
                logger.log(Level.WARNING, "spam copy failed", e);
              }
            } else if (bTransfer) {
              logger.log(Level.INFO, "transferList:" + transferList);
              try {
                transfer();
              } catch (Exception e) {
                logger.log(Level.WARNING, "transfer failed", e);
                failure = e;
              }
            } else {
              // メッセージコピー処理
              try {
                mail.copy(
                    boxSet,
                    socket.getLocalAddress().getHostName(),
                    socket.getInetAddress().getHostName());
              } catch (Exception e) {
                logger.log(Level.WARNING, "copy failed", e);
                failure = e;
              }
            }
            mail.delete();
            mail = null;
            transferList.clear();

            if (bSpam) {
              SmtpUtil.recieveLine(ps, "550");
            } else if (failure != null) {
              // 配送できていないため250は返さず、送信元に再送/失敗を判断させる
              SmtpUtil.recieveLine(
                  ps, isPermanentFailure(failure) ? Constants.RECV_554 : Constants.RECV_451);
            } else {
              SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
            }
            init();
          } else if (bOverSize) {
            // サイズ超過後は終端まで破棄する
          } else {
            dataSize += line.length() + 2L;
            if (dataSize > maxSize) {
              bOverSize = true;
            } else if (!bContinuation && !line.isEmpty() && line.charAt(0) == '.') {
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
          }
        } else if ((SmtpUtil.isEhlo(line) || SmtpUtil.isHelo(line))) {
          bHelo = true;
          String[] lines = line.split(" +");
          helo = lines[1];
          if (parameter.is("transfer")) {
            SmtpUtil.recieveLine(
                ps, Constants.RECV_250, "-", parameter.get("host"), " Hello ", senderAddress);
            SmtpUtil.recieveLine(ps, Constants.RECV_250, "-SIZE ", String.valueOf(maxSize));
            if (!(socket instanceof SSLSocket) && hasKeyStore()) {
              // STARTTLS完了までAUTHは広告しない
              SmtpUtil.recieveLine(ps, Constants.RECV_250, " STARTTLS");
            } else {
              SmtpUtil.recieveLine(ps, Constants.RECV_250, " AUTH LOGIN"); // TODO CRAM-MD5も追加したい
            }
          } else {

            if ((socket instanceof SSLSocket) || !hasKeyStore()) {
              SmtpUtil.recieveLine(
                  ps, Constants.RECV_250, "-", parameter.get("host"), " Hello ", senderAddress);
              SmtpUtil.recieveLine(ps, Constants.RECV_250, " SIZE ", String.valueOf(maxSize));
            } else {
              SmtpUtil.recieveLine(
                  ps, Constants.RECV_250, "-", parameter.get("host"), " Hello ", senderAddress);
              SmtpUtil.recieveLine(ps, Constants.RECV_250, "-SIZE ", String.valueOf(maxSize));
              SmtpUtil.recieveLine(ps, Constants.RECV_250, " STARTTLS");
            }
          }
          init();
        } else if (SmtpUtil.isStartTls(line)) {
          if (hasKeyStore()) {
            SmtpUtil.recieveLine(ps, Constants.RECV_220, " Go ahead");
            socket = startTls();
            socket.setSoTimeout(
                parameter.getInt("soTimeout", Integer.parseInt(Constants.DEFAULT_SO_TIMEOUT)));
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            lineReader = new LineReader(br);
            ps = new PrintStream(socket.getOutputStream());
          } else {
            SmtpUtil.recieveLine(ps, "502", " Command not implemented");
          }
        } else if (SmtpUtil.isAuthLogin(line) && parameter.is("transfer")) {
          if (!(socket instanceof SSLSocket) && hasKeyStore()) {
            SmtpUtil.recieveLine(ps, "530 Must issue a STARTTLS command first");
          } else {
            authStatus = AuthenticationStatus.USER;
            SmtpUtil.recieveLine(ps, Constants.RECV_334, " VXNlcm5hbWU6");
          }
        } else if (SmtpUtil.isRset(line)) {
          SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
          init();
        } else if (SmtpUtil.isMailFrom(line)) {
          if (bHelo) {
            String[] parsed = SmtpUtil.parseAddress(line, true);
            if (parsed == null) {
              SmtpUtil.recieveLine(ps, Constants.RECV_501_ADDRESS);
            } else if (isDeclaredSizeOver(line)) {
              SmtpUtil.recieveLine(ps, Constants.RECV_552);
            } else {
              mailFrom = parsed[0].isEmpty() ? "" : parsed[0] + "@" + parsed[1];
              logger.log(Level.INFO, "From email address:" + mailFrom);
              SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
              bMailFrom = true;
            }
          } else {
            // エラー500
            SmtpUtil.recieveLine(ps, "451 ERROR");
          }
        } else if (SmtpUtil.isRcptTo(line)) {
          String[] addresses = bMailFrom ? SmtpUtil.parseAddress(line, false) : null;
          if (bMailFrom && addresses == null) {
            SmtpUtil.recieveLine(ps, Constants.RECV_501_ADDRESS);
          } else if (bMailFrom && rcptCount >= maxRcpt) {
            SmtpUtil.recieveLine(ps, Constants.RECV_452_TOO_MANY_RCPT);
          } else if (bMailFrom) {
            String address = addresses[0] + "@" + addresses[1];
            logger.log(Level.INFO, "To email address:" + address);
            if (addresses[1].equals(parameter.get("host"))) {
              // 宛先チェック
              if (parameter.is("memory")) {
                for (String user : Context.singleton().getUsers()) {
                  if (addresses[0].equals(user)) {
                    boxSet.add(new MailBox(address, Context.singleton().getMailList(user)));
                    break;
                  }
                }

              } else {
                for (File box : parameter.getFile("dir").listFiles()) {
                  if (box.isDirectory()) {
                    if (addresses[0].equals(box.getName())) {
                      if (mailFromCheck(box)) {
                        boxSet.add(
                            MailBox.builder()
                                .mailAddress(address)
                                .dir(box)
                                .webhook(parameter.is("webhook"))
                                .build());
                      }
                      break;
                    }
                  }
                }
              }
              if (boxSet.size() > 0) {
                SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
                rcptCount++;
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
              rcptCount++;
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
                              + Thread.currentThread().threadId()
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
        line = nextLine();
      }

    } catch (LineTooLongException e) {
      logger.log(Level.WARNING, "Line too long from IP:" + senderAddress);
      if (ps != null) {
        SmtpUtil.recieveLine(ps, Constants.RECV_500_LINE_TOO_LONG);
      }
    } catch (IOException e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    } catch (Throwable e) {
      logger.log(Level.SEVERE, e.getMessage(), e);
    } finally {
      if (acquired) {
        ConnectionLimiter.singleton().release(senderAddress);
      }
      if (br != null) {
        try {
          br.close();
        } catch (IOException e) {
          logger.log(Level.WARNING, e.getMessage(), e);
        }
      }
      if (ps != null) {
        ps.close();
      }
      if (mail != null) {
        // メッセージコピー処理
        try {
          mail.copy(
              boxSet,
              socket.getLocalAddress().getHostName(),
              socket.getInetAddress().getHostName());
          mail.delete();
        } catch (Exception e) {
          logger.log(Level.WARNING, e.getMessage(), e);
        }
        mail = null;
      }
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException e) {
          logger.log(Level.WARNING, e.getMessage(), e);
        }
      }
      if (socket != null) {
        try {
          socket.close();
        } catch (Exception e) {
          logger.log(Level.WARNING, e.getMessage(), e);
        } finally {
          socket = null;
        }
      }
    }
  }

  /**
   * 次の行を読み込む. DATA中は上限で折り返し、それ以外は上限超過を例外とする.
   *
   * @return 行。終端の場合はnull
   * @throws IOException 通信エラー、または行長超過の場合
   */
  private String nextLine() throws IOException {
    bContinuation = lineReader.isFolded();
    return lineReader.readLine(bData ? maxLine : maxCommandLine, bData);
  }

  /**
   * MAIL FROMのSIZEパラメータで宣言されたサイズが上限を超えているか.
   *
   * @param line MAIL FROMコマンド行
   * @return 上限を超えている場合はtrue
   */
  private boolean isDeclaredSizeOver(String line) {
    Matcher matcher = Constants.REG_EXP_SIZE_PARAM.matcher(line);
    if (!matcher.find()) {
      return false;
    }
    try {
      return Long.parseLong(matcher.group(1)) > maxSize;
    } catch (NumberFormatException e) {
      return true;
    }
  }

  /** 初期化. */
  private void init() {
    mail = null;
    bMailFrom = false;
    bRcptTo = false;
    bData = false;
    bOverSize = false;
    dataSize = 0;
    rcptCount = 0;
    boxSet.clear();
  }

  public long getStartTime() {
    return startTime;
  }

  public void forceClose() {
    logger.info("forceClose!");
    if (socket != null && socket.isConnected()) {
      try {
        socket.close();
      } catch (IOException e) {
        logger.log(Level.WARNING, e.getMessage(), e);
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
            logger.log(Level.WARNING, e.getMessage(), e);
          }
        }
      }
      return mailList;
    }
  }

  /**
   * 転送先ごとにMXを優先度順に試行して転送する.
   *
   * @throws Exception いずれかの転送先に配送できなかった場合
   */
  private void transfer() throws Exception {
    Exception failure = null;
    for (String address : transferList) {
      logger.log(Level.INFO, "transfer:" + address);
      try {
        String domain = address.substring(address.indexOf('@') + 1);
        sendToHosts(
            lookupMailHosts(domain),
            mxHost ->
                MailSender.sendMail(
                    parameter.get("host"),
                    mxHost,
                    address,
                    mail.getData(),
                    parameter.getFile("dkim"),
                    parameter.get("dkimSelector")));
      } catch (Exception e) {
        logger.log(Level.WARNING, "transfer failed:" + address, e);
        if (failure == null) {
          failure = e;
        } else {
          failure.addSuppressed(e);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  /** ホストへの送信処理. */
  @FunctionalInterface
  interface HostSender {
    void send(String host) throws Exception;
  }

  /**
   * 先頭から順にホストへ送信し、成功した時点で終了する. 一時的な失敗は次のホストを試行し、恒久的な失敗(5xx)は即座に送出する.
   *
   * @param hosts 優先度順のホスト一覧
   * @param sender 送信処理
   * @throws Exception 全ホストで失敗した場合は最後の例外、ホストがない場合はIllegalArgumentException
   */
  void sendToHosts(String[] hosts, HostSender sender) throws Exception {
    if (hosts.length == 0) {
      throw new IllegalArgumentException("no host");
    }
    Exception last = null;
    for (String host : hosts) {
      try {
        sender.send(host);
        return;
      } catch (Exception e) {
        logger.log(Level.WARNING, "send failed:" + host, e);
        if (isPermanentFailure(e)) {
          throw e;
        }
        if (last != null) {
          e.addSuppressed(last);
        }
        last = e;
      }
    }
    throw last;
  }

  /**
   * 失敗が恒久的(SMTP応答コード5xx)か判定する.
   *
   * @param e 例外
   * @return 恒久的な失敗の場合はtrue
   */
  static boolean isPermanentFailure(Throwable e) {
    Set<Throwable> visited = new HashSet<>();
    Deque<Throwable> queue = new ArrayDeque<>();
    queue.add(e);
    while (!queue.isEmpty()) {
      Throwable t = queue.poll();
      if (t == null || !visited.add(t)) {
        continue;
      }
      int code = 0;
      if (t instanceof SMTPSendFailedException send) {
        code = send.getReturnCode();
      } else if (t instanceof SMTPAddressFailedException address) {
        code = address.getReturnCode();
      }
      if (code >= 500 && code < 600) {
        return true;
      }
      if (t.getCause() != null) {
        queue.add(t.getCause());
      }
      if (t instanceof MessagingException messaging && messaging.getNextException() != null) {
        queue.add(messaging.getNextException());
      }
    }
    return false;
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
    @SuppressWarnings("BanJNDI")
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
        if (!mailFrom.isEmpty()) {
          String topLevelDomain =
              prop.getProperty("*" + mailFrom.substring(mailFrom.lastIndexOf(".")));
          if (topLevelDomain != null) {
            add = Boolean.parseBoolean(topLevelDomain);
          }
          String domain = prop.getProperty("*" + mailFrom.substring(mailFrom.indexOf('@')));
          if (domain != null) {
            add = Boolean.parseBoolean(domain);
          }
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
