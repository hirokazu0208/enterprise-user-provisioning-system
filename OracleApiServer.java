import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import javax.naming.directory.DirContext;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class OracleApiServer {

    private static final String DB_URL =
        System.getenv().getOrDefault(
            "DB_URL",
            "jdbc:oracle:thin:@//172.17.1.35/*****"
        );

    private static final String DB_USER =
        System.getenv().getOrDefault("DB_USER", "****");

    private static final String DB_PASSWORD =
        System.getenv().getOrDefault("DB_PASSWORD", "****");

    private static Connection getConnection() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        return DriverManager.getConnection(
            DB_URL,
            DB_USER,
            DB_PASSWORD
        );
    }

    public static void main(String[] args) throws Exception {
        HttpServer server =
            HttpServer.create(new InetSocketAddress(8080), 0);

        // API
        server.createContext(
            "/api/users/create",
            new CreateUserHandler()
        );

        server.createContext(
            "/api/users/delete",
            new DeleteUserHandler()
        );

        server.createContext(
            "/api/users",
            new UserHandler()
        );

        server.createContext(
            "/api/ad/ous",
            new AdOuHandler()
        );

        server.createContext(
            "/api/ad/users",
            new AdUserHandler()
        );

        server.createContext(
            "/api/ad/only-users",
            new AdOnlyUserHandler()
        );

        server.setExecutor(null);
        server.start();

        System.out.println("起動:http://localhost:8080/api/users");
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = "";

            try {
                Class.forName("oracle.jdbc.OracleDriver");

                Connection conn = getConnection();
                Statement stmt = conn.createStatement();

                String sql =
                    "SELECT " +
                    "AJ2.年度, " +
                    "TRIM(AJ2.所属CD) AS 所属CD, " +
                    "AJ2.所属名称, " +
                    "TRIM(AJ1.職員番号) AS 職員番号ログインID, " +
                    "AJ3.姓, AJ3.名, " +
                    "AJ5.担当CD, " +
                    "AJ5.担当名称 " +
                    "FROM AJ職員基本制御 AJ1 " +
                    "INNER JOIN AJ所属名称 AJ2 " +
                    "ON AJ1.既定ログイン所属CD = AJ2.所属CD " +
                    "INNER JOIN AJ職員基本氏名 AJ3 " +
                    "ON AJ1.職員番号 = AJ3.職員番号 " +
                    "INNER JOIN AJ担当者 AJ4 " +
                    "ON AJ1.職員番号 = AJ4.職員番号 " +
                    "INNER JOIN AJ担当 AJ5 " +
                    "ON AJ4.担当CD = AJ5.担当CD " +
                    "WHERE 1=1 " +
                    "ORDER BY AJ2.所属名称, AJ1.職員番号";

                ResultSet rs = stmt.executeQuery(sql);

                StringBuilder sb = new StringBuilder();
                sb.append("[");

                boolean first = true;
                int no = 1;

                while (rs.next()) {
                    if (!first) sb.append(",");
                    first = false;

                    sb.append("{");
                    sb.append("\"no\":\"").append(no).append("\",");
                    sb.append("\"年度\":\"").append(safe(rs.getString("年度"))).append("\",");
                    sb.append("\"所属CD\":\"").append(safe(rs.getString("所属CD"))).append("\",");
                    sb.append("\"所属名称\":\"").append(safe(rs.getString("所属名称"))).append("\",");
                    sb.append("\"職員番号ログインID\":\"").append(safe(rs.getString("職員番号ログインID"))).append("\",");
                    sb.append("\"姓\":\"").append(safe(rs.getString("姓"))).append("\",");
                    sb.append("\"名\":\"").append(safe(rs.getString("名"))).append("\",");
                    sb.append("\"担当CD\":\"").append(safe(rs.getString("担当CD"))).append("\",");
                    sb.append("\"担当名称\":\"").append(safe(rs.getString("担当名称"))).append("\"");
                    sb.append("}");

                    no++;
                }

                sb.append("]");
                json = sb.toString();

                rs.close();
                stmt.close();
                conn.close();

            } catch (Exception e) {
                json =
                    "{\"error\":\"" +
                    safe(e.getMessage()) +
                    "\"}";
                e.printStackTrace();
            }

            addCors(exchange);
            sendJson(exchange, 200, json);
        }
    }

    private static String safe(String s) {
        if (s == null) return "";

        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }

    static class CreateUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            System.out.println("CreateUserHandlerに入りました");
            System.out.println("method: " + exchange.getRequestMethod());

            addCors(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            System.out.println("Create API 呼び出し");

            String bodyText = readBody(exchange);
            System.out.println("受信JSON");
            System.out.println(bodyText);

            String adOuDn = getJsonValue(bodyText, "adOUDn");
            String groupCd = getJsonValue(bodyText, "groupCd").trim();
            String id = getJsonValue(bodyText, "id");
            String sei = getJsonValue(bodyText, "sei");
            String mei = getJsonValue(bodyText, "mei");
            String tantoCd = getJsonValue(bodyText, "tantoCd").trim();
            String year = getJsonValue(bodyText, "year");
            String dateStart = getJsonValue(bodyText, "dateStart");

            String edsKanaSei = null;
            String edsKanaMei = "スイドウ";

            String kanriValue = getJsonValue(bodyText, "kanri");
            String syokuValue = getJsonValue(bodyText, "syoku");
            boolean kanri = "true".equalsIgnoreCase(kanriValue);
            boolean syoku = "true".equalsIgnoreCase(syokuValue);

            LocalDateTime now = LocalDateTime.now();
            String currentDate =
                now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String currentTime =
                now.format(DateTimeFormatter.ofPattern("HHmmss"));

            String dataCreateUser = "";
            String dataUpdateUser = "";
            String dataUpdateType = "";

            Connection conn = null;

            try {
                conn = getConnection();
                conn.setAutoCommit(false);

                edsKanaSei = getShozokuKana(conn, year, groupCd);
                if (edsKanaSei == null || edsKanaSei.trim().isEmpty()) {
                    System.out.println("所属名称カナ取得不可 スイドウを使用");
                    edsKanaSei = "スイドウ";
                }

                System.out.println("EDSカナ姓取得結果 = " + edsKanaSei);
                System.out.println("EDSカナ名固定定値 = " + edsKanaMei);

                boolean dbExists = dbUserExists(conn, id);
                System.out.println("DBユーザー存在確認 = " + dbExists);

                boolean adExists = false;
                DirContext adCtxCheck = null;

                try {
                    String adUser = System.getenv("AD_USER");
                    String adPassword = System.getenv("AD_PASSWORD");

                    adCtxCheck =
                        ActiveDirectoryService.connect(
                            adUser,
                            adPassword
                        );

                    adExists =
                        ActiveDirectoryService.userExists(
                            adCtxCheck,
                            "OU=水道局_本番,DC=FUKUSUI,DC=WALAN",
                            id
                        );

                    System.out.println("ADユーザー存在確認 = " + adExists);

                } catch (Exception e) {
                    System.out.println("AD接続・存在確認エラー");
                    e.printStackTrace();

                    String response =
                        "{\"result\":\"ng\"," +
                        "\"code\":\"AD_CONNECTION_ERROR\"," +
                        "\"message\":\"ADへ接続できないため登録処理を中止しました\"}";

                    sendJson(exchange, 503, response);
                    return;

                } finally {
                    ActiveDirectoryService.close(adCtxCheck);
                }

                if (dbExists && adExists) {
                    String response =
                        "{\"result\":\"ng\"," +
                        "\"code\":\"USER_ALREADY_EXISTS\"," +
                        "\"message\":\"この職員番号は既に登録されています\"}";

                    sendJson(exchange, 409, response);
                    return;
                }

                if (dbExists && !adExists) {
                    String response =
                        "{\"result\":\"ng\"," +
                        "\"code\":\"DB_ONLY_EXISTS\"," +
                        "\"message\":\"データベースには登録されていますが、ADには存在しません\"}";

                    sendJson(exchange, 409, response);
                    return;
                }

                if (!dbExists && adExists) {
                    String response =
                        "{\"result\":\"ng\"," +
                        "\"code\":\"AD_ONLY_EXISTS\"," +
                        "\"message\":\"ADには登録されていますが、データベースには存在しません\"}";

                    sendJson(exchange, 409, response);
                    return;
                }

                // 所属名称から監査情報取得
                String groupSql =
                    "SELECT データ作成者, データ更新者, データ更新種別 " +
                    "FROM AJ所属名称 " +
                    "WHERE TRIM(所属CD) = ? " +
                    "AND 年度 = ?";

                try (PreparedStatement ps =
                         conn.prepareStatement(groupSql)) {

                    ps.setString(1, groupCd);
                    ps.setString(2, year);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dataCreateUser = rs.getString("データ作成者");
                            dataUpdateUser = rs.getString("データ更新者");
                            dataUpdateType = rs.getString("データ更新種別");
                        } else {
                            throw new Exception(
                                "AJ所属名称に該当データがありません"
                            );
                        }
                    }
                }

                // ---- Oracle INSERT ----
                // 画像から確認できた現行テーブル群を、1トランザクションとして登録する。
                insertUserTables(
                    conn,
                    id,
                    year,
                    groupCd,
                    tantoCd,
                    sei,
                    mei,
                    dateStart,
                    currentDate,
                    currentTime,
                    dataCreateUser,
                    dataUpdateUser,
                    dataUpdateType
                );

                // ---- AD ----
                DirContext adCtx = null;

                try {
                    String adUser = System.getenv("AD_USER");
                    String adPassword = System.getenv("AD_PASSWORD");

                    System.out.println("AD作成開始");
                    System.out.println("adOuDn = " + adOuDn);
                    System.out.println("ADログインID = " + id);

                    adCtx =
                        ActiveDirectoryService.connect(
                            adUser,
                            adPassword
                        );

                    boolean exists =
                        ActiveDirectoryService.userExists(
                            adCtx,
                            "DC=FUKUSUI,DC=WALAN",
                            id
                        );

                    if (exists) {
                        System.out.println("ADユーザーは既に存在します: " + id);
                    } else {
                        String displayName = sei + " " + mei;

                        String userDn =
                            ActiveDirectoryService.createUser(
                                adCtx,
                                adOuDn,
                                id,
                                sei,
                                mei
                            );

                        System.out.println("ADユーザー作成DN = " + userDn);

                        // 画像では PW = LOGINID
                        ActiveDirectoryService.setPassword(
                            adCtx,
                            userDn,
                            id
                        );

                        System.out.println("ADパスワード設定成功");

                        ActiveDirectoryService.enableUser(
                            adCtx,
                            userDn
                        );

                        userDn =
                            ActiveDirectoryService.renameUserCn(
                                adCtx,
                                userDn,
                                adOuDn,
                                displayName
                            );

                        System.out.println("リネーム後 userDn = " + userDn);

                        String officeName = extractOuName(adOuDn);

                        String normalGroupName = null;
                        String managerGroupName = null;
                        String groupSearchBaseDn = adOuDn;

                        normalGroupName = "G_" + officeName;
                        managerGroupName = "G_" + officeName + "_管理職";

                        // 委託業者系例外
                        if (
                            officeName.equals("委託業者A") ||
                            officeName.equals("委託業者B") ||
                            officeName.equals("委託業者C") ||
                            officeName.equals("委託業者D") ||
                            officeName.equals("委託業者E") ||
                            officeName.equals("委託業者F")
                        ) {
                            normalGroupName = "G_水道局_業者";
                            managerGroupName = null;
                            groupSearchBaseDn =
                                "OU=水道局_本番,DC=FUKUSUI,DC=WALAN";
                        } else if (officeName.equals("転居清算業者")) {
                            normalGroupName = "転居清算業者";
                            managerGroupName = null;
                            groupSearchBaseDn =
                                "OU=水道局_本番,DC=FUKUSUI,DC=WALAN";
                        } else if (officeName.equals("給水審査課")) {
                            normalGroupName = "G_水道局_職員";
                            managerGroupName = "G_水道局_管理職";
                            groupSearchBaseDn =
                                "OU=水道局_本番,DC=FUKUSUI,DC=WALAN";
                        }

                        System.out.println(
                            "グループ検索BaseDn = " +
                            groupSearchBaseDn
                        );
                        System.out.println(
                            "一般職員グループ = " +
                            normalGroupName
                        );
                        System.out.println(
                            "管理職グループ = " +
                            managerGroupName
                        );

                        if (syoku && normalGroupName != null) {
                            String normalGroupDn =
                                ActiveDirectoryService.findGroupDn(
                                    adCtx,
                                    groupSearchBaseDn,
                                    normalGroupName
                                );

                            ActiveDirectoryService.addUserToGroup(
                                adCtx,
                                userDn,
                                normalGroupDn
                            );
                        }

                        if (kanri && managerGroupName != null) {
                            String managerGroupDn =
                                ActiveDirectoryService.findGroupDn(
                                    adCtx,
                                    groupSearchBaseDn,
                                    managerGroupName
                                );

                            ActiveDirectoryService.addUserToGroup(
                                adCtx,
                                userDn,
                                managerGroupDn
                            );
                        }

                        System.out.println("ADユーザー登録完了 = " + id);
                    }

                } finally {
                    ActiveDirectoryService.close(adCtx);
                }

                // ---- EDS ----
                System.out.println("EDSユーザー追加開始: " + id);
                System.out.println("EDSへ渡すカナ姓 = " + edsKanaSei);
                System.out.println("EDSへ渡すカナ名 = " + edsKanaMei);

                EdsService.createUser(
                    "I-BAT01",
                    id,
                    sei,
                    mei,
                    edsKanaSei,
                    edsKanaMei,
                    dateStart,
                    year
                );

                System.out.println("EDSユーザー追加完了: " + id);

                conn.commit();

                String response =
                    "{\"result\":\"ok\",\"message\":\"新規登録が完了しました\"}";

                sendJson(exchange, 200, response);

            } catch (Exception e) {
                e.printStackTrace();

                if (conn != null) {
                    try {
                        conn.rollback();
                        System.out.println("ROLLBACKしました");
                    } catch (Exception rollbackException) {
                        rollbackException.printStackTrace();
                    }
                }

                sendJson(
                    exchange,
                    500,
                    "{\"result\":\"ng\",\"message\":\"create error\"}"
                );

            } finally {
                if (conn != null) {
                    try { conn.close(); } catch (Exception ignored) {}
                }
            }
        }
    }

    static class DeleteUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            System.out.println("DeleteUserHandlerに入りました");
            System.out.println("method:" + exchange.getRequestMethod());

            addCors(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            System.out.println("Delete API 呼び出し");

            String bodyText = readBody(exchange);
            System.out.println("受信JONS");
            System.out.println(bodyText);

            String id = getJsonValue(bodyText, "id");
            System.out.println("削除対象 id = " + id);

            Connection conn = null;
            PreparedStatement ps = null;
            int totalDeleteCount = 0;

            try {
                conn = getConnection();
                conn.setAutoCommit(false);

                // EDS存在確認
                boolean edsExists = false;
                DirContext edsCtxCheck = null;

                try {
                    String edsUser = System.getenv("EDS_USER");
                    String edsPassword = System.getenv("EDS_PASSWORD");

                    edsCtxCheck =
                        EdsService.connect(
                            "I-BAT01",
                            389,
                            edsUser,
                            edsPassword
                        );

                    edsExists =
                        EdsService.userExists(
                            edsCtxCheck,
                            id
                        );

                    System.out.println(
                        "削除前EDSユーザー存在確認 = " +
                        edsExists
                    );

                } finally {
                    if (edsCtxCheck != null) {
                        try { edsCtxCheck.close(); } catch (Exception ignored) {}
                    }
                }

                // DB存在確認
                boolean dbExists = dbUserExists(conn, id);
                System.out.println(
                    "削除前DBユーザー存在確認 = " +
                    dbExists
                );

                if (!dbExists) {
                    sendJson(
                        exchange,
                        404,
                        "{\"result\":\"ng\",\"message\":\"DBに対象ユーザーが存在しないため削除を中止しました\"}"
                    );
                    return;
                }

                // AD存在確認
                boolean adExists = false;
                DirContext adCtxCheck = null;

                try {
                    String adUser = System.getenv("AD_USER");
                    String adPassword = System.getenv("AD_PASSWORD");

                    adCtxCheck =
                        ActiveDirectoryService.connect(
                            adUser,
                            adPassword
                        );

                    adExists =
                        ActiveDirectoryService.userExists(
                            adCtxCheck,
                            "OU=水道局_本番,DC=FUKUSUI,DC=WALAN",
                            id
                        );

                    System.out.println(
                        "削除前 ADユーザー存在確認 = " +
                        adExists
                    );

                } catch (Exception e) {
                    e.printStackTrace();

                    sendJson(
                        exchange,
                        503,
                        "{\"result\":\"ng\",\"code\":\"AD_CONNECTION_ERROR\",\"message\":\"ADへ接続できないため削除処理を中止しました\"}"
                    );
                    return;

                } finally {
                    ActiveDirectoryService.close(adCtxCheck);
                }

                // DB削除
                String[] deleteSqlList = {
                    "DELETE FROM AJ職員基本拡張 WHERE TRIM(職員番号) = ?",
                    "DELETE FROM AJ職員基本氏名 WHERE TRIM(職員番号) = ?",
                    "DELETE FROM AJ職員基本制御 WHERE TRIM(職員番号) = ?",
                    "DELETE FROM AJ職員基本戸籍氏名 WHERE TRIM(職員番号) = ?",
                    "DELETE FROM AJ職員権限情報 WHERE TRIM(職員番号) = ?",
                    "DELETE FROM AJ職員拡張 WHERE TRIM(職員番号) = ?",
                    "DELETE FROM AJ担当者拡張 WHERE TRIM(職員番号) = ?",
                    "DELETE FROM AJ担当者 WHERE TRIM(職員番号) = ?",
                    "DELETE FROM AJ職員 WHERE TRIM(職員番号) = ?",
                    "DELETE FROM AJ職員基本 WHERE TRIM(職員番号) = ?"
                };

                for (int i = 0; i < deleteSqlList.length; i++) {
                    ps = conn.prepareStatement(deleteSqlList[i]);
                    ps.setString(1, id);

                    int count = ps.executeUpdate();
                    totalDeleteCount += count;

                    System.out.println(
                        (i + 1) +
                        " 個数 DELETE件数 = " +
                        count
                    );

                    ps.close();
                    ps = null;
                }

                System.out.println(
                    "DELETE合計件数 = " +
                    totalDeleteCount
                );

                // AD削除
                DirContext adCtx = null;
                try {
                    String adUser = System.getenv("AD_USER");
                    String adPassword = System.getenv("AD_PASSWORD");

                    System.out.println("AD削除処理開始");
                    System.out.println("AD削除開始 ID = " + id);

                    adCtx =
                        ActiveDirectoryService.connect(
                            adUser,
                            adPassword
                        );

                    String adBaseDn =
                        "OU=水道局_本番,DC=FUKUSUI,DC=WALAN";

                    boolean adDeleted =
                        ActiveDirectoryService.deleteUser(
                            adCtx,
                            adBaseDn,
                            id
                        );

                    System.out.println(
                        "AD削除結果 = " +
                        adDeleted
                    );

                } catch (Exception adException) {
                    System.out.println("AD削除エラー");
                    adException.printStackTrace();
                    throw adException;

                } finally {
                    ActiveDirectoryService.close(adCtx);
                }

                // EDS削除
                System.out.println("EDSユーザー削除開始: " + id);

                EdsService.deleteUser(
                    "I-BAT01",
                    id
                );

                System.out.println("EDSユーザー削除完了: " + id);

                // DB-AD-EDS全削除後に確定
                conn.commit();

                System.out.println("削除処理すべて完了: " + id);

            } catch (Exception e) {
                e.printStackTrace();

                try {
                    if (conn != null) {
                        conn.rollback();
                        System.out.println("ROLLBACKしました");
                    }
                } catch (Exception rollbackException) {
                    rollbackException.printStackTrace();
                }

                sendJson(
                    exchange,
                    500,
                    "{\"result\":\"ng\",\"message\":\"delete error\"}"
                );
                return;

            } finally {
                try {
                    if (ps != null) ps.close();
                    if (conn != null) conn.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            sendJson(
                exchange,
                200,
                "{\"result\":\"ok\",\"message\":\"delete api received\"}"
            );
        }
    }

    static class AdOuHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            DirContext ctx = null;

            try {
                String adUser = System.getenv("AD_USER");
                String adPassword = System.getenv("AD_PASSWORD");

                System.out.println("AdOuHandler開始");
                System.out.println("AD_USER取得 = " + (adUser != null));
                System.out.println("AD_PASSWORD取得 = " + (adPassword != null));

                ctx =
                    ActiveDirectoryService.connect(
                        adUser,
                        adPassword
                    );

                String searchBaseDn =
                    "OU=水道局_本番," +
                    "DC=FUKUSUI," +
                    "DC=WALAN";

                System.out.println("searchBaseDn = " + searchBaseDn);

                List<Map<String, String>> ouList =
                    ActiveDirectoryService.getOuList(
                        ctx,
                        searchBaseDn
                    );

                System.out.println("OU取得件数 = " + ouList.size());

                StringBuilder json = new StringBuilder();
                json.append("[");

                for (int i = 0; i < ouList.size(); i++) {
                    Map<String, String> ou = ouList.get(i);

                    if (i > 0) {
                        json.append(",");
                    }

                    json.append("{");
                    json.append("\"name\":\"")
                        .append(escapeJson(ou.get("name")))
                        .append("\",");

                    json.append("\"dn\":\"")
                        .append(escapeJson(ou.get("dn")))
                        .append("\"");

                    json.append("}");
                }

                json.append("]");

                addCors(exchange);
                sendJson(exchange, 200, json.toString());

            } catch (Exception e) {
                e.printStackTrace();

                addCors(exchange);
                sendJson(
                    exchange,
                    500,
                    "{\"error\":\"AD OU取得失敗\"}"
                );

            } finally {
                ActiveDirectoryService.close(ctx);
            }
        }

        private static String escapeJson(String value) {
            if (value == null) return "";

            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        }
    }

    static class AdUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            System.out.println("ADユーザー一覧取得開始");
            String response = "[]";

            try {
                String adUser = System.getenv("AD_USER");
                String adPassword = System.getenv("AD_PASSWORD");

                DirContext ctx =
                    ActiveDirectoryService.connect(
                        adUser,
                        adPassword
                    );

                response =
                    ActiveDirectoryService.getUsersJson(
                        ctx
                    );

                ctx.close();

                System.out.println("ADユーザー一覧取得成功");

                addCors(exchange);
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                e.printStackTrace();

                String error =
                    "{\"result\":\"ng\"," +
                    "\"message\":\"ADユーザー一覧取得失敗\"}";

                addCors(exchange);
                sendJson(exchange, 500, error);
            }
        }
    }

    // ADのみ抽出
    static class AdOnlyUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange)
                throws IOException {

            System.out.println("ADのみユーザー一覧取得開始");

            Connection conn = null;
            DirContext ctx = null;

            try {
                String adUser = System.getenv("AD_USER");
                String adPassword = System.getenv("AD_PASSWORD");

                ctx =
                    ActiveDirectoryService.connect(
                        adUser,
                        adPassword
                    );

                conn = getConnection();

                String response =
                    ActiveDirectoryService.getAdOnlyUsersJson(
                        ctx,
                        conn
                    );

                addCors(exchange);
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                e.printStackTrace();

                addCors(exchange);
                sendJson(
                    exchange,
                    500,
                    "{\"result\":\"ng\",\"message\":\"ADのみユーザー取得失敗\"}"
                );

            } finally {
                ActiveDirectoryService.close(ctx);

                if (conn != null) {
                    try { conn.close(); } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static boolean dbUserExists(
            Connection conn,
            String id) throws SQLException {

        String sql =
            "SELECT COUNT(*) " +
            "FROM AJ職員基本 " +
            "WHERE TRIM(職員番号) = ?";

        try (PreparedStatement ps =
                 conn.prepareStatement(sql)) {

            ps.setString(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }

        return false;
    }

    private static String getShozokuKana(
            Connection conn,
            String year,
            String groupCd) throws SQLException {

        String sql =
            "SELECT 所属名称カナ " +
            "FROM BASE.AJ所属名称 " +
            "WHERE TRIM(年度) = ? " +
            "AND TRIM(所属CD) = ?";

        try (PreparedStatement ps =
                 conn.prepareStatement(sql)) {

            ps.setString(1, year.trim());
            ps.setString(2, groupCd.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println(
                        "所属名称カナ:所属データなし スイドウ"
                    );
                    return "スイドウ";
                }

                String kana =
                    rs.getString("所属名称カナ");

                if (kana == null || kana.trim().isEmpty()) {
                    System.out.println(
                        "所属名称カナ:NULL/空 スイドウ"
                    );
                    return "スイドウ";
                }

                return kana.trim();
            }
        }
    }

    private static void insertUserTables(
            Connection conn,
            String id,
            String year,
            String groupCd,
            String tantoCd,
            String sei,
            String mei,
            String dateStart,
            String currentDate,
            String currentTime,
            String dataCreateUser,
            String dataUpdateUser,
            String dataUpdateType) throws Exception {

        /*
         * ここは画像中で AJ職員基本 / AJ職員基本拡張 / AJ職員基本氏名 /
         * AJ職員基本制御 / AJ職員基本戸籍氏名 / AJ職員 / AJ職員権限情報 /
         * AJ職員拡張 / AJ担当者 / AJ担当者拡張 への INSERT が確認できた箇所。
         *
         * 実DB定義の列数・NOT NULL・固定値依存が強いため、
         * 復元版では「画像で確認できた列」を中心に安全な最小INSERTとした。
         * 本番へ戻す場合は元表定義に合わせて各列を確認すること。
         */

        String sql1 =
            "INSERT INTO AJ職員基本 " +
            "(職員番号, 採用日, データ作成日, データ作成時刻, " +
            "データ作成者, データ更新日, データ更新時刻, データ更新者, データ更新種別) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql1)) {
            ps.setString(1, id);
            ps.setString(2, dateStart);
            ps.setString(3, currentDate);
            ps.setString(4, currentTime);
            ps.setString(5, dataCreateUser);
            ps.setString(6, currentDate);
            ps.setString(7, currentTime);
            ps.setString(8, dataUpdateUser);
            ps.setString(9, dataUpdateType);

            int count = ps.executeUpdate();
            System.out.println("AJ職員基本 INSERT件数 = " + count);
        }

        String sql2 =
            "INSERT INTO AJ職員基本拡張 (職員番号) VALUES (?)";

        try (PreparedStatement ps = conn.prepareStatement(sql2)) {
            ps.setString(1, id);
            int count = ps.executeUpdate();
            System.out.println("AJ職員基本拡張 INSERT件数 = " + count);
        }

        String sql3 =
            "INSERT INTO AJ職員基本氏名 " +
            "(職員番号, 有効期間開始日, 姓, 名, データ作成日, データ作成時刻, データ作成者, データ更新日, データ更新時刻, データ更新者, データ更新種別) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql3)) {
            ps.setString(1, id);
            ps.setString(2, dateStart);
            ps.setString(3, sei);
            ps.setString(4, mei);
            ps.setString(5, currentDate);
            ps.setString(6, currentTime);
            ps.setString(7, dataCreateUser);
            ps.setString(8, currentDate);
            ps.setString(9, currentTime);
            ps.setString(10, dataUpdateUser);
            ps.setString(11, dataUpdateType);

            int count = ps.executeUpdate();
            System.out.println("AJ職員基本氏名 INSERT件数 = " + count);
        }

        String sql4 =
            "INSERT INTO AJ職員基本制御 " +
            "(職員番号, 年度, 既定ログイン所属CD) " +
            "VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql4)) {
            ps.setString(1, id);
            ps.setString(2, year);
            ps.setString(3, groupCd);

            int count = ps.executeUpdate();
            System.out.println("AJ職員基本制御 INSERT件数 = " + count);
        }

        String sql5 =
            "INSERT INTO AJ担当者 " +
            "(年度, 職員番号, 担当CD, データ作成日, データ作成時刻, データ作成者, データ更新日, データ更新時刻, データ更新者, データ更新種別) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql5)) {
            ps.setString(1, year);
            ps.setString(2, id);
            ps.setString(3, tantoCd);
            ps.setString(4, currentDate);
            ps.setString(5, currentTime);
            ps.setString(6, dataCreateUser);
            ps.setString(7, currentDate);
            ps.setString(8, currentTime);
            ps.setString(9, dataUpdateUser);
            ps.setString(10, dataUpdateType);

            int count = ps.executeUpdate();
            System.out.println("AJ担当者 INSERT件数 = " + count);
        }

        // 画像上に存在した拡張表は、主キーのみの最小INSERT
        String[] extensionTables = {
            "AJ職員基本戸籍氏名",
            "AJ職員権限情報",
            "AJ職員拡張",
            "AJ担当者拡張"
        };

        for (String table : extensionTables) {
            try {
                String sql =
                    "INSERT INTO " + table +
                    " (職員番号) VALUES (?)";

                try (PreparedStatement ps =
                         conn.prepareStatement(sql)) {

                    ps.setString(1, id);
                    int count = ps.executeUpdate();

                    System.out.println(
                        table + " INSERT件数 = " + count
                    );
                }
            } catch (SQLException e) {
                // 原本の列定義が必要なため、復元版ではログして続行
                System.out.println(
                    table +
                    " の復元INSERTはDB定義確認が必要: " +
                    e.getMessage()
                );
            }
        }
    }

    private static String extractOuName(String adOuDn) {
        if (adOuDn != null && adOuDn.startsWith("OU=")) {
            int commaPos = adOuDn.indexOf(",");
            if (commaPos > 3) {
                return adOuDn.substring(3, commaPos);
            }
        }
        return "";
    }

    private static String readBody(HttpExchange exchange)
            throws IOException {

        InputStream is = exchange.getRequestBody();

        BufferedReader br =
            new BufferedReader(
                new InputStreamReader(is, "UTF-8")
            );

        StringBuilder body = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            body.append(line);
        }

        return body.toString();
    }

    // 画像の現行コードに合わせた簡易JSON文字列取得
    private static String getJsonValue(
            String json,
            String key) {

        String search =
            "\"" + key + "\":\"";

        int start = json.indexOf(search);

        if (start == -1) {
            return "";
        }

        start = start + search.length();

        int end = json.indexOf("\"", start);

        if (end == -1) {
            return "";
        }

        return json.substring(start, end);
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders()
            .set("Access-Control-Allow-Origin", "*");

        exchange.getResponseHeaders()
            .set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS"
            );

        exchange.getResponseHeaders()
            .set(
                "Access-Control-Allow-Headers",
                "Content-Type"
            );

        exchange.getResponseHeaders()
            .set(
                "Content-Type",
                "application/json; charset=UTF-8"
            );
    }

    private static void sendJson(
            HttpExchange exchange,
            int status,
            String json) throws IOException {

        byte[] bytes = json.getBytes("UTF-8");

        exchange.sendResponseHeaders(
            status,
            bytes.length
        );

        try (OutputStream os =
                 exchange.getResponseBody()) {

            os.write(bytes);
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }
}
