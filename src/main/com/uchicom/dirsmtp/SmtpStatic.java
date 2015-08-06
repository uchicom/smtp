/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.File;

/**
 * SMTPの定数クラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class SmtpStatic {

	// SMTP返却メッセージ
	/** 返却メッセージ(250(成功応答)) */
	public static String RECV_250 = "250";
	/** 返却メッセージ(行終端文字列) */
	public static String RECV_LINE_END = "\r\n";
	/** 返却メッセージ(250 OK(成功応答)) */
	public static String RECV_250_OK = "250 OK";
	/** 返却メッセージ(550(失敗応答)) */
	public static String RECV_550 = "550 ";
	/** 返却メッセージ(354(中間応答)) */
	public static String RECV_354 = "354 Start mail input; end with \r\n.\r\n";

	// No such user here

	// SMTPコマンド正規表現
	/** EHLOの正規表現(大文字小文字後続スペース) */
	public static String REG_EXP_EHLO = "^ *[Ee][Hh][Ll][Oo] +[^ ]* *$";
	/** HELOの正規表現(大文字小文字後続スペース) */
	public static String REG_EXP_HELO = "^ *[Hh][Ee][Ll][Oo] +[^ ]* *$";
	/** MAIL FROM:アドレスの正規表現(大文字小文字後続スペース) */
	public static String REG_EXP_MAIL_FROM = "^[Mm][Aa][Ii][Ll] [Ff][Rr][Oo][Mm]:.* *$";
	/** RCPT TO:アドレスの正規表現(大文字小文字後続スペース) */
	public static String REG_EXP_RCPT_TO = "^[Rr][Cc][Pp][Tt] [Tt][Oo]:.* *$";
	/** DATAの正規表現(大文字小文字後続スペース) */
	public static String REG_EXP_DATA = "^[Dd][Aa][Tt][Aa] *$";
	/** QUITの正規表現(大文字小文字後続スペース) */
	public static String REG_EXP_QUIT = "^[Qq][Uu][Ii][Tt] *$";
	/** RSETの正規表現(大文字小文字後続スペース) */
	public static String REG_EXP_RSET = "^[Rr][Ss][Ee][Tt] *$";
	/** NOOPの正規表現(大文字小文字後続スペース) */
	public static String REG_EXP_NOOP = "^[Nn][Oo][Oo][Pp] *$";
	/** VRFYの正規表現(大文字小文字後続スペース) */
	public static String REG_EXP_VRFY = "^[Vv][Rr][Ff][Yy] +[^ ]+ *$";
	/** EXPNの正規表現(大文字小文字後続スペース) */
	public static String REG_EXP_EXPN = "^[Ee][Xx][Pp][Nn] +[^ ]+ *$";
	/** HELPの正規表現(大文字小文字後続スペース) */
	public static String REG_EXP_HELP = "^[Hh][Ee][Ll][Pp] *$";

	// 初期設定
	/** デフォルトメールボックスディレクトリ */
	public static File DEFAULT_MAILBOX = new File("./mailbox");
	/** デフォルト待ち受けポート番号 */
	public static int DEFAULT_PORT = 25;
	/** デフォルト接続待ち数 */
	public static int DEFAULT_BACK = 10;
	/** デフォルトスレッドプール数 */
	public static int DEFAULT_POOL = 10;
}
