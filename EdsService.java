import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NameClassPair;
import javax.naming.directory.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Hashtable;

public class EdsService {

    public static DirContext connect(
            String host,
            int port,
            String bindDn,
            String password) throws Exception {

        Hashtable<String, String> env = new Hashtable<>();

        env.put(
            Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory"
        );

        env.put(
            Context.PROVIDER_URL,
            "ldap://" + host + ":" + port
        );

        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, bindDn);
        env.put(Context.SECURITY_CREDENTIALS, password);

        DirContext ctx = new InitialDirContext(env);

        System.out.println("EDS LDAP接続成功 host=" + host);
        return ctx;
    }

    // EDSユーザー存在確認
    public static boolean userExists(
            DirContext ctx,
            String employeeNumber) throws Exception {

        String baseDn =
            "cn=PersonnelInfo," +
            "cn=OrganizationInfo," +
            "selfGovernCode=0000000001," +
            "c=JP";

        String filter =
            "(employeeNumber=" + employeeNumber + ")";

        NamingEnumeration<SearchResult> results =
            ctx.search(baseDn, filter, null);

        try {
            return results.hasMore();
        } finally {
            results.close();
        }
    }

    // EDS年度ノード存在確認
    public static boolean yearNodeExists(
            DirContext ctx,
            String yearDn) {

        try {
            ctx.getAttributes(yearDn);
            return true;
        } catch (javax.naming.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toBase64(String value) {
        return Base64.getEncoder().encodeToString(
            value.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String toDoubleBase64(String value) {
        return toBase64(toBase64(value));
    }

    // 画像上では未完成のLDIF生成メソッド
    public static String createAdd1Ldif(
            String id,
            String sei,
            String mei,
            String kanaSei,
            String kanaMei,
            String adoptionDay) {
        return "";
    }

    public static String createAdd2Ldif(
            String id,
            String year) {
        return "";
    }

    public static String createDelete2Ldif(
            String id,
            String year) {
        return "dn: cn=\"" + year + "\"" +
            ",employeeNumber=\"" + id + "\"" +
            ",cn=\"PersonnelInfo\"" +
            ",cn=\"OrganizationInfo\"" +
            ",selfGovernCode=\"0000000001\"" +
            ",c=\"JP\"\r\n" +
            "changetype: delete\r\n";
    }

    // EDS年度ノードDN生成
    public static String createYearDn(
            String id,
            String year) {

        return "cn=" + year +
            ",employeeNumber=" + id +
            ",cn=PersonnelInfo" +
            ",cn=OrganizationInfo" +
            ",selfGovernCode=0000000001" +
            ",c=JP";
    }

    // EDS 親ユーザー直下の子ノード確認
    public static String findYearNode(
            DirContext ctx,
            String parentDn) throws Exception {

        System.out.println("===== EDS子ノード一覧 =====");
        System.out.println("親DN = " + parentDn);

        NamingEnumeration<NameClassPair> results =
            ctx.list(parentDn);

        try {
            while (results.hasMore()) {
                NameClassPair item = results.next();
                String childName = item.getName();

                System.out.println(
                    "子ノード = " + childName +
                    " / クラス = " + item.getClassName()
                );

                if (childName.startsWith("cn=")) {
                    return childName;
                }
            }
        } finally {
            results.close();
        }

        return null;
    }

    public static void deleteNode(
            DirContext ctx,
            String dn) throws Exception {

        System.out.println("EDS削除開始 DN = " + dn);
        ctx.destroySubcontext(dn);
        System.out.println("EDS削除成功 DN = " + dn);
    }

    public static String createUserDn(String id) {
        return "employeeNumber=" + id +
            ",cn=PersonnelInfo" +
            ",cn=OrganizationInfo" +
            ",selfGovernCode=0000000001" +
            ",c=JP";
    }

    public static void deleteUser(
            String host,
            String id) throws Exception {

        String edsUser = System.getenv("EDS_USER");
        String edsPassword = System.getenv("EDS_PASSWORD");
        DirContext ctx = null;

        try {
            ctx = connect(
                host,
                389,
                edsUser,
                edsPassword
            );

            boolean exists = userExists(ctx, id);

            if (!exists) {
                System.out.println("EDSユーザーは存在しません: " + id);
                return;
            }

            String parentDn = createUserDn(id);
            String yearRdn = findYearNode(ctx, parentDn);

            if (yearRdn != null) {
                String yearDn = yearRdn + "," + parentDn;
                System.out.println("EDS年度ノード削除開始 DN = " + yearDn);
                deleteNode(ctx, yearDn);
                System.out.println("EDS年度ノード削除完了");
            } else {
                System.out.println("EDS年度ノードなし");
            }

            System.out.println("EDS親ユーザー削除開始 DN = " + parentDn);
            deleteNode(ctx, parentDn);
            System.out.println("EDS親ユーザー削除完了");

        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    public static void createUser(
            String host,
            String id,
            String sei,
            String mei,
            String kanaSei,
            String kanaMei,
            String adoptionDay,
            String year) throws Exception {

        String edsUser = System.getenv("EDS_USER");
        String edsPassword = System.getenv("EDS_PASSWORD");
        DirContext ctx = null;

        try {
            ctx = connect(
                host,
                389,
                edsUser,
                edsPassword
            );

            boolean exists = userExists(ctx, id);

            if (exists) {
                System.out.println("EDSユーザーは既に存在します: " + id);
                return;
            }

            String parentDn = createUserDn(id);
            System.out.println("EDS親ユーザー作成DN = " + parentDn);

            BasicAttributes attrs = new BasicAttributes(true);

            BasicAttribute objectClass =
                new BasicAttribute("objectClass");

            objectClass.add("top");
            objectClass.add("person");
            objectClass.add("organizationalPerson");
            objectClass.add("inetOrgPerson");
            objectClass.add("personnelBase");
            attrs.put(objectClass);

            String fullName = sei + " " + mei;
            attrs.put("cn", fullName);
            attrs.put("sn", sei);
            attrs.put("givenName", mei);

            attrs.put(
                new BasicAttribute("sn;phonetic", kanaSei)
            );
            attrs.put(
                new BasicAttribute("givenName;phonetic", kanaMei)
            );

            attrs.put("employeeNumber", id);
            attrs.put("uid", id);

            attrs.put(
                new BasicAttribute("cooperationUid1", id)
            );

            attrs.put(
                new BasicAttribute("systemFlag;0", "true")
            );
            attrs.put(
                new BasicAttribute("systemFlag;1", "true")
            );

            attrs.put("certificationCode", "PS");
            attrs.put("adoptionDay", adoptionDay);

            System.out.println("EDS親ユーザー作成開始");

            DirContext created = ctx.createSubcontext(parentDn, attrs);
            created.close();

            System.out.println("EDS親ユーザー作成成功");

            createYearNode(ctx, id, year);

            System.out.println("EDSユーザー追加完了: " + id);

        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    public static void createYearNode(
            DirContext ctx,
            String id,
            String year) throws Exception {

        String parentDn = createUserDn(id);
        String yearDn = "cn=" + year + "," + parentDn;

        System.out.println("EDS年度ノード作成DN = " + yearDn);

        BasicAttributes attrs = new BasicAttributes(true);

        BasicAttribute objectClass =
            new BasicAttribute("objectClass");

        objectClass.add("top");
        objectClass.add("branch");

        attrs.put(objectClass);
        attrs.put("cn", year);

        System.out.println("EDS年度ノード作成開始");

        DirContext created =
            ctx.createSubcontext(yearDn, attrs);

        created.close();

        System.out.println("EDS年度ノード作成成功");
    }
}
