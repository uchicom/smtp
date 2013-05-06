/**
 * (c) 2012 uchicom
 */
package com.uchicom.smtp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Uchiyama Shigeki
 *
 */
public class SingleSmtpServer {
	public static SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss.SSS");

    protected static Queue<ServerSocket> serverQueue = new ConcurrentLinkedQueue<ServerSocket>();
    
	protected String hostName;
	protected File file;
    
	/**
	 * アドレスとメールユーザーフォルダの格納フォルダを指定する
	 * @param args
	 */
	public static void main (String[] args) {
		if (args.length != 3) {
			System.out.println("args.length != 2");
			return;
		}
		//メールフォルダ格納フォルダ
		File dir = new File(args[0]);
		if (!dir.exists() || !dir.isDirectory()) {
			System.out.println("mailbox directory is not found.");
			return;
		}
		//メール
		String hostName = args[1];

        // ポート
        int port = 8025;
        if (args.length > 2) {
            port = Integer.parseInt(args[2]);
        } 
        // 接続待ち数
        int back = 10;
        if (args.length == 3) {
            back = Integer.parseInt(args[3]);
        }
		execute(hostName, dir, port, back);
	}
	
	/**
	 * 
	 * @param hostName
	 * @param file
	 * @param port
	 * @param back
	 */
	private static void execute(String hostName, File file, int port, int back) {
	    ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port), back);
            SingleSmtpServer smtpServer = new SingleSmtpServer(hostName, file);
            serverQueue.add(server);
            while (true) {
                Socket socket = server.accept();
                smtpServer.smtp(socket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (server != null) {
                try {
                    server.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    server = null;
                }
            }
        }
	}
	

    public SingleSmtpServer(String hostName, File file) {
        this.hostName = hostName;
        this.file = file;
    }
	
	public void smtp(Socket socket) {
		System.out.println(System.currentTimeMillis() + ":" + String.valueOf( socket.getRemoteSocketAddress()));
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			PrintStream ps = new PrintStream(socket.getOutputStream());
			OutputStreamWriter writer = null;
			//1接続に対する受付開始
			ps.print("220 " + hostName + " SMTP\r\n");
			ps.flush();
			String head = br.readLine();
			boolean bHelo = false;
			boolean bMailFrom = false;
			boolean bRcptTo = false;
			boolean bData = false;
			String helo = null;
			String mailFrom = null;
			File userBox = null;
			//HELO
			//MAIL FROM:
			//RCPT TO:
			//DATA
			//MAIL FROM:
			//RCPT TO:
			//DATAの順で処理する
			while (head != null) {
				System.out.println("[" + head + "]");
				if (bData) { 
					if (".".equals(head)) {
						//メッセージ終了
						writer.write(".\r\n");
						writer.close();
						ps.print("250 OK \r\n");
						bMailFrom = false;
						bRcptTo = false;
						bData = false;
					} else {
						//メッセージ本文
						writer.write(head);
						writer.write("\r\n");
						writer.flush();
					}
				} else if (head.startsWith("HELO")) {
					bHelo = true;
					helo = head.substring(4).trim();
					ps.print(SmtpStatic.RECV_250);
					ps.print(' ');
					ps.print(hostName + " Hello " + socket.getInetAddress().getHostAddress() + "\r\n");
					ps.flush();
				} else if (head.matches(SmtpStatic.REG_EXP_MAIL_FROM)) {
					if (bHelo) {
						mailFrom = head.substring(10).trim().replaceAll("[<>]", "");
						System.out.println(mailFrom);
						ps.print(SmtpStatic.RECV_250_OK);
						ps.flush();
						bMailFrom = true;
					} else {
						//エラー500
						ps.print("451 ERROR\r\n");
						ps.flush();
					}
				} else if (head.matches(SmtpStatic.REG_EXP_RCPT_TO)) {
					if (bMailFrom) {
						String[] heads = head.split(":");
						String address = heads[1].trim().replaceAll("[<>]", "");
						String[] addresses = address.split("@");
						System.out.println(addresses[0]);
						System.out.println(addresses[1]);
						if (addresses[1].equals(hostName)) {
							//宛先チェック
							boolean checkOK = false;
							for (File box : file.listFiles()) {
								if (box.isDirectory()) {
									if (addresses[0].equals(box.getName())) {
										userBox = box;
										checkOK = true;
									}
								}
							}
							if (checkOK) {
								ps.print(SmtpStatic.RECV_250_OK);
								ps.flush();
								bRcptTo = true;
							} else {
								//エラーユーザー存在しない
								ps.print("500\r\n");
								ps.flush();
							}
						} else {
							//エラーホストが違う
							ps.print("500\r\n");
							ps.flush();
						}
					} else {
						//エラー500
						ps.print("500\r\n");
						ps.flush();
					}
				} else if (head.matches(SmtpStatic.REG_EXP_DATA)) {
					if (bRcptTo) {
						File mailFile = new File(userBox, helo + "_" + mailFrom + "~" + socket.getInetAddress().getHostAddress() + "_" + format.format(new Date(System.currentTimeMillis())));
						mailFile.createNewFile();
						writer = new OutputStreamWriter(new FileOutputStream(mailFile));
						ps.print(SmtpStatic.RECV_354);
						ps.flush();
						bData = true;
					} else {
						//エラー
						ps.print("500\r\n");
						ps.flush();
					}
				
				} else if (head.matches(SmtpStatic.REG_EXP_QUIT)) {
					ps.print("221\r\n");
					ps.flush();
					break;
				} else if ("".equals(head)) {
					//何もしない
				} else {
					ps.print("500 command not found\r\n");
					ps.flush();
				}
				head = br.readLine();
			}
			ps.close();
			br.close();
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
		}
	}

	public static void shutdown(String[] args) {
        if (!serverQueue.isEmpty()) {
            try {
                serverQueue.poll().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
	}
}
