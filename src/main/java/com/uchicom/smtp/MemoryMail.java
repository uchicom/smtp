// (c) 2015 uchicom
package com.uchicom.smtp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

/**
 * メモリ形式のメールクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class MemoryMail implements Mail {

	private ByteArrayOutputStream baos = new ByteArrayOutputStream();

	public MemoryMail() {
	}

	public MemoryMail(String mailAddress, String senderHostName, String localHostName) {

	}

	@Override
	public Writer getWriter() throws Exception {
		return new OutputStreamWriter(baos);
	}

	@Override
	public void delete() {

	}

	@Override
	public void copy(List<MailBox> boxList, String senderHostName, String localHostName) {
		for (MailBox mailBox : boxList) {
			synchronized (mailBox.getMailList()) {
				MemoryMail mail = new MemoryMail(mailBox.getMailAddress(), senderHostName, localHostName);
				try {
					mail.baos.write(this.baos.toByteArray());
				} catch (IOException e) {
					// TODO 自動生成された catch ブロック
					e.printStackTrace();
				}
				mailBox.getMailList().add(mail);
			}
		}
	}

	@Override
	public String getData() {
		String mail = new String(baos.toByteArray());
		return mail;
	}
}
