// (C) 2023 uchicom
package com.uchicom.smtp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.uchicom.smtp.MockTest;
import com.uchicom.smtp.dto.DetectionDto;
import com.uchicom.smtp.dto.SendDto;
import com.uchicom.smtp.dto.WebhookDto;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import javax.mail.Header;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

public class WebhooServiceTest extends MockTest {

  @Captor ArgumentCaptor<Enumeration<Header>> headersCaptor;
  @Captor ArgumentCaptor<String> subjectCaptor;
  @Captor ArgumentCaptor<String> contentCaptor;
  @Captor ArgumentCaptor<SendDto> sendCaptor;

  @Test
  public void webhook_detectionNull() throws Exception {
    var service = spy(new WebhookService());
    doNothing()
        .when(service)
        .send(
            sendCaptor.capture(),
            headersCaptor.capture(),
            subjectCaptor.capture(),
            contentCaptor.capture());
    var file = mock(File.class);
    var mimeMessage = mock(MimeMessage.class);
    doReturn(mimeMessage).when(service).createMimeMessage(file);
    doReturn("subject").when(mimeMessage).getSubject();
    doReturn("content").when(mimeMessage).getContent();
    var webhook = new WebhookDto();
    service.webhook(file, webhook);

    assertThat(sendCaptor.getAllValues()).isEmpty();
    assertThat(subjectCaptor.getAllValues()).isEmpty();
    assertThat(contentCaptor.getAllValues()).isEmpty();
  }

  @Test
  public void webhook() throws Exception {
    var service = spy(new WebhookService());
    doNothing()
        .when(service)
        .send(
            sendCaptor.capture(),
            headersCaptor.capture(),
            subjectCaptor.capture(),
            contentCaptor.capture());
    var file = mock(File.class);
    var mimeMessage = mock(MimeMessage.class);
    doReturn(mimeMessage).when(service).createMimeMessage(file);
    doReturn("subject").when(mimeMessage).getSubject();
    doReturn("content").when(mimeMessage).getContent();
    var webhook = new WebhookDto();
    webhook.detection = new ArrayList<>();
    var detection = new DetectionDto();
    webhook.detection.add(detection);
    service.webhook(file, webhook);

    assertThat(sendCaptor.getValue()).isNull();
    assertThat(subjectCaptor.getValue()).isEqualTo("subject");
    assertThat(contentCaptor.getValue()).isEqualTo("content");
  }

  @Test
  public void webhook_subjectMatch() throws Exception {
    var service = spy(new WebhookService());
    doNothing()
        .when(service)
        .send(
            sendCaptor.capture(),
            headersCaptor.capture(),
            subjectCaptor.capture(),
            contentCaptor.capture());
    var file = mock(File.class);
    var mimeMessage = mock(MimeMessage.class);
    doReturn(mimeMessage).when(service).createMimeMessage(file);
    doReturn("subject").when(mimeMessage).getSubject();
    doReturn("content").when(mimeMessage).getContent();
    var webhook = new WebhookDto();
    webhook.detection = new ArrayList<>();
    var detection = new DetectionDto();
    detection.subject = "subject";
    webhook.detection.add(detection);
    service.webhook(file, webhook);

    assertThat(sendCaptor.getValue()).isNull();
    assertThat(subjectCaptor.getValue()).isEqualTo("subject");
    assertThat(contentCaptor.getValue()).isEqualTo("content");
  }

  @Test
  public void webhook_subjectNotMatch() throws Exception {
    var service = spy(new WebhookService());
    doNothing()
        .when(service)
        .send(
            sendCaptor.capture(),
            headersCaptor.capture(),
            subjectCaptor.capture(),
            contentCaptor.capture());
    var file = mock(File.class);
    var mimeMessage = mock(MimeMessage.class);
    doReturn(mimeMessage).when(service).createMimeMessage(file);
    doReturn("subject").when(mimeMessage).getSubject();
    doReturn("content").when(mimeMessage).getContent();
    var webhook = new WebhookDto();
    webhook.detection = new ArrayList<>();
    var detection = new DetectionDto();
    detection.subject = "subject2";
    webhook.detection.add(detection);
    service.webhook(file, webhook);

    assertThat(sendCaptor.getAllValues()).isEmpty();
    assertThat(subjectCaptor.getAllValues()).isEmpty();
    assertThat(contentCaptor.getAllValues()).isEmpty();
  }

  @Test
  public void webhook_contentMatch() throws Exception {
    var service = spy(new WebhookService());
    doNothing()
        .when(service)
        .send(
            sendCaptor.capture(),
            headersCaptor.capture(),
            subjectCaptor.capture(),
            contentCaptor.capture());
    var file = mock(File.class);
    var mimeMessage = mock(MimeMessage.class);
    doReturn(mimeMessage).when(service).createMimeMessage(file);
    doReturn("subject").when(mimeMessage).getSubject();
    doReturn("content").when(mimeMessage).getContent();
    var webhook = new WebhookDto();
    webhook.detection = new ArrayList<>();
    var detection = new DetectionDto();
    detection.content = "content";
    webhook.detection.add(detection);
    service.webhook(file, webhook);

    assertThat(sendCaptor.getValue()).isNull();
    assertThat(subjectCaptor.getValue()).isEqualTo("subject");
    assertThat(contentCaptor.getValue()).isEqualTo("content");
  }

