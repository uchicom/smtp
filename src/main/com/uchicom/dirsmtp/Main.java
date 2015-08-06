/**
 * (c) 2015 uchicom
 */
package com.uchicom.dirsmtp;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class Main {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		SmtpParameter param = new SmtpParameter(args);
		if (param.init(System.err)) {
			SingleSmtpServer server = new SingleSmtpServer(param);
			server.execute();
		}

	}

}
