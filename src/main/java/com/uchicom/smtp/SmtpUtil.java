// (c) 2014 uchicom
package com.uchicom.smtp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SMTPの処理で使用するユーティリティークラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class SmtpUtil {

	private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME;
	/**
	 * コマンドがEHLOかどうかをチェックする.
	 *
	 * @param cmd コマンド
	 * @return コマンドがEHLOの場合はtrue,それ以外はfalse
	 */
	public static boolean isEhlo(String cmd) {
		return cmd.matches(Constants.REG_EXP_EHLO);
	}

	/**
	 * コマンドがHELOかどうかをチェックする.
	 *
	 * @param cmd コマンド
	 * @return コマンドがHELOの場合はtrue,それ以外はfalse
	 */
	public static boolean isHelo(String cmd) {
		return cmd.matches(Constants.REG_EXP_HELO);
	}

	/**
	 * コマンドがRSETかどうかをチェックする.
	 *
	 * @param cmd コマンド
	 * @return コマンドがRSETの場合はtrue,それ以外はfalse
	 */
	public static boolean isRset(String cmd) {
		return cmd.matches(Constants.REG_EXP_RSET);
	}

	/**
	 * コマンドがMAIL_FROMかどうかをチェックする.
	 *
	 * @param cmd コマンド
	 * @return コマンドがMAIL_FROMの場合はtrue,それ以外はfalse
	 */
	public static boolean isMailFrom(String cmd) {
		return cmd.matches(Constants.REG_EXP_MAIL_FROM);
	}

	/**
	 * コマンドがRCPT_TOかどうかをチェックする.
	 *
	 * @param cmd コマンド
	 * @return コマンドがRCPT_TOの場合はtrue,それ以外はfalse
	 */
	public static boolean isRcptTo(String cmd) {
		return cmd.matches(Constants.REG_EXP_RCPT_TO);
	}

	/**
	 * コマンドがDATAかどうかをチェックする.
	 *
	 * @param cmd コマンド
	 * @return コマンドがDATAの場合はtrue,それ以外はfalse
	 */
	public static boolean isData(String cmd) {
		return cmd.matches(Constants.REG_EXP_DATA);
	}

	/**
	 * コマンドがQUITかどうかをチェックする.
	 *
	 * @param cmd コマンド
	 * @return コマンドがQUITの場合はtrue,それ以外はfalse
	 */
	public static boolean isQuit(String cmd) {
		return cmd.matches(Constants.REG_EXP_QUIT);
	}

	/**
	 * コマンドがNOOPかどうかをチェックする.
	 *
	 * @param cmd コマンド
	 * @return コマンドがNOOPの場合はtrue,それ以外はfalse
	 */
	public static boolean isNoop(String cmd) {
		return cmd.matches(Constants.REG_EXP_NOOP);
	}

	/**
	 * コマンドがVRFYかどうかをチェックする.
	 *
	 * @param cmd コマンド
	 * @return コマンドがVRFYの場合はtrue,それ以外はfalse
	 */
	public static boolean isVrfy(String cmd) {
		return cmd.matches(Constants.REG_EXP_VRFY);
	}

	/**
	 * コマンドがEXPNかどうかをチェックする.
	 *
	 * @param cmd コマンド
	 * @return コマンドがEXPNの場合はtrue,それ以外はfalse
	 */
	public static boolean isExpn(String cmd) {
		return cmd.matches(Constants.REG_EXP_EXPN);
	}

	/**
	 * コマンドがHELPかどうかをチェックする.
	 *
	 * @param cmd コマンド
	 * @return コマンドがHELPの場合はtrue,それ以外はfalse
	 */
	public static boolean isHelp(String cmd) {
		return cmd.matches(Constants.REG_EXP_HELP);
	}
	public static boolean isAuthLogin(String cmd) {
		return cmd.matches(Constants.REG_EXP_AUTH_LOGIN);
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
	 */
	public static void copyFile(File from, File to, String mailAddress, String senderHostName, String localHostName) throws IOException {
		try (@SuppressWarnings("resource")
		FileChannel ic = new FileInputStream(from).getChannel();
			@SuppressWarnings("resource")
			FileChannel oc = new FileOutputStream(to).getChannel();){

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
