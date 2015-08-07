/**
 * (c) 2015 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;

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

	@Override
	public OutputStreamWriter getWriter() throws Exception {
		return new OutputStreamWriter(baos);
	}

	@Override
	public void delete() {

	}

	@Override
	public Map<String, String> getMap() {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public void copy(List<MailBox> boxList) {
		for (MailBox mailBox : boxList) {
			synchronized (mailBox.getMailList()) {
				mailBox.getMailList().add(this);
			}
		}
	}

	@Override
	public String getTitle() {
		String mail = new String(baos.toByteArray());
		return mail;
	}
}