  @Test
  public void webhook_contentNotMatch() throws Exception {
    var service = spy(new WebhookService());
    doNothing()
        .when(service)
        .send(
            sendCaptor.capture(),
            headersCaptor.capture(),
            subjectCaptor.capture(),
            contentCaptor.capture());
    var file = mock(File.class);
    var mimeMessage = mock(MimeMessage.class);
    doReturn(mimeMessage).when(service).createMimeMessage(file);
    doReturn("subject").when(mimeMessage).getSubject();
    doReturn("content").when(mimeMessage).getContent();
    var webhook = new WebhookDto();
    webhook.detection = new ArrayList<>();
    var detection = new DetectionDto();
    detection.content = "content2";
    webhook.detection.add(detection);
    service.webhook(file, webhook);

    assertThat(sendCaptor.getAllValues()).isEmpty();
    assertThat(subjectCaptor.getAllValues()).isEmpty();
    assertThat(contentCaptor.getAllValues()).isEmpty();
  }

  @Test
  public void webhook_subjectNull() throws Exception {
    var service = spy(new WebhookService());
    doNothing()
        .when(service)
        .send(
            sendCaptor.capture(),
            headersCaptor.capture(),
            subjectCaptor.capture(),
            contentCaptor.capture());
    var file = mock(File.class);
    var mimeMessage = mock(MimeMessage.class);
    doReturn(mimeMessage).when(service).createMimeMessage(file);
    doReturn(null).when(mimeMessage).getSubject();
    doReturn("content").when(mimeMessage).getContent();
    var webhook = new WebhookDto();
    webhook.detection = new ArrayList<>();
    var detection = new DetectionDto();
    detection.subject = "subject";
    webhook.detection.add(detection);
    service.webhook(file, webhook);

    assertThat(sendCaptor.getAllValues()).isEmpty();
  }

  @Test
  public void webhook_subjectNullNoDetectionSubject() throws Exception {
    var service = spy(new WebhookService());
    doNothing()
        .when(service)
        .send(
            sendCaptor.capture(),
            headersCaptor.capture(),
            subjectCaptor.capture(),
            contentCaptor.capture());
    var file = mock(File.class);
    var mimeMessage = mock(MimeMessage.class);
    doReturn(mimeMessage).when(service).createMimeMessage(file);
    doReturn(null).when(mimeMessage).getSubject();
    doReturn("content").when(mimeMessage).getContent();
    var webhook = new WebhookDto();
    webhook.detection = new ArrayList<>();
    webhook.detection.add(new DetectionDto());
    service.webhook(file, webhook);

    assertThat(subjectCaptor.getValue()).isEmpty();
  }

  @Test
  public void webhook_recipientsNull() throws Exception {
    var service = spy(new WebhookService());
    doNothing()
        .when(service)
        .send(
            sendCaptor.capture(),
            headersCaptor.capture(),
            subjectCaptor.capture(),
            contentCaptor.capture());
    var file = mock(File.class);
    var mimeMessage = mock(MimeMessage.class);
    doReturn(mimeMessage).when(service).createMimeMessage(file);
    doReturn("subject").when(mimeMessage).getSubject();
    doReturn(null).when(mimeMessage).getFrom();
    doReturn(null).when(mimeMessage).getRecipients(any());
    doReturn("content").when(mimeMessage).getContent();
    var webhook = new WebhookDto();
    webhook.detection = new ArrayList<>();
    var detection = new DetectionDto();
    detection.from = "a";
    detection.to = "b";
    detection.cc = "c";
    detection.bcc = "d";
    webhook.detection.add(detection);
    service.webhook(file, webhook);

    assertThat(sendCaptor.getAllValues()).isEmpty();
  }

  @Test
  public void escapeJson() throws Exception {
    assertThat(WebhookService.escapeJson("a\"b\\c\nd\u0001")).isEqualTo("a\\\"b\\\\c\\nd\\u0001");
  }

  @Test
  public void send_nullBody() throws Exception {
    var service = new WebhookService();
    var send = new SendDto();
    send.url = "http://localhost:1/";
    // 例外が発生しないこと
    service.send(send, null, null, null);
  }

  @Test
  public void match_redosTimeout() throws Exception {
    var service = new WebhookService();
    var target = "a".repeat(40) + "!";

    var start = System.nanoTime();
    var map = service.match("content", "(a+)+$", target);

    assertThat(map).isEmpty();
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(5000);
  }

  @Test
  public void match_nullTarget() throws Exception {
    var service = new WebhookService();
    assertThat(service.match("content", "(a)", null)).isEmpty();
  }

  @Test
  public void isMultipart() throws Exception {
    // mock
    WebhookService service = new WebhookService();
    assertThat(service.isMultipart(new Object())).isFalse();
    assertThat(service.isMultipart(mock(MimeMultipart.class))).isTrue();
  }

  @Test
  public void getMultipart() throws Exception {
    // mock
    WebhookService service = new WebhookService();
    assertThat(service.getMultipart(new Object())).isNull();
    assertThat(service.getMultipart(mock(MimeMultipart.class))).isInstanceOf(MimeMultipart.class);
  }

  @Test
  public void multipart() throws Exception {}

  @Test
  public void send() throws Exception {}

  @Test
  public void match() throws Exception {
    // mock
    WebhookService service = new WebhookService();
    var key = "content";
    var pattern = ".* ([0-9]+) .*";
    var target = "body 0123 body";

    // test
    var map = service.match(key, pattern, target);

    // assert
    assertThat(map.get("${content:1}")).isEqualTo("0123");
  }
}
