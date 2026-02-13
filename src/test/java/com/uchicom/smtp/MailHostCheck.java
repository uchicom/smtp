// (C) 2018 uchicom
package com.uchicom.smtp;

import javax.naming.NamingException;

public class MailHostCheck {

  public static void main(String[] args) {
    if (args.length > 0) {
      try {
        String[] hosts = SmtpProcess.lookupMailHosts(args[0]);
        if (hosts != null) {
          for (String host : hosts) {
            System.out.println(host);
          }
        }
      } catch (NamingException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
