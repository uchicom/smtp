// (C) 2026 uchicom
package com.uchicom.smtp.auth;

import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PSSParameterSpec;
import java.util.Locale;
import javax.net.ssl.SSLSession;

/**
 * SCRAMのチャネルバインディングデータ(tls-server-end-point, RFC 5929 4節)を求めるクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public final class ChannelBinding {

  /** SCRAMのgs2ヘッダで使用するチャネルバインディング種別名 */
  public static final String TYPE_SERVER_END_POINT = "tls-server-end-point";

  private ChannelBinding() {}

  /**
   * TLSセッションのサーバー証明書から、tls-server-end-pointのデータを求める.
   *
   * @param session サーバー側のTLSセッション
   * @return バインディングデータ。証明書がない、またはハッシュ関数を決められない場合はnull
   */
  public static byte[] serverEndPoint(SSLSession session) {
    Certificate[] certificates = session.getLocalCertificates();
    if (certificates == null
        || certificates.length == 0
        || !(certificates[0] instanceof X509Certificate certificate)) {
      return null;
    }
    return serverEndPoint(certificate);
  }

  /**
   * サーバー証明書のハッシュを求める. 署名に使われたハッシュ関数を使い、MD5とSHA-1の場合はSHA-256に置き換える.
   *
   * @param certificate サーバー証明書
   * @return バインディングデータ。ハッシュ関数を決められない場合(Ed25519など)はnull
   */
  public static byte[] serverEndPoint(X509Certificate certificate) {
    String digestName = digestName(certificate);
    if (digestName == null) {
      return null;
    }
    try {
      return MessageDigest.getInstance(digestName).digest(certificate.getEncoded());
    } catch (GeneralSecurityException e) {
      return null;
    }
  }

  /** 証明書の署名アルゴリズムから、使用するダイジェスト名を決める. 決められない場合はnull. */
  static String digestName(X509Certificate certificate) {
    String signature = certificate.getSigAlgName().toUpperCase(Locale.ROOT);
    String hash;
    int index = signature.indexOf("WITH");
    if (index > 0) {
      hash = signature.substring(0, index);
    } else if ("RSASSA-PSS".equals(signature)) {
      hash = pssDigest(certificate);
    } else {
      // Ed25519/Ed448などハッシュ関数が定義されない署名
      return null;
    }
    if (hash == null) {
      return null;
    }
    return switch (hash.replace("-", "")) {
      case "MD5", "SHA1" -> "SHA-256";
      case "SHA224" -> "SHA-224";
      case "SHA256" -> "SHA-256";
      case "SHA384" -> "SHA-384";
      case "SHA512" -> "SHA-512";
      case "SHA3224" -> "SHA3-224";
      case "SHA3256" -> "SHA3-256";
      case "SHA3384" -> "SHA3-384";
      case "SHA3512" -> "SHA3-512";
      default -> null;
    };
  }

  /** RSASSA-PSSの署名パラメータからダイジェスト名を取り出す. */
  private static String pssDigest(X509Certificate certificate) {
    byte[] params = certificate.getSigAlgParams();
    if (params == null) {
      return null;
    }
    try {
      AlgorithmParameters parameters = AlgorithmParameters.getInstance("RSASSA-PSS");
      parameters.init(params);
      return parameters.getParameterSpec(PSSParameterSpec.class).getDigestAlgorithm();
    } catch (GeneralSecurityException | IOException e) {
      return null;
    }
  }
}
