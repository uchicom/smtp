// (c) 2017 uchicom
package com.uchicom.smtp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class MailSender {

	private static final Logger logger = Logger.getLogger(MailSender.class.getCanonicalName());

	private String host;
	public MailSender(String host) {
		this.host = host;
	}

	public void send(String from, String to, String data, String enc) throws Exception {

		logger.info("transfer:" + to);
		String[] addresses = to.split("@");
		String[] hosts = SmtpProcess.lookupMailHosts(addresses[1]);
		boolean sent = false;
		Exception exception = null;

		for (String host : hosts) {
			if (sent) break;
			exception = null;
			try (Socket socket = new Socket(host, 25);
					BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					OutputStreamWriter writer2 = new OutputStreamWriter(socket.getOutputStream(), enc);) {
				logger.info("t[" + reader.readLine() + "]");
				writer2.write("EHLO " + this.host + "\r\n");//EHLO
				writer2.flush();
				String rec = null;
				do {
					rec = reader.readLine();
					logger.info("t[" + rec + "]");
				}while (rec != null && rec.startsWith("250-"));
				logger.info("mailFrom:" + from);
				writer2.write("MAIL FROM: <" + from + ">\r\n");// MAIL FROM:
				writer2.flush();
				rec = reader.readLine();
				logger.info("t[" + rec + "]");
				if (!rec.startsWith("250")) throw new Exception(from + "が迷惑メールに登録されている可能性があります。メールの設定を確認してください");

				writer2.write("RCPT TO: <" + to + ">\r\n");// RCPT TO:
				writer2.flush();
				rec = reader.readLine();
				logger.info("t[" + rec + "]");
				if (!rec.startsWith("250")) throw new Exception(to + "のアドレスが存在しない可能性があります。メールの設定を確認してください");
				writer2.write("DATA\r\n");// DATA
				writer2.flush();
				rec = reader.readLine();
				logger.info("t[" + rec + "]");
				if (!rec.startsWith("354")) throw new Exception("メールの送信に失敗しました。メールの管理者までご連絡ください");
				writer2.write(data);
				writer2.flush();
				writer2.write(".\r\n");// DATA
				writer2.flush();
				rec = reader.readLine();
				logger.info("t[" + rec + "]");
				if (!rec.startsWith("250")) throw new Exception("メールの送信に失敗しました。申し訳ございませんが、info@uchicom.comまでご連絡ください");
				sent = true;
				writer2.write("QUIT\r\n");
				writer2.flush();
				rec = reader.readLine();
				logger.info("t[" + rec + "]");
				break;
			} catch (Exception e) {
				e.printStackTrace();
				logger.warning(e.getMessage());
				exception = e;
			}
			logger.info("mx!:" + host);
		}
		//送信していない場合はexceptionを送信
		if (!sent) {
			throw exception;
		}
	}
}
