// (C) 2026 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ConnectionLimiterTest {

  @Test
  public void acquire_perIpLimit() {
    var limiter = new ConnectionLimiter();

    assertThat(limiter.acquire("1.1.1.1", 10, 2)).isTrue();
    assertThat(limiter.acquire("1.1.1.1", 10, 2)).isTrue();
    assertThat(limiter.acquire("1.1.1.1", 10, 2)).isFalse();
    assertThat(limiter.acquire("2.2.2.2", 10, 2)).isTrue();
    assertThat(limiter.getTotal()).isEqualTo(3);
  }

  @Test
  public void acquire_totalLimit() {
    var limiter = new ConnectionLimiter();

    assertThat(limiter.acquire("1.1.1.1", 2, 10)).isTrue();
    assertThat(limiter.acquire("2.2.2.2", 2, 10)).isTrue();
    assertThat(limiter.acquire("3.3.3.3", 2, 10)).isFalse();
  }

  @Test
  public void release() {
    var limiter = new ConnectionLimiter();
    limiter.acquire("1.1.1.1", 1, 1);

    assertThat(limiter.acquire("1.1.1.1", 1, 1)).isFalse();
    limiter.release("1.1.1.1");
    assertThat(limiter.getTotal()).isZero();
    assertThat(limiter.acquire("1.1.1.1", 1, 1)).isTrue();
  }

  @Test
  public void release_unknown() {
    var limiter = new ConnectionLimiter();

    limiter.release("1.1.1.1");

    assertThat(limiter.getTotal()).isZero();
  }
}
