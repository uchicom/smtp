// (C) 2015 uchicom
package com.uchicom.smtp;

import com.uchicom.smtp.dto.ConfigDto;
import com.uchicom.smtp.dto.WebhookDto;
import com.uchicom.smtp.service.WebhookService;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.mail.MessagingException;
import org.yaml.snakeyaml.Yaml;

/**
 * メールボックスクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class MailBox {

  /** メールアドレス */
  private String mailAddress;
  /** ディレクトリ */
  private File dir;
  /** メールリスト */
  private List<Mail> mailList;
  /** 設定 */
  private WebhookDto webhook;

  private final WebhookService webhookService = new WebhookService();

  public MailBox(String mailAddress, File dir) throws IOException {
    this.mailAddress = mailAddress;
    this.dir = dir;
    File configFile = new File(dir, Constants.WEBHOOK_FILE_NAME);
    if (configFile.exists() && configFile.isFile()) {

      webhook =
          new Yaml()
              .loadAs(
                  new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8),
                  ConfigDto.class)
              .webhook;
    }

    if (!dir.exists()) {
      dir.mkdirs();
    }
  }

  public MailBox(String mailAddress, List<Mail> mailList) {
    this.mailAddress = mailAddress;
    this.mailList = mailList;
  }

  public void setDir(File dir) {
    this.dir = dir;
    if (!dir.exists()) {
      dir.mkdirs();
    }
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

  public boolean hasWebhook() {
    return webhook != null;
  }

  public void webhook(File file) throws MessagingException, IOException, InterruptedException {
    webhookService.webhook(file, webhook);
  }
}
