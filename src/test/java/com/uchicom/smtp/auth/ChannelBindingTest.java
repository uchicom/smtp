// (C) 2026 uchicom
package com.uchicom.smtp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.AlgorithmParameters;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

/** tls-server-end-pointのバインディングデータのテスト. */
class ChannelBindingTest {

  static final byte[] ENCODED = {1, 2, 3, 4, 5};

  static X509Certificate certificate(String sigAlgName, byte[] sigAlgParams) throws Exception {
    var certificate = mock(X509Certificate.class);
    when(certificate.getSigAlgName()).thenReturn(sigAlgName);
    when(certificate.getSigAlgParams()).thenReturn(sigAlgParams);
    when(certificate.getEncoded()).thenReturn(ENCODED);
    return certificate;
  }

  static byte[] digest(String name) throws Exception {
    return MessageDigest.getInstance(name).digest(ENCODED);
  }

  @Test
  void weakHash_isReplacedBySha256() throws Exception {
    assertThat(ChannelBinding.serverEndPoint(certificate("MD5withRSA", null)))
        .isEqualTo(digest("SHA-256"));
    assertThat(ChannelBinding.serverEndPoint(certificate("SHA1withRSA", null)))
        .isEqualTo(digest("SHA-256"));
    assertThat(ChannelBinding.serverEndPoint(certificate("SHA1withECDSA", null)))
        .isEqualTo(digest("SHA-256"));
  }

  @Test
  void signatureHash_isUsed() throws Exception {
    assertThat(ChannelBinding.serverEndPoint(certificate("SHA256withRSA", null)))
        .isEqualTo(digest("SHA-256"));
    assertThat(ChannelBinding.serverEndPoint(certificate("SHA384withECDSA", null)))
        .isEqualTo(digest("SHA-384"));
    assertThat(ChannelBinding.serverEndPoint(certificate("SHA512withRSA", null)))
        .isEqualTo(digest("SHA-512"));
    assertThat(ChannelBinding.serverEndPoint(certificate("SHA224withRSA", null)))
        .isEqualTo(digest("SHA-224"));
    assertThat(ChannelBinding.serverEndPoint(certificate("SHA3-256withRSA", null)))
        .isEqualTo(digest("SHA3-256"));
  }

  @Test
  void rsaPss_usesDigestOfParameters() throws Exception {
    var parameters = AlgorithmParameters.getInstance("RSASSA-PSS");
    parameters.init(new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, 1));

    assertThat(ChannelBinding.serverEndPoint(certificate("RSASSA-PSS", parameters.getEncoded())))
        .isEqualTo(digest("SHA-384"));
  }

  @Test
  void rsaPss_withoutParameters_isUnsupported() throws Exception {
    assertThat(ChannelBinding.serverEndPoint(certificate("RSASSA-PSS", null))).isNull();
    assertThat(ChannelBinding.serverEndPoint(certificate("RSASSA-PSS", new byte[] {1}))).isNull();
  }

  @Test
  void signatureWithoutHash_isUnsupported() throws Exception {
    assertThat(ChannelBinding.serverEndPoint(certificate("Ed25519", null))).isNull();
    assertThat(ChannelBinding.serverEndPoint(certificate("Ed448", null))).isNull();
    assertThat(ChannelBinding.serverEndPoint(certificate("UnknownWithRSA", null))).isNull();
  }

  @Test
  void session_usesFirstLocalCertificate() throws Exception {
    var session = mock(SSLSession.class);
    var certificate = certificate("SHA384withRSA", null);
    when(session.getLocalCertificates()).thenReturn(new Certificate[] {certificate});

    assertThat(ChannelBinding.serverEndPoint(session)).isEqualTo(digest("SHA-384"));
  }

  @Test
  void session_withoutCertificate_isUnsupported() {
    var session = mock(SSLSession.class);

    assertThat(ChannelBinding.serverEndPoint(session)).isNull();
    when(session.getLocalCertificates()).thenReturn(new Certificate[0]);
    assertThat(ChannelBinding.serverEndPoint(session)).isNull();
  }
}
