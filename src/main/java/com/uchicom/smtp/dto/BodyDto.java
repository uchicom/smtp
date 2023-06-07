// (C) 2022 uchicom
package com.uchicom.smtp.dto;

import java.util.Map;

public class BodyDto {
  public String template;
  public Map<String, ParameterDto> parameter;
}
