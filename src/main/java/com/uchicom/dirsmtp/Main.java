/**
 * (c) 2015 uchicom
 */
package com.uchicom.dirsmtp;

/**
 * 起動クラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class Main {

	/**
	 * アドレスとメールユーザーフォルダの格納フォルダを指定する.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		SmtpParameter parameter = new SmtpParameter(args);
		if (parameter.init(System.err)) {
			parameter.createServer().execute();
		}
	}

}
