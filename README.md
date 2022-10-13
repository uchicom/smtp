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

## .smtp.yml
```
account:
password:
webhook:
send:
	method: post
	url: https://dokosoko/share/api/webhook/payjp
	header:
	Content-Type: application/json
	Webhook-Token: smtp_webhook_0123456789
	body:
	template: {title:"${subject}", content:"${content:1}"} // :1を指定するとmatch
	parameter:
		subject:
		extract: subject
		match: ^abc$
		content: ^abc(def)gh$ // 括弧を
	query:
	... // bodyと同じ形式
detection: // 配列はorで
	- subject: and 条件
	content:
	from: dokosoko@mail.com
	to: to@dokosoko.com
	cc:
	- subject:
	content:
	from:
	to:
	cc:
```
