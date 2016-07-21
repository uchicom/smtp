/**
 * (c) 2014 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SMTP実行用のパラメータクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class SmtpParameter {

	/** メールボックスの基準フォルダ */
	private File base = SmtpStatic.DEFAULT_MAILBOX;

	/** ホスト名 */
	private String hostName = "localhost";

	/** 待ち受けポート */
	private int port = SmtpStatic.DEFAULT_PORT;

	/** 受信する接続 (接続要求) のキューの最大長 */
	private int backlog = SmtpStatic.DEFAULT_BACK;

	/** プールするスレッド数 */
	private int pool = SmtpStatic.DEFAULT_POOL;

	/** メモリ起動 */
	private boolean memory;

	/** ユーザー名一覧 */
	private String[] users;


	private String[] args;

	protected Map<String, List<Mail>> boxMap;

	/**
	 * 引数指定のコンストラクター.
	 * @param args 引数
	 */
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
		//        if (args.length < 1) {
		//            ps.println("args.length < 1");
		//            return false;
		//        }
		for (int i = 0; i < args.length - 1; i++) {
			if ("-server".equals(args[i])) {

			} else if ("-dir".equals(args[i])) {// メールフォルダ格納フォルダ
				base = new File(args[++i]);
				if (!base.exists() || !base.isDirectory()) {
					ps.println("mailbox directory is not found.");
					return false;
				}
			} else if ("-host".equals(args[i])) {// ホスト名
				hostName = args[++i];
			} else if ("-port".equals(args[i])) {// ポート
				port = Integer.parseInt(args[++i]);
			} else if ("-back".equals(args[i])) {// 接続待ち数
				backlog = Integer.parseInt(args[++i]);
			} else if ("-pool".equals(args[i])) {// スレッドプール数
				pool = Integer.parseInt(args[++i]);
			} else if ("-memory".equals(args[i])) {
				memory = true;
				users = args[++i].split(",");
				boxMap = new HashMap<>();
				for (String user : users) {
					boxMap.put(user, new ArrayList<Mail>());
				}
			}
		}

		return true;
	}

	/**
	 * hostNameを取得します.
	 *
	 * @return hostName
	 */
	public String getHostName() {
		return hostName;
	}

	/**
	 * baseを取得します.
	 *
	 * @return base
	 */
	public File getBase() {
		return base;
	}

	/**
	 * portを取得します.
	 *
	 * @return port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * backlogを取得します.
	 *
	 * @return backlog
	 */
	public int getBacklog() {
		return backlog;
	}

	/**
	 * poolを取得します.
	 *
	 * @return pool
	 */
	public int getPool() {
		return pool;
	}

	/**
	 * memoryを取得します.
	 *
	 * @return memory
	 */
	public boolean isMemory() {
		return memory;
	}

	public List<Mail> getMailList(String user) {
		if (memory) {
			if (boxMap != null) {
				return boxMap.get(user);
			}
		} else {
			File userDir = new File(base, user);
			if (userDir.exists()) {
				File[] files = userDir.listFiles();
				List<Mail> mailList = new ArrayList<Mail>(files.length);
				for (File file : files) {
					try {
						mailList.add(new FileMail(file));
					} catch (IOException e) {
						// TODO 自動生成された catch ブロック
						e.printStackTrace();
					}
				}
				return mailList;
			}
		}
		return null;
	}

	public void addMail(String user, Mail mail) {
		boxMap.get(user).add(mail);
	}

	public String[] getUsers() {
		return users;
	}
}
