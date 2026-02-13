// (C) 2015 uchicom
package com.uchicom.smtp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.mail.MessagingException;

/**
 * ファイル形式のメールクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class FileMail implements Mail {

  /** メールファイル */
  private File file;

  /**
   * 引数指定のコンストラクタ.
   *
   * @param file 出力ファイル
   * @throws IOException IOエラー
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
  public Writer getWriter() throws Exception {
    return new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
  }

  @Override
  public void delete() {
    file.delete();
  }

  public File getFile() {
    return file;
  }

  @Override
  public void copy(Set<MailBox> boxSet, String localHostName, String senderHostName) {
    for (MailBox mailBox : boxSet) {
      try {
        SmtpUtil.copyFile(
            file,
            new File(mailBox.getDir(), file.getName()),
            mailBox.getMailAddress(),
            senderHostName,
            localHostName);
        if (mailBox.hasWebhook()) {
          mailBox.webhook(file);
        }
      } catch (MessagingException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public String getData() {
    // TODO 自動生成されたメソッド・スタブ
    return null;
  }
}
