package com.uchicom.dirsmtp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;

/**
 * SMTPの処理で使用するユーティリティークラス.
 * @author shigeki
 *
 */
public class SmtpUtil {

	/**
	 * コマンドがEHLOかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isEhelo(String cmd) {
		return cmd.matches(SmtpStatic.REG_EXP_EHLO);
	}

	/**
	 * コマンドがHELOかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isHelo(String cmd) {
		return cmd.matches(SmtpStatic.REG_EXP_HELO);
	}

	/**
	 * コマンドがRSETかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isRset(String cmd) {
		return cmd.matches(SmtpStatic.REG_EXP_RSET);
	}

	/**
	 * コマンドがMAIL_FROMかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isMailFrom(String cmd) {
		return cmd.matches(SmtpStatic.REG_EXP_MAIL_FROM);
	}

	/**
	 * コマンドがRCPT_TOかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isRcptTo(String cmd) {
		return cmd.matches(SmtpStatic.REG_EXP_RCPT_TO);
	}

	/**
	 * コマンドがDATAかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isData(String cmd) {
		return cmd.matches(SmtpStatic.REG_EXP_DATA);
	}

	/**
	 * コマンドがQUITかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isQuit(String cmd) {
		return cmd.matches(SmtpStatic.REG_EXP_QUIT);
	}

	/**
	 * コマンドがNOOPかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isNoop(String cmd) {
		return cmd.matches(SmtpStatic.REG_EXP_NOOP);
	}

	/**
	 * コマンドがVRFYかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isVrfy(String cmd) {
		return cmd.matches(SmtpStatic.REG_EXP_VRFY);
	}

	/**
	 * コマンドがEXPNかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isExpn(String cmd) {
		return cmd.matches(SmtpStatic.REG_EXP_EXPN);
	}

	/**
	 * コマンドがHELPかどうかをチェックする.
	 * @param cmd
	 * @return
	 */
	public static boolean isHelp(String cmd) {
		return cmd.matches(SmtpStatic.REG_EXP_HELP);
	}

	/**
	 * ステータス行を出力する.
	 * @param ps
	 * @param strings
	 */
	public static void recieveLine(PrintStream ps, String... strings) {
		for (String string : strings) {
			ps.print(string);
		}
		ps.print(SmtpStatic.RECV_LINE_END);
		ps.flush();
	}


	/**
	 * ファイルコピー処理
	 * 
	 * @param from
	 * @param to
	 */
	public static void copyFile(File from, File to) throws IOException {
		FileChannel ic = null;
		FileChannel oc = null;
		try {
			ic = new FileInputStream(from).getChannel();
			oc = new FileOutputStream(to).getChannel();
			ic.transferTo(0, ic.size(), oc);
		} catch (IOException e) {
			throw e;
		} finally {
			if (ic != null)
				try {
					ic.close();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					ic = null;
				}
			if (oc != null)
				try {
					oc.close();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					oc = null;
				}
		}
	}
}
