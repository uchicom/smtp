/**
 * (c) 2014 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.File;
import java.io.PrintStream;

/**
 * パラメータクラス.
 * 
 * @author uchicom: Shigeki Uchiyama
 * 
 */
public class SmtpParameter {
	/** メールボックスの基準フォルダ */
	private File base;
	/** ホスト名 */
	private String hostName;
	/** 待ち受けポート */
	private int port;
	/** 受信する接続 (接続要求) のキューの最大長 */
	private int backlog;
	/** プールするスレッド数 */
	private int pool;

	private String[] args;

	public SmtpParameter(String[] args) {
		this.args = args;
	}

	/**
	 * 初期化
	 * 
	 * @param ps
	 * @return
	 */
	public boolean init(PrintStream ps) {
		if (args.length < 3) {
			System.out.println("args.length >= 3");
			return false;
		}
		// メールフォルダ格納フォルダ
		base = SmtpStatic.DEFAULT_MAILBOX;
		if (!base.exists() || !base.isDirectory()) {
			System.out.println("mailbox directory is not found.");
			return false;
		}
		// メール
		hostName = args[1];

		// ポート
		port = SmtpStatic.DEFAULT_PORT;
		if (args.length > 2) {
			port = Integer.parseInt(args[2]);
		}
		// 接続待ち数
		backlog = SmtpStatic.DEFAULT_BACK;
		if (args.length > 3) {
			backlog = Integer.parseInt(args[3]);
		}
		// スレッドプール数
		pool = SmtpStatic.DEFAULT_POOL;
		if (args.length > 4) {
			pool = Integer.parseInt(args[4]);
		}

		return true;
	}

	public String getHostName() {
		return hostName;
	}

	public int getPort() {
		return port;
	}

	public int getBacklog() {
		return backlog;
	}

	public File getBase() {
		return base;
	}

	public int getPool() {
		return pool;
	}
}
