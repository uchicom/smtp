# smtp

[![Maven Central](https://img.shields.io/maven-central/v/com.uchicom/smtp.svg)](http://search.maven.org/#search|ga|1|com.uchicom.smtp)
[![License](https://img.shields.io/github/license/uchicom/smtp.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Java CI with Maven](https://github.com/uchicom/smtp/actions/workflows/package.yml/badge.svg)](https://github.com/uchicom/smtp/actions/workflows/package.yml)

smtp server

## mvn
### サーバ起動
```
mvn exec:java "-Dexec.mainClass=com.uchicom.smtp.Main"

mvn exec:java "-Dexec.mainClass=com.uchicom.smtp.Main" "-Dexec.args=-port 8025"

mvn exec:java "-Dexec.mainClass=com.uchicom.smtp.Main" "-Dexec.args=-port 8025 -keyStoreName keystore -keyStorePass changeit"
```
```
mvn exec:java "-Dexec.mainClass=com.uchicom.smtp.TestMain"
```

### 入力制限パラメータ
既定値はGmailの受信仕様とRFC 5321に合わせています。行長は改行(CRLF)を除く文字数です。

| パラメータ | 既定値 | RFC 5321 / Gmail | 内容 |
| --- | --- | --- | --- |
| -maxCommandLine | 510 | コマンド行は512オクテット(CRLF込み) | コマンド行長の上限。超過時は500を返して切断 |
| -maxLine | 998 | 本文行は1000オクテット(CRLF込み) | DATA中の1行の上限。超過した行は切断せず、上限位置で折り返して(CRLFを挿入して)受け付ける |
| -maxSize | 35882577 | 最低64KB。Gmailは約34MBをSIZEで広告 | メッセージサイズの上限(バイト)。超過時は552を返して破棄。EHLOでSIZEとして広告し、MAIL FROMのSIZE=が超過していれば事前に552を返す |
| -maxRcpt | 100 | 最低100件。Gmailは100件まで | 1通あたりの宛先数の上限。超過時は452を返す |
| -soTimeout | 300000 | コマンド待ちは最低5分 | ソケットタイムアウト(ミリ秒) |
| -maxConn | 100 | 規定なし | 同時接続数の上限。超過時は421を返して切断 |
| -maxConnPerIp | 10 | 規定なし | IPごとの同時接続数の上限。リバースプロキシやNAT配下では接続元が1つに見えるため、必要に応じて引き上げる |

アドレス構文が不正な`MAIL FROM` / `RCPT TO`は501を返します。バウンスメール用のヌル送信元(`MAIL FROM:<>`)は受け付けます(`RCPT TO:<>`は501)。

## SMTP認証 (AUTH)
`-transfer true` のとき、外部宛ての中継に認証が必要になる。EHLO で次の SASL 機構を広告する。

- SCRAM-SHA-512 / SCRAM-SHA-384 / SCRAM-SHA-256 / SCRAM-SHA-224 / SCRAM-SHA-1 (RFC 5802, 7677)
- PLAIN / LOGIN

**対応しない**
- `-PLUS` 系(チャネルバインディング): Java の標準 API では `tls-unique` と `tls-exporter`(RFC 9266)に必要な値を取得できないため。
- SCRAM-SHA3-512: 仕様がドラフトのため。

キーストアを指定した場合は、STARTTLS 完了までは AUTH を広告せず、受け付けない。

パスワードファイル(`.smtp`)は**平文のまま保存**する(SCRAM の資格情報をサーバ側で導出するため)。ファイルは実行ユーザー以外が読めないようにする。

## keytool
```
keytool -genkey -alias smtp -keyalg RSA -keystore keystore -storepass changeit
```

## .webhook.yml
```
send:
	method: post
	url: https://dokosoko/test/api/webhook/mail
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
