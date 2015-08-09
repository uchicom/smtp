/**
 * (c) 2014 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Writer;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * SMTP処理クラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class SmtpProcess {

	private SimpleDateFormat format = new SimpleDateFormat(
			"yyyyMMdd_HHmmss.SSS");
	private SmtpParameter parameter;
	private Socket socket;

	private boolean bHelo;
	private boolean bMailFrom;
	private boolean bRcptTo;
	private boolean bData;

	private String senderAddress;
	private String helo;
	private String mailFrom;
	private Mail mail;

	private List<MailBox> boxList = new ArrayList<MailBox>();
	private static final int ERROR_COUNT = 3;

	/** 送付先一覧 */
	private List<MailBox> rcptList = new ArrayList<MailBox>();

	/**
	 * コンストラクタ.
	 *
	 * @param parameter
	 * @param socket
	 * @throws IOException
	 */
	public SmtpProcess(SmtpParameter parameter, Socket socket)
			throws IOException {
		this.parameter = parameter;
		this.socket = socket;
		this.senderAddress = socket.getInetAddress().getHostAddress();
	}

	public void execute() {
		execute(null, System.out);
	}

	/**
	 * SMTP処理を実行
	 *
	 * @throws IOException
	 */
	public void execute(Map<String, Integer> rejectMap, PrintStream logStream) {

		logStream.println(System.currentTimeMillis() + ":"
				+ String.valueOf(senderAddress));
		BufferedReader br = null;
		PrintStream ps = null;
		Writer writer = null;
		try {
			//転送はしないので３回チャレンジしたサーバは除外する.
			if (rejectMap != null && rejectMap.containsKey(senderAddress)) {
				if (rejectMap.get(senderAddress).intValue() >= ERROR_COUNT) {
					return;
				}
			}
			br = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			ps = new PrintStream(socket.getOutputStream());
			SmtpUtil.recieveLine(ps, "220 ", parameter.getHostName(), " SMTP");
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
				if (bData) {
					if (".".equals(line)) {
						// メッセージ終了
						writer.close();
						// メッセージコピー処理
						try {
							mail.copy(boxList);
							mail.delete();
						} catch (Exception e) {
							e.printStackTrace();
						}
						mail = null;
						rcptList.clear();
						SmtpUtil.recieveLine(ps, SmtpStatic.RECV_250_OK);
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
					SmtpUtil.recieveLine(ps, SmtpStatic.RECV_250, " ",
							parameter.getHostName(), " Hello ", senderAddress);
					init();
				} else if (SmtpUtil.isRset(line)) {
					SmtpUtil.recieveLine(ps, SmtpStatic.RECV_250_OK);
					init();
				} else if (SmtpUtil.isMailFrom(line)) {
					if (bHelo) {
						mailFrom = line.substring(10).trim()
								.replaceAll("[<>]", "");
						logStream.println(mailFrom);
						SmtpUtil.recieveLine(ps, SmtpStatic.RECV_250_OK);
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
						if (addresses[1].equals(parameter.getHostName())) {
							// 宛先チェック
							boolean checkOK = false;
							if (parameter.isMemory()) {
								for (String user : parameter.getUsers()) {
									if (addresses[0].equals(user)) {
										boxList.add(new MailBox(parameter.getMailList(user)));
										checkOK = true;
										break;
									}
								}

							} else {
								for (File box : parameter.getBase().listFiles()) {
									if (box.isDirectory()) {
										if (addresses[0].equals(box.getName())) {
											checkOK = true;
											boxList.add(new MailBox(box));
											break;
										}
									}
								}
							}
							if (checkOK) {
								SmtpUtil.recieveLine(ps, SmtpStatic.RECV_250_OK);
								bRcptTo = true;
							} else {
								// エラーユーザー存在しない
								SmtpUtil.recieveLine(ps, "550 Failure reply");
								if (rejectMap != null) {
									if (rejectMap.containsKey(senderAddress)) {
										rejectMap.put(senderAddress, rejectMap.get(senderAddress) + 1);
									} else {
										rejectMap.put(senderAddress, Integer.valueOf(1));
									}
								}
								if (rejectMap.get(senderAddress).intValue() >= ERROR_COUNT) {
									return;
								}
							}
						} else {
							// エラーホストが違う
							SmtpUtil.recieveLine(ps, "500");
							if (rejectMap != null) {
								if (rejectMap.containsKey(senderAddress)) {
									rejectMap.put(senderAddress, rejectMap.get(senderAddress) + 1);
								} else {
									rejectMap.put(senderAddress, Integer.valueOf(1));
								}
							}
						}
					} else {
						// エラー500
						SmtpUtil.recieveLine(ps, "500");
					}
				} else if (SmtpUtil.isData(line)) {
					if (bRcptTo) {
						if (parameter.isMemory()) {
							mail = new MemoryMail();
						} else {
							mail = new FileMail(new File(new File(parameter.getBase(), "@rcpt"), helo.replaceAll(":", "_")
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
						SmtpUtil.recieveLine(ps, SmtpStatic.RECV_354);
						bData = true;
					} else {
						// エラー
						SmtpUtil.recieveLine(ps, "500");
					}
				} else if (SmtpUtil.isHelp(line)) {
					SmtpUtil.recieveLine(ps, "250");
				} else if (SmtpUtil.isQuit(line)) {
					SmtpUtil.recieveLine(ps, "221 ", parameter.getHostName());
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
					mail.copy(boxList);
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
}
