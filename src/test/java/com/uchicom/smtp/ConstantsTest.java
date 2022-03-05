// (C) 2017 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** @author uchicom: Shigeki Uchiyama */
public class ConstantsTest {

  @Test
  public void test() {

    assertThat(Constants.pattern.matcher("Subject:Viagra").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:VPXL").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:Penisole").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:Cialis").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:Levitra").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: CV").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: ED !").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: pill").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: pills").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Pill").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Pills").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Medicine").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: medicine").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: hair").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Salary").find()).isTrue();

    assertThat(Constants.pattern.matcher("Subject:aViagra").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:aVPXL").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:aPenisole").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:aCialis").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:aLevitra").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: CV ").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: ED ").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: pill ").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: pills ").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Pill ").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Pills ").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Medicine ").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: medicine ").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: hair ").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Salary ").find()).isTrue();

    assertThat(Constants.pattern.matcher("Subject:aViagrab").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:aVPXLb").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:aPenisoleb").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:aCialisb").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:aLevitrab").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: CV").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: ED!").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: pill!").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: pills!").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Pill!").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Pills!").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Medicine!").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: medicine!").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: hair!").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject: Salary!").find()).isTrue();

    assertThat(Constants.pattern.matcher("Subject:Viagrab").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:VPXLb").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:Penisoleb").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:Cialisb").find()).isTrue();
    assertThat(Constants.pattern.matcher("Subject:Levitrab").find()).isTrue();
    assertThat(Constants.pattern.matcher(" Click Here ").find()).isTrue();
    assertThat(Constants.pattern.matcher("From: ").find()).isTrue();
    assertThat(Constants.pattern.matcher("Salary is ").find()).isTrue();
    assertThat(Constants.pattern.matcher(" Casino ").find()).isTrue();

    assertThat(Constants.pattern.matcher("Subject:Via gra").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject:VP XL").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject:Peni sole").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject:Cia lis").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject:Levi tra").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject:aCVIC").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject: EDIT").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject: apill!").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject: apills!").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject: aPill!").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject: aPills!").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject: aMedicine!").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject: amedicine!").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject: ahair!").find()).isFalse();
    assertThat(Constants.pattern.matcher("Subject: aSalary!").find()).isFalse();
    assertThat(Constants.pattern.matcher(" From:  ").find()).isFalse();
    assertThat(Constants.pattern.matcher(" Casinoi ").find()).isFalse();
  }
}
