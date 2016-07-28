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
	/** 実行するサーバのタイプ */
	private String type = "single";

	private String[] args;

	protected Map<String, List<Mail>> boxMap;

	/**
	 * 引数指定のコンストラクター.
	 *
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
		for (int i = 0; i < args.length - 1; i++) {
			switch (args[i]) {
			case "-server":
				break;
			case "-dir":// メールフォルダ格納フォルダ
				base = new File(args[++i]);
				if (!base.exists() || !base.isDirectory()) {
					ps.println("mailbox directory is not found.");
					return false;
				}
				break;
			case "-host":// ホスト名
				hostName = args[++i];
				break;
			case "-port":// ポート
				port = Integer.parseInt(args[++i]);
				break;
			case "-back":// 接続待ち数
				backlog = Integer.parseInt(args[++i]);
				break;
			case "-pool":// スレッドプール数
				pool = Integer.parseInt(args[++i]);
				break;
			case "-memory":
				memory = true;
				users = args[++i].split(",");
				boxMap = new HashMap<>();
				for (String user : users) {
					boxMap.put(user, new ArrayList<Mail>());
				}
				break;
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

    public Server createServer() {
    	Server server = null;
		switch (type) {
		case "multi":
			server = new MultiSmtpServer(this);
		case "pool":
			server = new PoolSmtpServer(this);
			break;
		case "single":
			server = new SingleSmtpServer(this);
			break;
		}
    	return server;
    }
}
