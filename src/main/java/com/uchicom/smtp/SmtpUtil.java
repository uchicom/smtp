// (C) 2014 uchicom
package com.uchicom.smtp;

import com.uchicom.smtp.auth.ScramAlgorithm;
import com.uchicom.smtp.auth.ScramCredential;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;

/**
 * SMTPの処理で使用するユーティリティークラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class SmtpUtil {

  /** SCRAMの反復回数 */
  private static final int SCRAM_ITERATIONS = 4096;

  /** SCRAMのソルト生成用のプロセス固有の秘密値 */
  private static final byte[] SERVER_SECRET = new byte[32];

  static {
    new SecureRandom().nextBytes(SERVER_SECRET);
  }

  private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME;

  /**
   * コマンドがEHLOかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがEHLOの場合はtrue,それ以外はfalse
   */
  public static boolean isEhlo(String cmd) {
    return Constants.REG_EXP_EHLO.matcher(cmd).matches();
  }

  /**
   * コマンドがSTARTTLSかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがSTARTTLSの場合はtrue,それ以外はfalse
   */
  public static boolean isStartTls(String cmd) {
    return Constants.REG_EXP_START_TLS.matcher(cmd).matches();
  }

  /**
   * コマンドがHELOかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがHELOの場合はtrue,それ以外はfalse
   */
  public static boolean isHelo(String cmd) {
    return Constants.REG_EXP_HELO.matcher(cmd).matches();
  }

  /**
   * コマンドがRSETかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがRSETの場合はtrue,それ以外はfalse
   */
  public static boolean isRset(String cmd) {
    return Constants.REG_EXP_RSET.matcher(cmd).matches();
  }

  /**
   * コマンドがMAIL_FROMかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがMAIL_FROMの場合はtrue,それ以外はfalse
   */
  public static boolean isMailFrom(String cmd) {
    return Constants.REG_EXP_MAIL_FROM.matcher(cmd).matches();
  }

  /**
   * コマンドがRCPT_TOかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがRCPT_TOの場合はtrue,それ以外はfalse
   */
  public static boolean isRcptTo(String cmd) {
    return Constants.REG_EXP_RCPT_TO.matcher(cmd).matches();
  }

  /**
   * コマンドがDATAかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがDATAの場合はtrue,それ以外はfalse
   */
  public static boolean isData(String cmd) {
    return Constants.REG_EXP_DATA.matcher(cmd).matches();
  }

  /**
   * コマンドがQUITかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがQUITの場合はtrue,それ以外はfalse
   */
  public static boolean isQuit(String cmd) {
    return Constants.REG_EXP_QUIT.matcher(cmd).matches();
  }

  /**
   * コマンドがNOOPかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがNOOPの場合はtrue,それ以外はfalse
   */
  public static boolean isNoop(String cmd) {
    return Constants.REG_EXP_NOOP.matcher(cmd).matches();
  }

  /**
   * コマンドがVRFYかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがVRFYの場合はtrue,それ以外はfalse
   */
  public static boolean isVrfy(String cmd) {
    return Constants.REG_EXP_VRFY.matcher(cmd).matches();
  }

  /**
   * コマンドがEXPNかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがEXPNの場合はtrue,それ以外はfalse
   */
  public static boolean isExpn(String cmd) {
    return Constants.REG_EXP_EXPN.matcher(cmd).matches();
  }

  /**
   * コマンドがHELPかどうかをチェックする.
   *
   * @param cmd コマンド
   * @return コマンドがHELPの場合はtrue,それ以外はfalse
   */
  public static boolean isHelp(String cmd) {
    return Constants.REG_EXP_HELP.matcher(cmd).matches();
  }

  /**
   * AUTHコマンドを解析する.
   *
   * @param cmd コマンド
   * @return AUTHの場合は、機構名と初期応答(なければnull)の配列。AUTHでない場合はnull
   */
  public static String[] parseAuth(String cmd) {
    Matcher matcher = Constants.REG_EXP_AUTH.matcher(cmd);
    if (!matcher.matches()) {
      return null;
    }
    return new String[] {matcher.group(1), matcher.group(2)};
  }

  /**
   * MAIL FROM / RCPT TO コマンドからアドレスを抽出して構文検証する.
   *
   * @param line コマンド行
   * @param allowNullPath ヌル逆方向パス(&lt;&gt;)を許可する場合はtrue(MAIL FROMのみ)
   * @return {ローカル部, ドメイン部}。ヌル逆方向パスの場合は{"", ""}。構文が不正な場合はnull
   */
  public static String[] parseAddress(String line, boolean allowNullPath) {
    int index = line.indexOf(':');
    if (index < 0) {
      return null;
    }
    String path = line.substring(index + 1).trim();
    if (allowNullPath && Constants.REG_EXP_NULL_PATH.matcher(path).matches()) {
      return new String[] {"", ""};
    }
    Matcher matcher = Constants.REG_EXP_ADDRESS.matcher(path);
    if (!matcher.matches()) {
      return null;
    }
    String local = matcher.group(1);
    String domain = matcher.group(2);
    if (local.length() > Constants.MAX_LOCAL_PART_LENGTH
        || local.length() + 1 + domain.length() > Constants.MAX_ADDRESS_LENGTH
        || domain.startsWith(".")
        || domain.endsWith(".")
        || domain.contains("..")) {
      return null;
    }
    return new String[] {local, domain};
  }

  /**
   * 認証ユーザーのメールボックスディレクトリを解決する.
   *
   * @param baseDir メールボックスの基準フォルダ
   * @param name ユーザー名
   * @return 妥当なユーザー名で基準フォルダ配下に存在するディレクトリ。それ以外はnull
   */
  public static File resolveUserDir(File baseDir, String name) {
    if (name == null || !Constants.REG_EXP_USER_NAME.matcher(name).matches()) {
      return null;
    }
    try {
      File dir = new File(baseDir, name).getCanonicalFile();
      if (!baseDir.getCanonicalFile().equals(dir.getParentFile()) || !dir.isDirectory()) {
        return null;
      }
      return dir;
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * パスワードを検証する. ユーザーまたはパスワードファイルが存在しない場合はfalse.
   *
   * @param baseDir ユーザーディレクトリの親
   * @param name ユーザー名
   * @param pass 入力されたパスワード
   * @return 一致する場合はtrue
   */
  public static boolean verifyPassword(File baseDir, String name, String pass) {
    if (pass == null) {
      return false;
    }
    String password = readPassword(baseDir, name);
    if (password == null) {
      return false;
    }
    return MessageDigest.isEqual(
        pass.getBytes(StandardCharsets.UTF_8), password.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * パスワードファイルからパスワードを読み込む.
   *
   * @param baseDir ユーザーディレクトリの親
   * @param name ユーザー名
   * @return パスワード。ユーザーまたはパスワードファイルが存在しない場合はnull
   */
  public static String readPassword(File baseDir, String name) {
    File dir = resolveUserDir(baseDir, name);
    if (dir == null) {
      return null;
    }
    File passwordFile = new File(dir, Constants.PASSWORD_FILE_NAME);
    if (!passwordFile.isFile()) {
      return null;
    }
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(passwordFile), StandardCharsets.UTF_8))) {
      String password = reader.readLine();
      while ("".equals(password)) {
        password = reader.readLine();
      }
      return password;
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * SCRAMの資格情報をパスワードから導出する. 存在しないユーザーにも、実在するユーザーと区別できない導出値を返す.
   *
   * @param baseDir ユーザーディレクトリの親
   * @param name ユーザー名
   * @param algorithm SCRAMのアルゴリズム
   * @return 資格情報
   */
  public static ScramCredential findScramCredential(
      File baseDir, String name, ScramAlgorithm algorithm) {
    byte[] salt =
        Arrays.copyOf(algorithm.hmac(SERVER_SECRET, name.getBytes(StandardCharsets.UTF_8)), 16);
    String password = readPassword(baseDir, name);
    if (password == null) {
      // 存在しないユーザー、パスワードファイルなしの場合は、誰にも一致しない擬似パスワードを使う
      password = Base64.getEncoder().encodeToString(algorithm.hmac(SERVER_SECRET, salt));
    }
    return algorithm.derive(password, salt, SCRAM_ITERATIONS);
  }

  /**
   * ステータス行を出力する.
   *
   * @param ps 出力ストリーム
   * @param strings 文字列配列
   */
  public static void recieveLine(PrintStream ps, String... strings) {
    for (String string : strings) {
      ps.print(string);
    }
    ps.print(Constants.RECV_LINE_END);
    ps.flush();
  }

  /**
   * ファイルコピー処理
   *
   * @param from 元ファイル
   * @param to コピー先ファイル
   * @param mailAddress Eメールアドレス
   * @param senderHostName 送信元ホスト名
   * @param localHostName ローカルホスト名
   * @throws IOException 通信エラーの場合
   */
  public static void copyFile(
      File from, File to, String mailAddress, String senderHostName, String localHostName)
      throws IOException {
    try (@SuppressWarnings("resource")
            FileChannel ic = new FileInputStream(from).getChannel();
        @SuppressWarnings("resource")
            FileChannel oc = new FileOutputStream(to).getChannel(); ) {

      StringBuffer strBuff = new StringBuffer(1024);
      strBuff.append("Received: from ");
      strBuff.append(senderHostName);
      strBuff.append("\r\n");
      strBuff.append("	by ");
      strBuff.append(localHostName);
      strBuff.append("\r\n");
      strBuff.append("	for <");
      strBuff.append(mailAddress);
      strBuff.append(">; ");
      strBuff.append(dateTimeFormatter.format(OffsetDateTime.now()));
      strBuff.append("\r\n");
      ByteBuffer buff = ByteBuffer.wrap(strBuff.toString().getBytes());
      oc.write(buff);
      long current = 0;
      long size = ic.size();
      while (current < size) {
        current += ic.transferTo(current, size, oc);
      }
    } catch (IOException e) {
      throw e;
    }
  }
}
