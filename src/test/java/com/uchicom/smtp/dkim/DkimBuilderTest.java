// (C) 2022 uchicom
package com.uchicom.smtp.dkim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.uchicom.smtp.MockTest;
import java.io.ByteArrayInputStream;
import java.security.PrivateKey;
import java.time.LocalDateTime;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Spy;

public class DkimBuilderTest extends MockTest {

  @Captor ArgumentCaptor<String> textCaptor;
  @Captor ArgumentCaptor<PrivateKey> privateKeyCaptor;
  @Spy DkimBuilder builder;

  @Test
  public void build() throws Exception {

    // mock
    doReturn("normalizeBody").when(builder).normalizeBody();
    doReturn("normalizeHeader").when(builder).normalizeHeader();
    doReturn("bh1").when(builder).createBh("normalizeBody");
    doReturn("b2").when(builder).createB(anyString(), anyString());
    doReturn(10000000L).when(builder).getEpocSecond(any());
    // test method
    builder.fromHost("fromHost");
    String result = builder.build();
    // assert
    assertThat(result)
        .isEqualTo(
            "v=1; "
                + "a=rsa-sha256; "
                + "b=b2; "
                + "bh=bh1; "
                + "c=relaxed/relaxed; "
                + "d=fromHost; "
                + "h=subject:from; "
                + "s=fromHost; "
                + "t=10000000");
  }

  @Test
  public void createBh() throws Exception {

    // mock
    // test method
    String result = builder.createBh("abcd");
    // assert
    assertThat(result).isEqualTo("iNQmb9TmM40TuEX88olXnSCciXgjuSF9o+Fhk28DFYk=");
  }

  @Test
  public void createB() throws Exception {

    // mock
    doReturn("from@from ".getBytes()).when(builder).getSign(textCaptor.capture());
    // test method
    String result = builder.createB("from:from@from\r\n", "dkim:dkim");
    // assert
    assertThat(result).isEqualTo("ZnJvbUBmcm9tIA==");
    assertThat(textCaptor.getValue()).isEqualTo("from:from@from\r\ndkim:dkim");
  }

  @Test
  public void normalizeHeader() throws Exception {
    // mock
    MimeMessage message = mock(MimeMessage.class);
    doReturn(" su   b ").when(message).getHeader("Subject", null);
    Address toAddress = mock(Address.class);
    doReturn(new Address[] {toAddress}).when(message).getRecipients(Message.RecipientType.TO);
    Address fromAddress = mock(Address.class);
    doReturn(" from  @from ").when(message).getHeader("From", null);
    doReturn(new Address[] {fromAddress}).when(message).getFrom();
    // test method
    builder.message(message);
    String result = builder.normalizeHeader();
    // assert
    assertThat(result).isEqualTo("subject:su b\r\n" + "from:from @from\r\n");
  }

  @Test
  public void normalizeBody() throws Exception {

    // mock
    MimeMessage message = spy(new MimeMessage((Session) null));
    String body = "a  b  c  d   \r\n" + "e  f  g  h   \r\n" + "  a  b \r\n" + "\r\n\r\n";
    doReturn(new ByteArrayInputStream(body.getBytes())).when(message).getRawInputStream();
    // test method
    builder.message(message);
    String result = builder.normalizeBody();
    // assert
    assertThat(result).isEqualTo("a b c d\r\n" + "e f g h\r\n" + " a b\r\n");
  }

  @Test
  public void createPrivateKey() {}

  @Test
  public void getSign() {}

  @Test
  public void getEpocSecond() {

    // mock
    LocalDateTime now = LocalDateTime.of(2022, 3, 7, 6, 39, 0);
    // test method
    long result = builder.getEpocSecond(now);
    // assert
    assertThat(result).isEqualTo(1646635140L);
  }
}
