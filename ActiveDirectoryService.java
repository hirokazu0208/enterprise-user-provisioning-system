import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.PartialResultException;
import javax.naming.directory.*;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveDirectoryService {

    /*
     * 画像から復元した現行系。
     * 接続先は環境依存のため環境変数 AD_LDAP_URL を優先。
     */
    private static final String LDAP_URL =
        System.getenv().getOrDefault("AD_LDAP_URL", "ldaps://I-ADS01.fukusui.walan:636");

    public static DirContext connect(String user, String password) throws Exception {
        Hashtable<String, String> env = new Hashtable<>();

        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, LDAP_URL);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, user);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put(Context.REFERRAL, "ignore");
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "5000");

        System.out.println("LDAP_URL = " + LDAP_URL);
        System.out.println("Active Directory 接続開始...");

        DirContext ctx = new InitialDirContext(env);

        // LDAPアクセス/BIND確認
        ctx.getAttributes("");

        System.out.println("Active Directory 接続成功");
        return ctx;
    }

    public static void close(DirContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
                System.out.println("Active Directory 接続終了");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 既存ユーザー検索
    public static void searchUser(
            DirContext ctx,
            String baseDn,
            String samAccountName) throws Exception {

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(new String[]{
            "distinguishedName",
            "sAMAccountName",
            "userPrincipalName",
            "displayName",
            "sn",
            "givenName"
        });

        String filter = "(&(objectClass=user)(sAMAccountName=" + escapeLdapFilter(samAccountName) + "))";
        NamingEnumeration<SearchResult> results = ctx.search(baseDn, filter, controls);

        if (!results.hasMore()) {
            System.out.println("対象ユーザが見つかりません");
            return;
        }

        try {
            while (results.hasMore()) {
                SearchResult result = results.next();
                Attributes attrs = result.getAttributes();
                System.out.println("DN = " + result.getNameInNamespace());
                System.out.println("sAMAccountName = " + attrs.get("sAMAccountName"));
                System.out.println("userPrincipalName = " + attrs.get("userPrincipalName"));
                System.out.println("displayName = " + attrs.get("displayName"));
                System.out.println("sn = " + attrs.get("sn"));
                System.out.println("givenName = " + attrs.get("givenName"));
            }
        } catch (PartialResultException e) {
            System.out.println("Referral検出済・検索結果取得済");
        } finally {
            try { results.close(); } catch (Exception ignored) {}
        }
    }

    public static boolean userExists(
            DirContext ctx,
            String baseDn,
            String samAccountName) throws Exception {

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(new String[]{"sAMAccountName"});

        String filter =
            "(&(objectClass=user)(sAMAccountName=" +
            escapeLdapFilter(samAccountName) +
            "))";

        NamingEnumeration<SearchResult> results = null;
        boolean found = false;

        try {
            results = ctx.search(baseDn, filter, controls);

            while (results.hasMore()) {
                SearchResult result = results.next();
                System.out.println("AD存在確認でユーザ発見 = " + result.getName());
                found = true;
                break;
            }
        } catch (PartialResultException e) {
            System.out.println("Referralを検出しました");
        } finally {
            if (results != null) {
                try { results.close(); } catch (Exception ignored) {}
            }
        }

        System.out.println("ADユーザ存在確認結果 = " + found);
        return found;
    }

    public static String createUser(
            DirContext ctx,
            String targetOu,
            String userId,
            String sei,
            String mei) throws Exception {

        String displayName = sei + " " + mei;
        String userDn = "CN=" + Rdn.escapeValue(displayName) + "," + targetOu;

        System.out.println("AD表示名 = " + displayName);

        BasicAttributes attrs = new BasicAttributes(true);

        BasicAttribute objectClass = new BasicAttribute("objectClass");
        objectClass.add("top");
        objectClass.add("person");
        objectClass.add("organizationalPerson");
        objectClass.add("user");
        attrs.put(objectClass);

        attrs.put("sAMAccountName", userId);
        attrs.put("userPrincipalName", userId + "@fukusui.walan");
        attrs.put("sn", sei);
        attrs.put("givenName", mei);
        attrs.put("displayName", displayName);
        attrs.put("cn", displayName);

        System.out.println("ADユーザ作成開始 DN = " + userDn);

        DirContext userContext = ctx.createSubcontext(userDn, attrs);
        userContext.close();

        System.out.println("ADユーザ作成成功");
        return userDn;
    }

    public static void setPassword(
            DirContext ctx,
            String userDn,
            String password) throws Exception {

        // Active Directory unicodePwd は "password" を UTF-16LE にする必要あり
        String quotedPassword = "\"" + password + "\"";
        byte[] passwordBytes = quotedPassword.getBytes(StandardCharsets.UTF_16LE);

        BasicAttribute passwordAttr =
            new BasicAttribute("unicodePwd", passwordBytes);

        ModificationItem[] mods = {
            new ModificationItem(
                DirContext.REPLACE_ATTRIBUTE,
                passwordAttr
            )
        };

        System.out.println("setPassword開始 userDn = " + userDn);
        System.out.println("password文字数 = " + password.length());

        ctx.modifyAttributes(userDn, mods);

        System.out.println("AD初期パスワード設定成功");
    }

    public static void enableUser(
            DirContext ctx,
            String userDn) throws Exception {

        Attributes attrs = ctx.getAttributes(
            userDn,
            new String[]{"userAccountControl"}
        );

        int currentValue =
            Integer.parseInt(
                attrs.get("userAccountControl").get().toString()
            );

        // ACCOUNTDISABLE=0x0002 を外し、NORMAL_ACCOUNT(0x0200) と DONT_EXPIRE_PASSWORD(0x10000)
        int newValue = (currentValue & ~0x0002 & ~0x0020) | 0x0200 | 0x10000;

        ModificationItem[] mods = {
            new ModificationItem(
                DirContext.REPLACE_ATTRIBUTE,
                new BasicAttribute(
                    "userAccountControl",
                    String.valueOf(newValue)
                )
            )
        };

        ctx.modifyAttributes(userDn, mods);

        System.out.println("ADアカウント有効化成功/パスワード無期限設定成功");
        System.out.println("userAccountControl = " + newValue);
    }

    public static List<Map<String, String>> getOuList(
            DirContext ctx,
            String searchBaseDn) throws Exception {

        List<Map<String, String>> ouList = new ArrayList<>();

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        controls.setReturningAttributes(new String[]{
            "ou",
            "distinguishedName"
        });

        String filter = "(objectClass=organizationalUnit)";
        NamingEnumeration<SearchResult> results = null;

        try {
            results = ctx.search(searchBaseDn, filter, controls);

            while (results.hasMore()) {
                SearchResult result = results.next();
                Attributes attrs = result.getAttributes();

                String ouName = "";
                String dn = "";

                if (attrs.get("ou") != null) {
                    ouName = attrs.get("ou").get().toString();
                }

                String relativeDn = result.getName();
                if (relativeDn != null && !relativeDn.isEmpty()) {
                    dn = relativeDn + "," + searchBaseDn;
                }

                Map<String, String> item = new HashMap<>();
                item.put("name", ouName);
                item.put("dn", dn);
                ouList.add(item);

                System.out.println("OU取得 name=" + ouName + " / dn=" + dn);
            }
        } finally {
            if (results != null) {
                try { results.close(); } catch (Exception ignored) {}
            }
        }

        return ouList;
    }

    public static boolean deleteUser(
            DirContext ctx,
            String baseDn,
            String samAccountName) throws Exception {

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(new String[]{"sAMAccountName"});

        String filter =
            "(&(objectClass=user)(sAMAccountName=" +
            escapeLdapFilter(samAccountName) +
            "))";

        NamingEnumeration<SearchResult> results = null;

        try {
            results = ctx.search(baseDn, filter, controls);

            if (!results.hasMore()) {
                System.out.println("AD削除対象ユーザーなし: " + samAccountName);
                return false;
            }

            SearchResult result = results.next();
            String userDn = result.getName() + "," + baseDn;

            System.out.println("AD削除対象DN = " + userDn);
            ctx.destroySubcontext(userDn);
            System.out.println("ADユーザ削除成功 = " + samAccountName);

            return true;
        } finally {
            if (results != null) {
                try { results.close(); } catch (Exception ignored) {}
            }
        }
    }

    public static void addUserToGroup(
            DirContext ctx,
            String userDn,
            String groupDn) throws Exception {

        ModificationItem[] mods = {
            new ModificationItem(
                DirContext.ADD_ATTRIBUTE,
                new BasicAttribute("member", userDn)
            )
        };

        ctx.modifyAttributes(groupDn, mods);

        System.out.println(
            "ADグループ追加成功 userDn=" + userDn +
            " groupDn=" + groupDn
        );
    }

    public static String findGroupDn(
            DirContext ctx,
            String baseDn,
            String groupName) throws Exception {

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(new String[]{"distinguishedName"});

        String filter =
            "(&(objectClass=group)(cn=" +
            escapeLdapFilter(groupName) +
            "))";

        NamingEnumeration<SearchResult> results =
            ctx.search(baseDn, filter, controls);

        try {
            if (results.hasMore()) {
                SearchResult result = results.next();
                Attribute dnAttr =
                    result.getAttributes().get("distinguishedName");

                if (dnAttr != null) {
                    String groupDn = dnAttr.get().toString();
                    System.out.println("グループDN取得成功 = " + groupDn);
                    return groupDn;
                }
            }
        } finally {
            try { results.close(); } catch (Exception ignored) {}
        }

        throw new Exception("ADグループが見つかりません: " + groupName);
    }

    public static String renameUserCn(
            DirContext ctx,
            String oldUserDn,
            String targetOu,
            String displayName) throws Exception {

        String newUserDn =
            "CN=" + Rdn.escapeValue(displayName) + "," + targetOu;

        System.out.println("ADユーザーCN変更前 = " + oldUserDn);
        System.out.println("ADユーザーCN変更後 = " + newUserDn);

        ctx.rename(oldUserDn, newUserDn);

        System.out.println("ADユーザーCN変更成功");
        return newUserDn;
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    public static String getUsersJson(DirContext ctx) throws Exception {
        String baseDn = "OU=水道局_本番,DC=FUKUSUI,DC=WALAN";

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(
            new String[]{"sAMAccountName", "displayName"}
        );

        NamingEnumeration<SearchResult> results =
            ctx.search(
                baseDn,
                "(&(objectCategory=person)(objectClass=user))",
                controls
            );

        StringBuilder json = new StringBuilder();
        json.append("[");
        boolean first = true;

        try {
            while (results.hasMore()) {
                SearchResult result = results.next();
                Attributes attrs = result.getAttributes();

                String id = "";
                String name = "";

                Attribute idAttr = attrs.get("sAMAccountName");
                if (idAttr != null) {
                    id = idAttr.get().toString();
                }

                Attribute nameAttr = attrs.get("displayName");
                if (nameAttr != null) {
                    name = nameAttr.get().toString();
                }

                if (!first) {
                    json.append(",");
                }

                json.append("{")
                    .append("\"id\":\"")
                    .append(escapeJson(id))
                    .append("\",")
                    .append("\"name\":\"")
                    .append(escapeJson(name))
                    .append("\"}");

                first = false;
            }
        } finally {
            try { results.close(); } catch (Exception ignored) {}
        }

        json.append("]");
        return json.toString();
    }

    // AD-DB差分取得処理
    public static String getAdOnlyUsersJson(
            DirContext ctx,
            Connection conn) throws Exception {

        String baseDn = "OU=水道局_本番,DC=FUKUSUI,DC=WALAN";

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(
            new String[]{"sAMAccountName", "displayName"}
        );

        NamingEnumeration<SearchResult> results =
            ctx.search(
                baseDn,
                "(&(objectCategory=person)(objectClass=user))",
                controls
            );

        StringBuilder json = new StringBuilder();
        json.append("[");
        boolean first = true;

        String existsSql =
            "SELECT COUNT(*) FROM AJ職員基本 WHERE TRIM(職員番号) = ?";

        try (PreparedStatement ps = conn.prepareStatement(existsSql)) {
            while (results.hasMore()) {
                SearchResult result = results.next();
                Attributes attrs = result.getAttributes();

                String id = attr(attrs, "sAMAccountName");
                String name = attr(attrs, "displayName");

                ps.setString(1, id);
                boolean dbExists = false;
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        dbExists = rs.getInt(1) > 0;
                    }
                }

                if (!dbExists) {
                    if (!first) json.append(",");
                    json.append("{")
                        .append("\"id\":\"").append(escapeJson(id)).append("\",")
                        .append("\"name\":\"").append(escapeJson(name)).append("\"")
                        .append("}");
                    first = false;
                }
            }
        } finally {
            try { results.close(); } catch (Exception ignored) {}
        }

        json.append("]");
        return json.toString();
    }

    private static String attr(Attributes attrs, String key) throws Exception {
        Attribute a = attrs.get(key);
        return a == null ? "" : a.get().toString();
    }

    // LDAP filter minimal escaping
    private static String escapeLdapFilter(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\5c")
            .replace("*", "\\2a")
            .replace("(", "\\28")
            .replace(")", "\\29")
            .replace("\u0000", "\\00");
    }
}
