// (C) 2025 uchicom
package com.uchicom.smtp.factory.di;

import com.uchicom.smtp.Constants;
import com.uchicom.util.logging.DailyRollingFileHandler;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class DIFactory {

  public static Logger logger() {
    try {
      var PROJECT_NAME = "smtp";
      var name =
          Stream.of(Thread.currentThread().getStackTrace())
              .map(StackTraceElement::getClassName)
              .filter(className -> className.endsWith("Main"))
              .findFirst()
              .orElse(PROJECT_NAME);
      var logger = Logger.getLogger(name);
      if (!PROJECT_NAME.equals(name)) {
        if (Arrays.stream(logger.getHandlers())
            .filter(handler -> handler instanceof DailyRollingFileHandler)
            .findFirst()
            .isEmpty()) {
          logger.addHandler(
              new DailyRollingFileHandler(
                  Constants.LOG_DIR, Constants.LOG_FORMAT, name + "_%d.log", Constants.ZONE_ID));
        }
      }
      return logger;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
