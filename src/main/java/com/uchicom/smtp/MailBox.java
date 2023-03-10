// (C) 2015 uchicom
package com.uchicom.smtp;

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
  /** Webhook設定 */
  private WebhookDto webhook;

  private final WebhookService webhookService = new WebhookService();

  MailBox(String mailAddress, File dir) {
    this.mailAddress = mailAddress;
    this.dir = dir;
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

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    /** メールアドレス */
    String mailAddress;
    /** ディレクトリ */
    File dir;

    boolean webhook;

    public Builder() {}

    public Builder mailAddress(String mailAddress) {
      this.mailAddress = mailAddress;
      return this;
    }

    public Builder dir(File dir) {
      this.dir = dir;
      return this;
    }

    public Builder webhook(boolean webhook) {
      this.webhook = webhook;
      return this;
    }

    public MailBox build() throws IOException {
      var mailBox = new MailBox(mailAddress, dir);
      if (dir.exists()) {
        if (webhook) {
          mailBox.webhook = getWebhook(dir);
        }
      } else {
        dir.mkdirs();
      }
      return mailBox;
    }

    WebhookDto getWebhook(File dir) throws IOException {
      var webhookFile = new File(dir, Constants.WEBHOOK_FILE_NAME);
      if (!webhookFile.exists() || !webhookFile.isFile()) {
        return null;
      }
      return new Yaml()
          .loadAs(
              new String(Files.readAllBytes(webhookFile.toPath()), StandardCharsets.UTF_8),
              WebhookDto.class);
    }
  }
}
