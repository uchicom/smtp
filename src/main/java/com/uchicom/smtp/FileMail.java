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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;

/**
 * ファイル形式のメールクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class FileMail implements Mail {

  private static final Logger logger = Logger.getLogger(FileMail.class.getCanonicalName());

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
        new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.ISO_8859_1));
  }

  @Override
  public void delete() {
    file.delete();
  }

  public File getFile() {
    return file;
  }

  @Override
  public void copy(Set<MailBox> boxSet, String localHostName, String senderHostName)
      throws Exception {
    Exception failure = null;
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
      } catch (MessagingException | IOException | InterruptedException e) {
        logger.log(Level.WARNING, "copy failed:" + mailBox.getMailAddress(), e);
        if (failure == null) {
          failure = e;
        } else {
          failure.addSuppressed(e);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  @Override
  public String getData() {
    return null;
  }
}
