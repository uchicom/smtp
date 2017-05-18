// (c) 2017 uchicom
package com.uchicom.smtp;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class ConstantsTest {

	@Test
	public void test() {

		assertTrue(Constants.pattern.matcher("Subject:Viagra").find());
		assertTrue(Constants.pattern.matcher("Subject:VPXL").find());
		assertTrue(Constants.pattern.matcher("Subject:Penisole").find());
		assertTrue(Constants.pattern.matcher("Subject:Cialis").find());
		assertTrue(Constants.pattern.matcher("Subject:Levitra").find());
		assertTrue(Constants.pattern.matcher("Subject: CV").find());
		assertTrue(Constants.pattern.matcher("Subject: ED !").find());
		assertTrue(Constants.pattern.matcher("Subject: pill").find());
		assertTrue(Constants.pattern.matcher("Subject: pills").find());
		assertTrue(Constants.pattern.matcher("Subject: Pill").find());
		assertTrue(Constants.pattern.matcher("Subject: Pills").find());
		assertTrue(Constants.pattern.matcher("Subject: Medicine").find());
		assertTrue(Constants.pattern.matcher("Subject: medicine").find());
		assertTrue(Constants.pattern.matcher("Subject: hair").find());
		assertTrue(Constants.pattern.matcher("Subject: Salary").find());

		assertTrue(Constants.pattern.matcher("Subject:aViagra").find());
		assertTrue(Constants.pattern.matcher("Subject:aVPXL").find());
		assertTrue(Constants.pattern.matcher("Subject:aPenisole").find());
		assertTrue(Constants.pattern.matcher("Subject:aCialis").find());
		assertTrue(Constants.pattern.matcher("Subject:aLevitra").find());
		assertTrue(Constants.pattern.matcher("Subject: CV ").find());
		assertTrue(Constants.pattern.matcher("Subject: ED ").find());
		assertTrue(Constants.pattern.matcher("Subject: pill ").find());
		assertTrue(Constants.pattern.matcher("Subject: pills ").find());
		assertTrue(Constants.pattern.matcher("Subject: Pill ").find());
		assertTrue(Constants.pattern.matcher("Subject: Pills ").find());
		assertTrue(Constants.pattern.matcher("Subject: Medicine ").find());
		assertTrue(Constants.pattern.matcher("Subject: medicine ").find());
		assertTrue(Constants.pattern.matcher("Subject: hair ").find());
		assertTrue(Constants.pattern.matcher("Subject: Salary ").find());


		assertTrue(Constants.pattern.matcher("Subject:aViagrab").find());
		assertTrue(Constants.pattern.matcher("Subject:aVPXLb").find());
		assertTrue(Constants.pattern.matcher("Subject:aPenisoleb").find());
		assertTrue(Constants.pattern.matcher("Subject:aCialisb").find());
		assertTrue(Constants.pattern.matcher("Subject:aLevitrab").find());
		assertTrue(Constants.pattern.matcher("Subject: CV").find());
		assertTrue(Constants.pattern.matcher("Subject: ED!").find());
		assertTrue(Constants.pattern.matcher("Subject: pill!").find());
		assertTrue(Constants.pattern.matcher("Subject: pills!").find());
		assertTrue(Constants.pattern.matcher("Subject: Pill!").find());
		assertTrue(Constants.pattern.matcher("Subject: Pills!").find());
		assertTrue(Constants.pattern.matcher("Subject: Medicine!").find());
		assertTrue(Constants.pattern.matcher("Subject: medicine!").find());
		assertTrue(Constants.pattern.matcher("Subject: hair!").find());
		assertTrue(Constants.pattern.matcher("Subject: Salary!").find());


		assertTrue(Constants.pattern.matcher("Subject:Viagrab").find());
		assertTrue(Constants.pattern.matcher("Subject:VPXLb").find());
		assertTrue(Constants.pattern.matcher("Subject:Penisoleb").find());
		assertTrue(Constants.pattern.matcher("Subject:Cialisb").find());
		assertTrue(Constants.pattern.matcher("Subject:Levitrab").find());
		assertTrue(Constants.pattern.matcher(" Click Here ").find());
		assertTrue(Constants.pattern.matcher("From: ").find());
		assertTrue(Constants.pattern.matcher("Salary is ").find());
		assertTrue(Constants.pattern.matcher(" Casino ").find());

		assertFalse(Constants.pattern.matcher("Subject:Via gra").find());
		assertFalse(Constants.pattern.matcher("Subject:VP XL").find());
		assertFalse(Constants.pattern.matcher("Subject:Peni sole").find());
		assertFalse(Constants.pattern.matcher("Subject:Cia lis").find());
		assertFalse(Constants.pattern.matcher("Subject:Levi tra").find());
		assertFalse(Constants.pattern.matcher("Subject:aCVIC").find());
		assertFalse(Constants.pattern.matcher("Subject: EDIT").find());
		assertFalse(Constants.pattern.matcher("Subject: apill!").find());
		assertFalse(Constants.pattern.matcher("Subject: apills!").find());
		assertFalse(Constants.pattern.matcher("Subject: aPill!").find());
		assertFalse(Constants.pattern.matcher("Subject: aPills!").find());
		assertFalse(Constants.pattern.matcher("Subject: aMedicine!").find());
		assertFalse(Constants.pattern.matcher("Subject: amedicine!").find());
		assertFalse(Constants.pattern.matcher("Subject: ahair!").find());
		assertFalse(Constants.pattern.matcher("Subject: aSalary!").find());
		assertFalse(Constants.pattern.matcher(" From:  ").find());
		assertFalse(Constants.pattern.matcher(" Casinoi ").find());
	}

}
