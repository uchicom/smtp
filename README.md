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

mvn exec:java "-Dexec.mainClass=com.uchicom.smtp.Main" "-Dexec.args=-port 8025 -type virtual"
```
```
mvn exec:java "-Dexec.mainClass=com.uchicom.smtp.TestMain"
```

### サーバー種別 (-type)
| 値 | 内容 |
| --- | --- |
| single (既定) | 1接続ずつ順番に処理する |
| multi | 接続ごとにスレッドを生成して処理する |
| pool | `-pool` で指定した数のスレッドプールで処理する |
| virtual | 接続ごとに仮想スレッド(Virtual Thread)で処理する |

`virtual`は数に上限がないため、`-maxConn` / `-maxConnPerIp`による接続制限は必須(既定値で有効)。
処理の大半がブロッキングI/O(ソケット、DNS問合せ、転送、Webhook)なので仮想スレッドと相性がよい。

- SCRAM認証は毎回PBKDF2を計算するCPU処理のため、仮想スレッドでも速くならない。認証失敗の回数制限は未実装のため、大量の認証要求には注意する。
- 仮想スレッドは通常の`jstack`に出ない。`jcmd <pid> Thread.dump_to_file <file>`を使う。ログのスレッド名も空になる。

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

### 転送時のTLS証明書検証 (-checkServerIdentity)
`-transfer true`で転送する際、STARTTLS後にサーバー証明書のホスト名(SAN)が接続先ホスト名と一致するかを検証する(既定: `true`)。

| 値 | 内容 |
| --- | --- |
| true (既定) | 証明書のホスト名を検証する。不一致の場合は転送に失敗する |
| false | 証明書のホスト名を検証しない |

独自ドメイン宛てでも共通証明書(例: さくらインターネットの`*.sakura.ne.jp`)で応答するサーバーへ転送する場合、`No subject alternative DNS name matching ... found`で失敗する。その場合は`-checkServerIdentity false`を指定する。
`false`にすると暗号化は行われるが、接続先のなりすまし(中間者攻撃)を検出できなくなる。

#### メモリ見積もり
DATAは`MemoryMail`にメモリ上で溜めるため、最悪時のメモリ使用量はおおよそ`maxSize` × `maxConn`になる。
既定値では 35,882,577バイト × 100 ≒ 3.58GB(約3.4GiB)。`-maxConn`と`-maxSize`は、ヒープ上限(`-Xmx`)に収まるように組み合わせて設定する。

| maxSize | maxConn | 最悪値の目安 |
| --- | --- | --- |
| 35882577 (約34MiB, 既定) | 100 (既定) | 約3.4GiB |
| 35882577 | 20 | 約0.7GiB |
| 10485760 (10MiB) | 100 | 約1.0GiB |

アドレス構文が不正な`MAIL FROM` / `RCPT TO`は501を返します。バウンスメール用のヌル送信元(`MAIL FROM:<>`)は受け付けます(`RCPT TO:<>`は501)。

## SMTP認証 (AUTH)
`-transfer true` のとき、外部宛ての中継に認証が必要になる。EHLO で次の SASL 機構を広告する。

- SCRAM-SHA-512 / SCRAM-SHA-384 / SCRAM-SHA-256 / SCRAM-SHA-224 / SCRAM-SHA-1 (RFC 5802, 7677)
- SCRAM-SHA-512-PLUS / SCRAM-SHA-384-PLUS / SCRAM-SHA-256-PLUS / SCRAM-SHA-224-PLUS / SCRAM-SHA-1-PLUS (チャネルバインディング `tls-server-end-point`, RFC 5929)
- PLAIN / LOGIN

`-PLUS` 系は TLS 確立後のみ広告する。バインディングデータはサーバー証明書のハッシュで、署名に使われたハッシュ関数を使う(MD5/SHA-1 署名の場合は SHA-256)。Ed25519 / Ed448 の証明書はハッシュ関数が定義されないため、`-PLUS` 系を広告しない。`-PLUS` を広告している接続では、通常の SCRAM で `y`(クライアントが対応を知らないという宣言)を送るとダウングレードとして拒否する。

**対応しない**
- `tls-unique` / `tls-exporter`(RFC 9266): Java の標準 API では必要な値(Finished メッセージ、エクスポーター鍵)を取得できないため。`tls-server-end-point` は標準 API だけで求められる。
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
