// (C) 2022 uchicom
package com.uchicom.smtp.service;

import com.uchicom.smtp.dto.DetectionDto;
import com.uchicom.smtp.dto.ParameterDto;
import com.uchicom.smtp.dto.SendDto;
import com.uchicom.smtp.dto.WebhookDto;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class WebhookService {

  private static final Logger logger = Logger.getLogger(WebhookService.class.getCanonicalName());

  public WebhookService() {}

  public void webhook(File file, WebhookDto webhook)
      throws MessagingException, IOException, InterruptedException {

    MimeMessage message = new MimeMessage(null, new FileInputStream(file));

    LABEL:
    for (DetectionDto detection : webhook.detection) {
      if (detection.subject != null && !message.getSubject().contains(detection.subject)) {
        continue;
      }
      if (detection.from != null) {
        Address[] froms = message.getFrom();
        if (!Stream.of(froms).anyMatch(from -> from.toString().contains(detection.from))) {
          continue;
        }
      }
      if (detection.to != null) {
        Address[] tos = message.getRecipients(RecipientType.TO);
        if (!Stream.of(tos).anyMatch(to -> to.toString().contains(detection.to))) {
          continue;
        }
      }
      if (detection.cc != null) {
        Address[] ccs = message.getRecipients(RecipientType.CC);
        if (!Stream.of(ccs).anyMatch(cc -> cc.toString().contains(detection.cc))) {
          continue;
        }
      }
      if (detection.bcc != null) {
        Address[] bccs = message.getRecipients(RecipientType.BCC);
        if (!Stream.of(bccs).anyMatch(bcc -> bcc.toString().contains(detection.bcc))) {
          continue;
        }
      }
      if (detection.header != null && !detection.header.isEmpty()) {
        for (Entry<String, String> entry : detection.header.entrySet()) {

          String[] headerValues = message.getHeader(entry.getKey());
          if (headerValues == null) continue LABEL;
          boolean headerExist = false;
          for (String headerValue : headerValues) {
            if (!headerValue.contains(entry.getValue())) continue;
            headerExist = true;
            break;
          }
          if (!headerExist) continue LABEL;
        }
      }
      if (detection.multipart != null) {
        // マルチパートチェック
        MimeMultipart mimeMultipart = getMultipart(message.getContent());
        if (mimeMultipart == null) continue;
        // マルチパート検出送信したかチェック
        if (multipart(mimeMultipart, detection, message.getSubject(), webhook.send)) {
          return;
        }
        continue;
      }
      if (detection.content != null) {
        if (isMultipart(message.getContent())) continue;
        if (!message.getContent().toString().contains(detection.content)) continue;
      }
      send(webhook.send, message.getSubject(), message.getContent().toString());
      return;
    }
  }

  boolean isMultipart(Object content) {
    return content instanceof MimeMultipart;
  }

  MimeMultipart getMultipart(Object content) {
    return isMultipart(content) ? (MimeMultipart) content : null;
  }

  boolean multipart(
      MimeMultipart mimeMultipart, DetectionDto detection, String subject, SendDto send)
      throws IOException, MessagingException, InterruptedException {
    for (int i = 0; i < mimeMultipart.getCount(); i++) {
      BodyPart body = mimeMultipart.getBodyPart(i);
      MimeMultipart bodyMimeMultipart = getMultipart(body.getContent());
      if (bodyMimeMultipart != null && multipart(bodyMimeMultipart, detection, subject, send)) {
        return true;
      }
      if (detection.multipart.contentType != null
          && !body.getContentType().contains(detection.multipart.contentType)) {
        continue;
      }
      if (detection.multipart.body != null
          && !body.getContent().toString().contains(detection.multipart.body)) {
        continue;
      }
      send(send, subject, body.getContent().toString());
      return true;
    }
    return false;
  }

  void send(SendDto send, String subject, String content) throws IOException, InterruptedException {
    if (send == null) return;
    Map<String, String> parameterMap = new HashMap<>();
    parameterMap.put("${subject}", subject);
    parameterMap.put("${content}", content);
    for (Entry<String, ParameterDto> entry : send.body.parameter.entrySet()) {
      switch (entry.getValue().target) {
        case "subject":
          parameterMap.putAll(match(entry.getKey(), entry.getValue().pattern, subject));
          break;
        case "content":
          parameterMap.putAll(match(entry.getKey(), entry.getValue().pattern, content));
          break;
      }
    }
    String template = send.body.template;
    for (Entry<String, String> entry : parameterMap.entrySet()) {
      template = template.replace(entry.getKey(), entry.getValue());
    }
    Builder builder = HttpRequest.newBuilder().uri(URI.create(send.url));
    for (Entry<String, String> entry : send.header.entrySet()) {
      builder.header(entry.getKey(), entry.getValue());
    }

    HttpRequest request = builder.POST(BodyPublishers.ofString(template)).build();
    HttpClient client = HttpClient.newHttpClient();
    var response = client.send(request, BodyHandlers.ofString());
    logger.info("webhook.status:" + response.statusCode());
  }

  public Map<String, String> match(String key, String pattern, String target) {
    Map<String, String> map = new HashMap<>();
    Matcher matcher = Pattern.compile(pattern).matcher(target);
    if (matcher.find()) {
      for (int i = 1; i <= matcher.groupCount(); i++) {
        map.put("${" + key + ":" + i + "}", matcher.group(i));
      }
    }
    return map;
  }
}
