// (C) 2023 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.uchicom.smtp.dto.WebhookDto;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

public class MailBoxTest extends MockTest {
  @Captor ArgumentCaptor<File> dirCaptor;

  @Test
  public void build() throws Exception {
    var builder = spy(MailBox.builder());
    var dir = mock(File.class);
    doReturn(true).when(dir).exists();
    var webhook = new WebhookDto();
    doReturn(webhook).when(builder).getWebhook(dirCaptor.capture());

    var mailbox = builder.mailAddress("address").dir(dir).build();

    assertThat(dirCaptor.getAllValues()).hasSize(0);
    assertThat(mailbox.getMailAddress()).isEqualTo("address");
    assertThat(mailbox.getDir()).isEqualTo(dir);
  }

  @Test
  public void build_webhook() throws Exception {
    var builder = spy(MailBox.builder());
    var dir = mock(File.class);
    doReturn(true).when(dir).exists();
    var webhook = new WebhookDto();
    doReturn(webhook).when(builder).getWebhook(dirCaptor.capture());

    var mailbox = builder.mailAddress("address").dir(dir).webhook(true).build();

    assertThat(dirCaptor.getValue()).isEqualTo(dir);
    assertThat(mailbox.getMailAddress()).isEqualTo("address");
    assertThat(mailbox.getDir()).isEqualTo(dir);
  }
}
