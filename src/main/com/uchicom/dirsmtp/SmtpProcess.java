package com.uchicom.dirsmtp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 
 * @author shigeki
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
	private File mailFile;
	private File rcptBox;

	/** 送付先一覧 */
	private List<File> rcptList = new ArrayList<File>();

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

	/**
	 * SMTP処理を実行
	 * 
	 * @throws IOException
	 */
	public void execute() {

		System.out.println(System.currentTimeMillis() + ":"
				+ String.valueOf(senderAddress));
		BufferedReader br = null;
		PrintStream ps = null;
		try {
			br = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			ps = new PrintStream(socket.getOutputStream());
			rcptBox = new File(parameter.getBase(), "@"
					+ Thread.currentThread().getId());
			if (!rcptBox.exists()) {
				rcptBox.mkdirs();
			}
			SmtpUtil.recieveLine(ps, "220 ", parameter.getHostName(), " SMTP");
			String line = br.readLine();
			OutputStreamWriter writer = null;
			// HELO
			// MAIL FROM:
			// RCPT TO:
			// DATA
			// MAIL FROM:
			// RCPT TO:
			// DATAの順で処理する
			while (line != null) {
				System.out.println("[" + line + "]");
				if (bData) {
					if (".".equals(line)) {
						// メッセージ終了
						writer.close();
						// メッセージコピー処理
						try {
							for (File userBox : rcptList) {
								SmtpUtil.copyFile(mailFile, new File(userBox,
										mailFile.getName()));
							}
							mailFile.delete();
						} catch (Exception e) {
							e.printStackTrace();
						}
						mailFile = null;
						rcptList.clear();
						SmtpUtil.recieveLine(ps, SmtpStatic.RECV_250_OK);
						init();
					} else {
						// メッセージ本文
						writer.write(line);
						writer.write("\r\n");
						writer.flush();
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
						System.out.println(mailFrom);
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
						System.out.println(addresses[0]);
						System.out.println(addresses[1]);
						if (addresses[1].equals(parameter.getHostName())) {
							// 宛先チェック
							boolean checkOK = false;
							for (File box : parameter.getBase().listFiles()) {
								if (box.isDirectory()) {
									if (addresses[0].equals(box.getName())) {
										checkOK = true;
										rcptList.add(box);
									}
								}
							}
							if (checkOK) {
								SmtpUtil.recieveLine(ps, SmtpStatic.RECV_250_OK);
								bRcptTo = true;
							} else {
								// エラーユーザー存在しない
								SmtpUtil.recieveLine(ps, "500");
							}
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
						mailFile = new File(rcptBox, helo.replaceAll(":", "_")
								+ "_"
								+ mailFrom
								+ "~"
								+ senderAddress.replaceAll(":", "_")
								+ "_"
								+ format.format(new Date(System
										.currentTimeMillis())));
						mailFile.createNewFile();
						writer = new OutputStreamWriter(new FileOutputStream(
								mailFile));
						SmtpUtil.recieveLine(ps, SmtpStatic.RECV_354);
						bData = true;
					} else {
						// エラー
						SmtpUtil.recieveLine(ps, "500");
					}
				} else if (SmtpUtil.isHelp(line)) {
					SmtpUtil.recieveLine(ps, "250");
				} else if (SmtpUtil.isQuit(line)) {
					SmtpUtil.recieveLine(ps, "221", parameter.getHostName());
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
			if (socket != null) {
				try {
					socket.close();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					socket = null;
				}
			}
			// フォルダ削除
			if (rcptBox != null) {
				rcptBox.delete();
			}
		}
	}

	/**
	 * フラグ初期化.
	 */
	private void init() {
		bMailFrom = false;
		bRcptTo = false;
		bData = false;
	}
}
