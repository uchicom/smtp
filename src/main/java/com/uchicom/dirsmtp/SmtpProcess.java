/**
 * (c) 2014 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import com.uchicom.server.Parameter;
import com.uchicom.server.ServerProcess;

/**
 * SMTP処理クラス.
 * 送信処理認証後は、他サーバに直接接続してプロキシ―のような処理をする。
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class SmtpProcess implements ServerProcess {

	private SimpleDateFormat format = new SimpleDateFormat(
			"yyyyMMdd_HHmmss.SSS");
	private Parameter parameter;
	private Socket socket;

	private boolean bHelo;
	private boolean bMailFrom;
	private boolean bRcptTo;
	private boolean bData;
	private boolean bAuth;
	private int authStatus;

	private String senderAddress;
	private String helo;
	private String mailFrom;
	private Mail mail;
	private String authName;

	private List<MailBox> boxList = new ArrayList<MailBox>();

	/** 送付先一覧 */
	private List<MailBox> rcptList = new ArrayList<MailBox>();

	private long startTime = System.currentTimeMillis();

	/**
	 * コンストラクタ.
	 *
	 * @param parameter
	 * @param socket
	 * @throws IOException
	 */
	public SmtpProcess(Parameter parameter, Socket socket)
			throws IOException {
		this.parameter = parameter;
		this.socket = socket;
		this.senderAddress = socket.getInetAddress().getHostAddress();
	}

	public void execute() {
		execute(System.out);
	}

	/**
	 * SMTP処理を実行
	 *
	 * @throws IOException
	 */
	public void execute(PrintStream logStream) {

		logStream.println(System.currentTimeMillis() + ":"
				+ String.valueOf(senderAddress));
		BufferedReader br = null;
		PrintStream ps = null;
		Writer writer = null;
		try {
			br = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
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
			while (line != null) {
				logStream.println("[" + line + "]");
				if (authStatus > 0 && authStatus < 3) {
					switch (authStatus) {
					case 1:
						String name = new String(Base64.getDecoder().decode(line));
						File dir = new File(parameter.getFile("dir"), name);
						if (dir.exists() && dir.isDirectory()) {
							authStatus = 2;
							authName = name;

							SmtpUtil.recieveLine(ps,
									Constants.RECV_334, " UGFzc3dvcmQ6");
						}
						break;
					case 2:
						String pass = new String(Base64.getDecoder().decode(line));
						File passwordFile = new File(new File(parameter.getFile("dir"), authName), "pass.txt");
						if (passwordFile.exists() && passwordFile.isFile()) {
							try (BufferedReader passReader = new BufferedReader(
									new InputStreamReader(
											new FileInputStream(
													passwordFile)));) {
								String password = passReader.readLine();
								while ("".equals(password)) {
									password = passReader.readLine();
								}
								if (pass.equals(password)) {

									SmtpUtil.recieveLine(ps,
											Constants.RECV_235);
									bAuth = true;
									authStatus = 3;
								} else {
									// パスワード不一致エラー
									SmtpUtil.recieveLine(ps,
											Constants.RECV_535);
									authStatus = 0;
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
						// メッセージコピー処理
						try {
							mail.copy(boxList, socket.getLocalAddress().getHostName() ,socket.getInetAddress().getHostName());
							mail.delete();
						} catch (Exception e) {
							e.printStackTrace();
						}
						mail = null;
						rcptList.clear();
						SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
						init();
					} else {
						// メッセージ本文
						writer.write(line);
						writer.write("\r\n");
					}
				} else if (!bHelo
						&& (SmtpUtil.isEhelo(line) || SmtpUtil.isHelo(line))) {
					bHelo = true;
					String[] lines = line.split(" +");
					helo = lines[1];
					SmtpUtil.recieveLine(ps, Constants.RECV_250, "-",
							parameter.get("host"), " Hello ", senderAddress);
					SmtpUtil.recieveLine(ps, Constants.RECV_250, " AUTH LOGIN");
					init();
				} else if (SmtpUtil.isAuthLogin(line)){
					authStatus = 1;
					SmtpUtil.recieveLine(ps, Constants.RECV_334, " VXNlcm5hbWU6");
				} else if (SmtpUtil.isRset(line)) {
					SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
					init();
				} else if (SmtpUtil.isMailFrom(line)) {
					if (bHelo) {
						mailFrom = line.substring(10).trim()
								.replaceAll("[<>]", "");
						logStream.println(mailFrom);
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
						logStream.println(addresses[0]);
						logStream.println(addresses[1]);
						if (addresses[1].equals(parameter.get("host"))) {
							// 宛先チェック
							boolean checkOK = false;
							if (parameter.is("memory")) {
								for (String user : Context.singleton().getUsers()) {
									if (addresses[0].equals(user)) {
										boxList.add(new MailBox(address, Context.singleton().getMailList(user)));
										checkOK = true;
										break;
									}
								}

							} else {
								for (File box : parameter.getFile("base").listFiles()) {
									if (box.isDirectory()) {
										if (addresses[0].equals(box.getName())) {
											checkOK = true;
											boxList.add(new MailBox(address, box));
											break;
										}
									}
								}
							}
							if (checkOK) {
								SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
								bRcptTo = true;
							} else {
								// エラーユーザー存在しない
								SmtpUtil.recieveLine(ps, "550 Failure reply");
							}
						} else if (bAuth) {
							//認証済みなので転送OKする

							SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
							bRcptTo = true;
						} else {
							// エラーホストが違う
							SmtpUtil.recieveLine(ps, "500");
						}
					} else {
						// エラー500
						SmtpUtil.recieveLine(ps, "500");
					}
				} else if (SmtpUtil.isData(line)) {
					if (bRcptTo) {
						if (parameter.is("memory")) {
							mail = new MemoryMail();
						} else {
							mail = new FileMail(new File(new File(parameter.getFile("dir"), "@rcpt"), helo.replaceAll(":", "_")
									+ "_"
									+ mailFrom
									+ "~"
									+ senderAddress.replaceAll(":", "_")
									+ "_"
									+ format.format(new Date())
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
					SmtpUtil.recieveLine(ps,
							"500 Syntax error, command unrecognized");
				}
				startTime = System.currentTimeMillis();
				line = br.readLine();
			}

			br.close();
			ps.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		} finally {
			if (mail != null) {
				// メッセージコピー処理
				try {
					mail.copy(boxList, socket.getInetAddress().getHostName(), InetAddress.getLocalHost().getHostName());
					mail.delete();
				} catch (Exception e) {
					e.printStackTrace();
				}
				mail = null;
				rcptList.clear();
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

	/**
	 * フラグ初期化.
	 */
	private void init() {
		mail = null;
		bMailFrom = false;
		bRcptTo = false;
		bData = false;
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
			File box = new File(parameter.getFile("base"), user);
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
		return 0;
	}

}
