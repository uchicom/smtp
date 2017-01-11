// (c) 2014 uchicom
package com.uchicom.smtp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

import com.uchicom.server.Parameter;
import com.uchicom.server.ServerProcess;

/**
 * SMTP処理クラス. 送信処理認証後は、他サーバに直接接続してプロキシ―のような処理をする。
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
	/** 外のメールサーバに転送する */
	private boolean bTransfer;
	private int authStatus;

	private String senderAddress;
	private String helo;
	private String mailFrom;
	private Mail mail;
	private String authName;

	private List<MailBox> boxList = new ArrayList<>();

	/** 送付先一覧 */
	private List<MailBox> rcptList = new ArrayList<>();

	/** 転送アドレス一覧 */
	private List<String> transferList = new ArrayList<>();

	private long startTime = System.currentTimeMillis();

	/**
	 * コンストラクタ.
	 *
	 * @param parameter
	 * @param socket
	 * @throws IOException
	 */
	public SmtpProcess(Parameter parameter, Socket socket) {
		this.parameter = parameter;
		this.socket = socket;
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
		this.senderAddress = socket.getInetAddress().getHostAddress();
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
				logStream.println("status:" + authStatus);
				if (authStatus > 0 && authStatus < 3) {
					switch (authStatus) {
					case 1:
						String name = new String(Base64.getDecoder().decode(line));
						File dir = new File(parameter.getFile("dir"), name);
						if (dir.exists() && dir.isDirectory()) {
							authStatus = 2;
							authName = name;

							SmtpUtil.recieveLine(ps,
									Constants.RECV_334,
									" UGFzc3dvcmQ6");
						}
						break;
					case 2:
						String pass = new String(Base64.getDecoder().decode(line));
						File passwordFile = new File(new File(parameter.getFile("dir"), authName), Constants.PASSWORD_FILE_NAME);
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
						if (bTransfer) {
							logStream.println("transferList:" + transferList);
							for (String address : transferList) {
								//転送処理を実行する。

								logStream.println("transfer:" + address);
								String[] addresses = address.split("@");
								String[] hosts = lookupMailHosts(addresses[1]);
								for (String hostss : hosts) {

									try (Socket transferSocket = new Socket(hostss, 25);
											BufferedReader reader = new BufferedReader(new InputStreamReader(transferSocket.getInputStream()));
											BufferedWriter writer2 = new BufferedWriter(new OutputStreamWriter(transferSocket.getOutputStream()));) {
										logStream.println("t[" + reader.readLine() + "]");
										startTime = System.currentTimeMillis();
										writer2.write("EHLO uchicom.com\r\n");//EHLO
										writer2.flush();
										startTime = System.currentTimeMillis();
										String rec = null;
//										boolean starttls = false;
										do {
											rec = reader.readLine();
											logStream.println("t[" + rec + "]");
											if (rec.contains("STARTTLS")) {
//												starttls = true;
											}
										}while (rec != null && rec.startsWith("250-"));
										startTime = System.currentTimeMillis();
//										if (starttls) {
//											writer2.write("STARTTLS\r\n");// STARTTLS
//											writer2.flush();
//											startTime = System.currentTimeMillis();
//											logStream.println("t[" + reader.readLine() + "]");
//											writer2.close();
//											reader.close();
//											//SSL処理開始
//											SSLSocket sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(
//								                       socket,
//								                       socket.getInetAddress().getHostAddress(),
//								                       socket.getPort(),
//								                       true);
////											sslSocket.setEnabledProtocols(sslSocket.getSupportedProtocols());
////
////											sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());
//											sslSocket.setEnableSessionCreation(true);
//											sslSocket.setUseClientMode(true);
//											logStream.println("startHandshake");
//											startTime = System.currentTimeMillis();
//											sslSocket.startHandshake();
//											startTime = System.currentTimeMillis();
//											logStream.println("reader2");
//											BufferedReader reader2 = new BufferedReader(new InputStreamReader(sslSocket.getInputStream()));
//
//											logStream.println("writer3");
//											BufferedWriter writer3 = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream()));
//
//											startTime = System.currentTimeMillis();
//
//											logStream.println("ehlo");
//											writer3.write("EHLO uchicom.com\r\n");//EHLO
//											logStream.println("flush");
//											writer3.flush();
//											startTime = System.currentTimeMillis();
//											rec = null;
//											do {
//												logStream.println("readline");
//												rec = reader2.readLine();
//												logStream.println("t[" + rec + "]");
//											}while (rec != null && rec.startsWith("250-"));
//											logStream.println("mailFrom3:" + mailFrom);
//											writer3.write("MAIL FROM: <" + mailFrom + ">\r\n");// MAIL FROM:
//											writer3.flush();
//											startTime = System.currentTimeMillis();
//											logStream.println("t[" + reader2.readLine() + "]");
//											startTime = System.currentTimeMillis();
//											writer3.write("RCPT TO: <" + address + ">\r\n");// RCPT TO:
//											writer3.flush();
//											startTime = System.currentTimeMillis();
//											logStream.println("t[" + reader2.readLine() + "]");
//											startTime = System.currentTimeMillis();
//											writer3.write("DATA\r\n");// DATA
//											writer3.flush();
//											startTime = System.currentTimeMillis();
//											logStream.println("t[" + reader2.readLine() + "]");
//											startTime = System.currentTimeMillis();
//											writer3.write(mail.getTitle());
//											writer3.flush();
//											startTime = System.currentTimeMillis();
//											writer3.write(".\r\n");
//											writer3.flush();
//											logStream.println("t[" + reader2.readLine() + "]");
//											startTime = System.currentTimeMillis();
//											writer3.write("QUIT\r\n");
//											writer3.flush();
//											logStream.println("t[" + reader2.readLine() + "]");
//											startTime = System.currentTimeMillis();
//											reader2.close();
//											writer3.close();
//										} else {
											logStream.println("mailFrom:" + mailFrom);
											writer2.write("MAIL FROM: <" + mailFrom + ">\r\n");// MAIL FROM:
											writer2.flush();
											startTime = System.currentTimeMillis();
											logStream.println("t[" + reader.readLine() + "]");
											startTime = System.currentTimeMillis();
											writer2.write("RCPT TO: <" + address + ">\r\n");// RCPT TO:
											writer2.flush();
											startTime = System.currentTimeMillis();
											logStream.println("t[" + reader.readLine() + "]");
											startTime = System.currentTimeMillis();
											writer2.write("DATA\r\n");// DATA
											writer2.flush();
											startTime = System.currentTimeMillis();
											logStream.println("t[" + reader.readLine() + "]");
											startTime = System.currentTimeMillis();
											writer2.write(mail.getTitle());
											writer2.flush();
											startTime = System.currentTimeMillis();
											writer2.write(".\r\n");
											writer2.flush();
											logStream.println("t[" + reader.readLine() + "]");
											startTime = System.currentTimeMillis();
											writer2.write("QUIT\r\n");
											writer2.flush();
											logStream.println("t[" + reader.readLine() + "]");
											startTime = System.currentTimeMillis();
//										}
									}
									logStream.println("mx!:" + hostss);
									break;
								}
							}
							mail.delete();
						} else {
							// メッセージコピー処理
							try {
								mail.copy(boxList,
										socket.getLocalAddress().getHostName(),
										socket.getInetAddress().getHostName());
								mail.delete();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						mail = null;
						transferList.clear();
						rcptList.clear();
						SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
						init();
					} else if (line.indexOf(0) == '.') {
						writer.write(line.substring(1));
						writer.write("\r\n");
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
					SmtpUtil.recieveLine(ps,
							Constants.RECV_250,
							"-",
							parameter.get("host"),
							" Hello ",
							senderAddress);
					SmtpUtil.recieveLine(ps, Constants.RECV_250, " AUTH LOGIN");
					init();
				} else if (SmtpUtil.isAuthLogin(line)) {
					authStatus = 1;
					SmtpUtil.recieveLine(ps, Constants.RECV_334, " VXNlcm5hbWU6");
				} else if (SmtpUtil.isRset(line)) {
					SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
					init();
				} else if (SmtpUtil.isMailFrom(line)) {
					if (bHelo) {
						mailFrom = line.substring(10)
								.trim()
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
							if (parameter.is("memory")) {
								for (String user : Context.singleton().getUsers()) {
									if (addresses[0].equals(user)) {
										boxList.add(new MailBox(address, Context.singleton().getMailList(user)));
										break;
									}
								}

							} else {
								for (File box : parameter.getFile("dir").listFiles()) {
									if (box.isDirectory()) {
										if (addresses[0].equals(box.getName())) {
											if (mailFromCheck(box, logStream)) {
												boxList.add(new MailBox(address, box));
											}
											break;
										}
									}
								}
							}
							if (boxList.size() > 0) {
								SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
								bRcptTo = true;
							} else {
								// エラーユーザー存在しない
								SmtpUtil.recieveLine(ps, "550 Failure reply");
							}
						} else if (bAuth) {
							//認証済みなので転送OKする

							SmtpUtil.recieveLine(ps, Constants.RECV_250_OK);
							bTransfer = true;
							transferList.add(address);
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
						if (bTransfer) {
							mail = new MemoryMail();
						} else if (parameter.is("memory")) {
							mail = new MemoryMail();
						} else {
							mail = new FileMail(
									new File(new File(parameter.getFile("dir"), "@rcpt"), helo.replaceAll(":", "_")
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
			File box = new File(parameter.getFile("dir"), user);
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
		return System.currentTimeMillis();
	}

	public static String[] lookupMailHosts(String domainName) throws NamingException {

		InitialDirContext idc = new InitialDirContext();
		Attributes attributes = idc.getAttributes("dns:/" + domainName, new String[] { "MX" });
		Attribute attributeMX = attributes.get("MX");

		if (attributeMX == null) {
			return (new String[] { domainName });
		}

		String[][] pvhn = new String[attributeMX.size()][2];
		for (int i = 0; i < attributeMX.size(); i++) {
			pvhn[i] = ("" + attributeMX.get(i)).split("\\s+");
		}

		Arrays.sort(pvhn, new Comparator<String[]>() {
			public int compare(String[] o1, String[] o2) {
				return (Integer.parseInt(o1[0]) - Integer.parseInt(o2[0]));
			}
		});

		String[] sortedHostNames = new String[pvhn.length];
		for (int i = 0; i < pvhn.length; i++) {
			if (pvhn[i][1].endsWith(".")) {
				sortedHostNames[i] = pvhn[i][1].substring(0, pvhn[i][1].length() - 1);
			} else {
				sortedHostNames[i] = pvhn[i][1];
			}
		}
		return sortedHostNames;
	}

	/**
	 * MAIL FROM で制御する
	 * @param box
	 * @param logStream
	 * @return
	 */
	private boolean mailFromCheck(File box, PrintStream logStream) {
		boolean add = true;
		File mailFromFile = new File(box, Constants.IGNORE_FILE_NAME);
		if (mailFromFile.exists() && mailFromFile.isFile()) {
			Properties prop = new Properties();
			try (FileInputStream fis = new FileInputStream(mailFromFile)) {
				prop.load(fis);
				String all = prop.getProperty("*");
				if (all != null) {
					add = Boolean.getBoolean(all);
				}
				String ignore = prop.getProperty(mailFrom);
				if (ignore != null) {
					add = Boolean.getBoolean(ignore);
				}
			} catch (Exception e) {
				e.printStackTrace(logStream);
			}
			prop.clear();
			if (!add) {
				File mailFromResultFile = new File(box, Constants.IGNORE_RESULT_FILE_NAME);
				int cnt = 1;
				try {
					mailFromResultFile.createNewFile();
					try (FileInputStream fis = new FileInputStream(mailFromResultFile)) {
						prop.load(fis);
					}
					String ignore = prop.getProperty(mailFrom);
					if (ignore != null) {
						cnt = Integer.parseInt(ignore);
						cnt++;
					}
					prop.setProperty(mailFrom, String.valueOf(cnt));
					try (FileOutputStream fos = new FileOutputStream(mailFromResultFile)) {
						prop.store(fos, "");
					}
				} catch (IOException e) {
					e.printStackTrace(logStream);
				} catch (Exception e) {
					e.printStackTrace(logStream);
				}
			}
		}
		return add;
	}
}
