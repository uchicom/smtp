// (c) 2017 uchicom
package com.uchicom.smtp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.logging.Logger;

import javax.naming.NamingException;

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

	public boolean send(String from, String to, String data) throws NamingException {

		logger.info("transfer:" + to);
		String[] addresses = to.split("@");
		String[] hosts = SmtpProcess.lookupMailHosts(addresses[1]);
		boolean sent = false;
		for (String host : hosts) {
			try (Socket socket = new Socket(host, 25);
					BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					BufferedWriter writer2 = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));) {
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
				logger.info("t[" + reader.readLine() + "]");
				writer2.write("RCPT TO: <" + to + ">\r\n");// RCPT TO:
				writer2.flush();
				logger.info("t[" + reader.readLine() + "]");
				writer2.write("DATA\r\n");// DATA
				writer2.flush();
				logger.info("t[" + reader.readLine() + "]");
				writer2.write(data);
				writer2.flush();
				writer2.write(".\r\n");
				writer2.flush();
				logger.info("t[" + reader.readLine() + "]");
				writer2.write("QUIT\r\n");
				writer2.flush();
				logger.info("t[" + reader.readLine() + "]");
				sent = true;
				break;
			} catch (Exception e) {
				logger.warning(e.getMessage());
			}
			logger.info("mx!:" + host);
		}
		return sent;
	}
}
