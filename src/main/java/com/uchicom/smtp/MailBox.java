// (c) 2015 uchicom
package com.uchicom.smtp;

import java.io.File;
import java.util.List;

/**
 * メールボックスクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class MailBox {

	/** メールアドレス */
	private String mailAddress;
	/** ディレクトリ */
	private File dir;
	/** メールリスト */
	private List<Mail> mailList;

	public MailBox(String mailAddress, File dir) {
		this.mailAddress = mailAddress;
		this.dir = dir;

		if (!dir.exists()) {
			dir.mkdirs();
		}
	}

	public MailBox(String mailAddress, List<Mail> mailList) {
		this.mailAddress = mailAddress;
		this.mailList = mailList;
	}

	public File getDir() {
		return dir;
	}

	public List<Mail> getMailList() {
		return mailList;
	}

	public String getMailAddress() {
		return mailAddress;
	}

}
