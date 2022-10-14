// (C) 2022 uchicom
package com.uchicom.smtp.dto;

import java.util.Map;

public class SendDto {
  public String method;
  public String url;
  public Map<String, String> header;
  public BodyDto body;
}
