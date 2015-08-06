/**
 * (c) 2015 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.File;
import java.util.List;

/**
 * メールボックスクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class MailBox {

	private File dir;
	private List<Mail> mailList;

	public MailBox(File dir) {
		this.dir = dir;

		if (!dir.exists()) {
			dir.mkdirs();
		}
	}

	public MailBox(List<Mail> mailList) {
		this.mailList = mailList;
	}

	public File getDir() {
		return dir;
	}

	public List<Mail> getMailList() {
		return mailList;
	}

}
