# enterprise-user-provisioning-system
対象:
- OracleApiServer.java
- ActiveDirectoryService.java
- EdsService.java
- app.js
- index.html

注意:

1. 特に Oracle の各 INSERT は実テーブルの列定義/NOT NULL制約に依存するため、原本と完全一致ではありません。
2. AD/IAM/DB の認証情報は環境変数利用を前提にしています。
3. まず本番環境へそのまま投入せず、まず閉域の検証環境でコンパイル・SQL確認をしてください。
