// (C) 2012 uchicom
package com.uchicom.smtp;

import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * SMTPの定数クラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class Constants {

  public static String PASSWORD_FILE_NAME = ".smtp";
  public static String WEBHOOK_FILE_NAME = ".webhook.yml";
  public static String IGNORE_FILE_NAME = ".ignore";
  public static String IGNORE_RESULT_FILE_NAME = ".ignore_result";
  public static String SPAM_DIR = ".spam";

  // SMTP返却メッセージ
  /** 返却メッセージ(220(STARTTLS開始)) */
  public static String RECV_220 = "220";

  /** 返却メッセージ(250(成功応答)) */
  public static String RECV_250 = "250";

  /** 返却メッセージ(250(成功応答)) */
  public static String RECV_235 = "235 Authentication successful";

  /** 返却メッセージ(250(成功応答)) */
  public static String RECV_334 = "334";

  /** 返却メッセージ(535(認証エラー)) */
  public static String RECV_535 = "535";

  /** 返却メッセージ(行終端文字列) */
  public static String RECV_LINE_END = "\r\n";

  /** 返却メッセージ(250 OK(成功応答)) */
  public static String RECV_250_OK = "250 OK";

  /** 返却メッセージ(550(失敗応答)) */
  public static String RECV_550 = "550 ";

  /** 返却メッセージ(501(アドレス構文エラー)) */
  public static String RECV_501_ADDRESS = "501 Syntax error in address";

  /** 返却メッセージ(500(行長超過)) */
  public static String RECV_500_LINE_TOO_LONG = "500 Line too long";

  /** 返却メッセージ(452(宛先数超過)) */
  public static String RECV_452_TOO_MANY_RCPT = "452 Too many recipients";

  /** 返却メッセージ(552(サイズ超過)) */
  public static String RECV_552 = "552 Message size exceeds fixed maximum message size";

  /** 返却メッセージ(421(接続数超過)) */
  public static String RECV_421_BUSY = "421 Too many connections, try again later";

  /** 返却メッセージ(451(配送失敗・一時的)) */
  public static String RECV_451 = "451 Requested action aborted: local error in processing";

  /** 返却メッセージ(554(配送失敗・恒久的)) */
  public static String RECV_554 = "554 Transaction failed";

  /** 返却メッセージ(354(中間応答)) */
  public static String RECV_354 = "354 Start mail input; end with \\r\\n.\\r\\n";

  // No such user here

  /** 認証ユーザー名の正規表現 */
  public static Pattern REG_EXP_USER_NAME = Pattern.compile("[A-Za-z0-9._-]+");

  // SMTPコマンド正規表現
  /** EHLOの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_EHLO = Pattern.compile("^ *[Ee][Hh][Ll][Oo] +[^ ]+ *$");

  /** STARTTLSの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_START_TLS =
      Pattern.compile("^ *[Ss][Tt][Aa][Rr][Tt][Tt][Ll][Ss] *$");

  /** HELOの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_HELO = Pattern.compile("^ *[Hh][Ee][Ll][Oo] +[^ ]+ *$");

  /** MAIL FROM:アドレスの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_MAIL_FROM =
      Pattern.compile("^[Mm][Aa][Ii][Ll] [Ff][Rr][Oo][Mm]:.*$");

  /** RCPT TO:アドレスの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_RCPT_TO = Pattern.compile("^[Rr][Cc][Pp][Tt] [Tt][Oo]:.*$");

  /** コマンドのアドレス部(&lt;local@domain&gt; 後続のESMTPパラメータ可)の正規表現 */
  public static Pattern REG_EXP_ADDRESS =
      Pattern.compile("^<?([^\\s<>@/\\\\:\"]+)@([A-Za-z0-9.-]+)>?(?:\\s+\\S.*)?$");

  /** ヌル逆方向パス(&lt;&gt;、バウンスメール用)の正規表現 */
  public static Pattern REG_EXP_NULL_PATH = Pattern.compile("^<>(?:\\s+\\S.*)?$");

  /** MAIL FROMのSIZEパラメータの正規表現 */
  public static Pattern REG_EXP_SIZE_PARAM = Pattern.compile("(?i)\\sSIZE=(\\d+)(?:\\s|$)");

  /** ローカル部の最大長(RFC 5321) */
  public static int MAX_LOCAL_PART_LENGTH = 64;

  /** アドレスの最大長(RFC 5321) */
  public static int MAX_ADDRESS_LENGTH = 254;

  /** DATAの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_DATA = Pattern.compile("^[Dd][Aa][Tt][Aa] *$");

  /** QUITの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_QUIT = Pattern.compile("^[Qq][Uu][Ii][Tt] *$");

  /** RSETの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_RSET = Pattern.compile("^[Rr][Ss][Ee][Tt] *$");

  /** NOOPの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_NOOP = Pattern.compile("^[Nn][Oo][Oo][Pp] *$");

  /** VRFYの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_VRFY = Pattern.compile("^[Vv][Rr][Ff][Yy] +[^ ]+ *$");

  /** EXPNの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_EXPN = Pattern.compile("^[Ee][Xx][Pp][Nn] +[^ ]+ *$");

  /** HELPの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_HELP = Pattern.compile("^[Hh][Ee][Ll][Pp] *$");

  /** AUTH PLAINの正規表現(大文字小文字後続スペース) */
  public static Pattern REG_EXP_AUTH_LOGIN =
      Pattern.compile("^[Aa][Uu][Tt][Hh] +[Ll][Oo][Gg][Ii][Nn] *$");

  // 初期設定
  /** デフォルトメールボックスディレクトリ */
  public static String DEFAULT_MAILBOX = "./mailbox";

  /** デフォルト待ち受けポート番号 */
  public static String DEFAULT_PORT = "25";

  /** デフォルト接続待ち数 */
  public static String DEFAULT_BACK = "10";

  /** デフォルトスレッドプール数 */
  public static String DEFAULT_POOL = "10";

  /** デフォルト本文行長上限(CRLFを除く文字数, RFC 5321は1000オクテット) */
  public static String DEFAULT_MAX_LINE = "998";

  /** デフォルトコマンド行長上限(CRLFを除く文字数, RFC 5321は512オクテット) */
  public static String DEFAULT_MAX_COMMAND_LINE = "510";

  /** デフォルト1通あたりの宛先数上限(RFC 5321の最低値, Gmailと同じ) */
  public static String DEFAULT_MAX_RCPT = "100";

  /** デフォルトメッセージサイズ上限(バイト, Gmailの受信上限に合わせる) */
  public static String DEFAULT_MAX_SIZE = "35882577";

  /** デフォルトソケットタイムアウト(ミリ秒, 5分) */
  public static String DEFAULT_SO_TIMEOUT = "300000";

  /** デフォルト同時接続数上限 */
  public static String DEFAULT_MAX_CONN = "100";

  /** デフォルトIPごとの同時接続数上限 */
  public static String DEFAULT_MAX_CONN_PER_IP = "10";

  /** デフォルト送信フラグ */
  public static String DEFAULT_TRANSFER = "false";

  /** フォルトDKIMセレクター */
  public static String DEFAULT_DKIM_SELECTOR = "dkim";

  /** 迷惑メールパターン */
  public static final Pattern pattern =
      Pattern.compile(
          "(^Subject:.*(Viagra|VPXL|Penisole|Cialis|Levitra|[^A-z]+(CV|ED|[Pp]il(l|ls)|[Mm]edicine|hair|Salary)([^A-z]+|$)).*)|( *[Cc]lick *[Hh]ere *)|([Cc]hat *[Ll]ater)|([Hh]air *[Ll]oss)|([Ss]alary *is *)|([Cc]asino )");

  /** ログ出力ディレクトリ. */
  public static final String LOG_DIR = "./logs";

  /** ログ出力フォーマット. */
  public static final String LOG_FORMAT =
      "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$s %2$s %5$s%6$s%n";

  /** タイムゾーンID */
  public static final ZoneId ZONE_ID = ZoneId.systemDefault();
}
