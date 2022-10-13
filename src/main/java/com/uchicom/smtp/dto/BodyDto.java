// (C) 2022 uchicom
package com.uchicom.smtp.dto;

import java.util.Map;

public class BodyDto {
  String template;
  Map<String, ParameterDto> parameter;
}
