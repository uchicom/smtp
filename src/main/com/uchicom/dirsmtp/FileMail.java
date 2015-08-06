/**
 * (c) 2015 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;

/**
 * ファイル形式のメールクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class FileMail implements Mail {

	/** メールファイル */
	private File file;

	/**
	 * 引数指定のコンストラクタ.
	 *
	 * @param file
	 * @throws IOException
	 */
	public FileMail(File file) throws IOException {
		File parent = file.getParentFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}
		file.createNewFile();
		this.file = file;
	}

	@Override
	public OutputStreamWriter getWriter() throws Exception {
		return new OutputStreamWriter(new FileOutputStream(
				file));
	}

	@Override
	public void delete() {
		file.delete();

	}

	public File getFile() {
		return file;
	}

	@Override
	public Map<String, String> getMap() {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public void copy(List<MailBox> boxList) {
		for (MailBox mailBox : boxList) {
			try {
				SmtpUtil.copyFile(file, new File(mailBox.getDir(),
						file.getName()));
			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
		}

	}

	@Override
	public String getTitle() {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

}
