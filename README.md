# smtp

[![Maven Central](https://img.shields.io/maven-central/v/com.uchicom/smtp.svg)](http://search.maven.org/#search|ga|1|com.uchicom.smtp)
[![License](https://img.shields.io/github/license/uchicom/smtp.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Java CI with Maven](https://github.com/uchicom/smtp/actions/workflows/package.yml/badge.svg)](https://github.com/uchicom/smtp/actions/workflows/package.yml)

smtp server

## mvn
### サーバ起動
```
mvn exec:java "-Dexec.mainClass=com.uchicom.smtp.Main"

mvn exec:java "-Dexec.mainClass=com.uchicom.smtp.Main" -Dexec.args="-port 8025"

mvn exec:java "-Dexec.mainClass=com.uchicom.smtp.Main" -Dexec.args="-port 8025 -keyStoreName keystore -keyStorePass changeit"
```
```
mvn exec:java "-Dexec.mainClass=com.uchicom.smtp.TestMain"
```

## keytool
```
keytool -genkey -alias smtp -keyalg RSA -keystore keystore -storepass changeit
```
