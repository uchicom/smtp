// (C) 2022 uchicom
package com.uchicom.smtp.service;

import com.uchicom.smtp.dto.DetectionDto;
import com.uchicom.smtp.dto.ParameterDto;
import com.uchicom.smtp.dto.SendDto;
import com.uchicom.smtp.dto.WebhookDto;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Header;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class WebhookService {

  private static final Logger logger = Logger.getLogger(WebhookService.class.getCanonicalName());

  /** HTTP接続タイムアウト */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  /** HTTPリクエストタイムアウト */
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  /** 正規表現マッチの上限時間(ミリ秒) */
  private static final long MATCH_TIMEOUT_MILLIS = 1000;

  private static final HttpClient client =
      HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

  public WebhookService() {}

  /** 正規表現のマッチ時間が上限を超えた場合にスローする. */
  static class MatchTimeoutException extends RuntimeException {
    MatchTimeoutException() {
      super("regex match timeout", null, false, false);
    }
  }

  /** 文字参照のたびに期限を確認し、ReDoSを打ち切るCharSequence. */
  static class DeadlineCharSequence implements CharSequence {
    private final CharSequence delegate;
    private final long deadlineNanos;

    private DeadlineCharSequence(CharSequence delegate, long deadlineNanos) {
      this.delegate = delegate;
      this.deadlineNanos = deadlineNanos;
    }

    static DeadlineCharSequence of(CharSequence delegate, long timeoutMillis) {
      return new DeadlineCharSequence(delegate, System.nanoTime() + timeoutMillis * 1_000_000L);
    }

    @Override
    public char charAt(int index) {
      if (System.nanoTime() - deadlineNanos > 0) {
        throw new MatchTimeoutException();
      }
      return delegate.charAt(index);
    }

    @Override
    public int length() {
      return delegate.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return new DeadlineCharSequence(delegate.subSequence(start, end), deadlineNanos);
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }

  public void webhook(File file, WebhookDto webhook)
      throws MessagingException, IOException, InterruptedException {

    if (webhook.detection == null) {
      return;
    }
    MimeMessage message = createMimeMessage(file);
    String subject = nullToEmpty(message.getSubject());

    LABEL:
    for (DetectionDto detection : webhook.detection) {
      if (detection.subject != null && !subject.contains(detection.subject)) {
        continue;
      }
      if (detection.from != null) {
        Address[] froms = nullToEmpty(message.getFrom());
        if (!Stream.of(froms).anyMatch(from -> from.toString().contains(detection.from))) {
          continue;
        }
      }
      if (detection.to != null) {
        Address[] tos = nullToEmpty(message.getRecipients(RecipientType.TO));
        if (!Stream.of(tos).anyMatch(to -> to.toString().contains(detection.to))) {
          continue;
        }
      }
      if (detection.cc != null) {
        Address[] ccs = nullToEmpty(message.getRecipients(RecipientType.CC));
        if (!Stream.of(ccs).anyMatch(cc -> cc.toString().contains(detection.cc))) {
          continue;
        }
      }
      if (detection.bcc != null) {
        Address[] bccs = nullToEmpty(message.getRecipients(RecipientType.BCC));
        if (!Stream.of(bccs).anyMatch(bcc -> bcc.toString().contains(detection.bcc))) {
          continue;
        }
      }
      if (detection.header != null && !detection.header.isEmpty()) {
        for (Entry<String, String> entry : detection.header.entrySet()) {

          String[] headerValues = message.getHeader(entry.getKey());
          if (headerValues == null || entry.getValue() == null) continue LABEL;
          boolean headerExist = false;
          for (String headerValue : headerValues) {
            if (headerValue == null || !headerValue.contains(entry.getValue())) continue;
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
        if (multipart(mimeMultipart, detection, message.getAllHeaders(), subject, webhook.send)) {
          return;
        }
        continue;
      }
      if (detection.content != null) {
        if (isMultipart(message.getContent())) continue;
        if (!message.getContent().toString().contains(detection.content)) continue;
      }
      send(webhook.send, message.getAllHeaders(), subject, message.getContent().toString());
      return;
    }
  }

  MimeMessage createMimeMessage(File file)
      throws MessagingException, FileNotFoundException, IOException {
    try (var fis = new FileInputStream(file)) {
      return new MimeMessage(null, fis);
    }
  }

  boolean isMultipart(Object content) {
    return content instanceof MimeMultipart;
  }

  MimeMultipart getMultipart(Object content) {
    return isMultipart(content) ? (MimeMultipart) content : null;
  }

  boolean multipart(
      MimeMultipart mimeMultipart,
      DetectionDto detection,
      Enumeration<Header> headers,
      String subject,
      SendDto send)
      throws IOException, MessagingException, InterruptedException {
    for (int i = 0; i < mimeMultipart.getCount(); i++) {
      BodyPart body = mimeMultipart.getBodyPart(i);
      MimeMultipart bodyMimeMultipart = getMultipart(body.getContent());
      if (bodyMimeMultipart != null
          && multipart(bodyMimeMultipart, detection, headers, subject, send)) {
        return true;
      }
      if (detection.multipart.contentType != null
          && (body.getContentType() == null
              || !body.getContentType().contains(detection.multipart.contentType))) {
        continue;
      }
      if (detection.multipart.body != null
          && !body.getContent().toString().contains(detection.multipart.body)) {
        continue;
      }
      send(send, headers, subject, body.getContent().toString());
      return true;
    }
    return false;
  }

  /**
   * Webhookを送信する.
   *
   * <p>メールファイルは配送後に削除されるため、リクエストの組み立てまでは呼び出しスレッドで行い、HTTP送信のみ非同期で実行する。
   */
  void send(SendDto send, Enumeration<Header> headers, String subject, String content)
      throws IOException, InterruptedException {
    if (send == null || send.body == null || send.body.template == null || send.url == null) {
      return;
    }
    String subjectValue = nullToEmpty(subject);
    String contentValue = nullToEmpty(content);
    Map<String, String> parameterMap = new HashMap<>();
    List<Header> headerList = new ArrayList<>();
    if (headers != null) {
      headers
          .asIterator()
          .forEachRemaining(
              header -> {
                parameterMap.put("${" + header.getName() + "}", nullToEmpty(header.getValue()));
                headerList.add(header);
              });
    }
    parameterMap.put("${subject}", subjectValue);
    parameterMap.put("${content}", contentValue);
    if (send.body.parameter != null) {
      for (Entry<String, ParameterDto> entry : send.body.parameter.entrySet()) {
        ParameterDto parameter = entry.getValue();
        if (parameter == null || parameter.target == null || parameter.pattern == null) continue;
        switch (parameter.target) {
          case "subject" ->
              parameterMap.putAll(match(entry.getKey(), parameter.pattern, subjectValue));
          case "content" ->
              parameterMap.putAll(match(entry.getKey(), parameter.pattern, contentValue));
          default ->
              headerList.forEach(
                  header -> {
                    if (!parameter.target.equals(header.getName())) return;
                    parameterMap.putAll(
                        match(entry.getKey(), parameter.pattern, nullToEmpty(header.getValue())));
                  });
        }
      }
    }
    String template = send.body.template;
    for (Entry<String, String> entry : parameterMap.entrySet()) {
      template = template.replace(entry.getKey(), escapeJson(entry.getValue()));
    }

    var builder = HttpRequest.newBuilder().uri(URI.create(send.url)).timeout(REQUEST_TIMEOUT);
    if (send.header != null) {
      for (var entry : send.header.entrySet()) {
        builder.header(entry.getKey(), entry.getValue());
      }
    }

    HttpRequest request = builder.POST(BodyPublishers.ofString(template)).build();
    client
        .sendAsync(request, BodyHandlers.ofString())
        .whenComplete(
            (response, throwable) -> {
              if (throwable != null) {
                logger.log(Level.WARNING, "webhook failed", throwable);
                return;
              }
              logger.info("webhook.response.status:" + response.statusCode());
            });
  }

  static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  static Address[] nullToEmpty(Address[] value) {
    return value == null ? new Address[0] : value;
  }

  /** JSON文字列リテラルの内側として安全になるようエスケープする. */
  static String escapeJson(String value) {
    var sb = new StringBuilder(value.length() + 16);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }

  public Map<String, String> match(String key, String pattern, String target) {
    Map<String, String> map = new HashMap<>();
    try {
      Matcher matcher =
          Pattern.compile(pattern)
              .matcher(DeadlineCharSequence.of(nullToEmpty(target), MATCH_TIMEOUT_MILLIS));
      if (matcher.find()) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
          map.put("${" + key + ":" + i + "}", nullToEmpty(matcher.group(i)));
        }
      }
    } catch (MatchTimeoutException e) {
      logger.warning("webhook regex timeout:" + key);
    } catch (PatternSyntaxException e) {
      logger.warning("webhook regex invalid:" + key);
    }
    return map;
  }
}
