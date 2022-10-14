// (C) 2022 uchicom
package com.uchicom.smtp.dto;

import java.util.Map;

public class DetectionDto {
  public Map<String, String> header;
  public String from;
  public String to;
  public String cc;
  public String bcc;
  public String subject;
  public MultipartDto multipart;
  public String content;
}
