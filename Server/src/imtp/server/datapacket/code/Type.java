package imtp.server.datapacket.code;

/**
 * 类型
 *
 * @author NiZhanBo
 * @since 2025/01/25
 * @version 1.0.0
 */
public class Type {
    /** 默认 */
    public static final int DEFAULT = 0;
    /** 文本 */
    public static final int TEXT = 101;
    /** 文件 */
    public static final int FILE = 102;
    /** 原图 */
    public static final int IMAGE_ORIGINAL = 111;
    /** 缩略图 */
    public static final int IMAGE_THUMBNAIL = 112;
    /** 视频 */
    public static final int VIDEO = 113;
    /** 音频 */
    public static final int AUDIO = 114;
    /** 图标资源 */
    public static final int RESOURCE_ICON = 201;

    /** 文件传输连接 */
    public static final int FILE_LINK = 901;
    /** web服务连接 */
    public static final int WEB_LINK = 902;
    /** 考勤 */
    public static final int ATTENDANCE = 951;

    /** 普通用户账号 */
    public static final int ADMIN_ACCOUNT = 1001;
    /** 管理员账号 */
    public static final int USER_ACCOUNT = 1002;
    /** 账号ID */
    public static final int ACCOUNT_ID = 1011;
    /** 账号密码 */
    public static final int ACCOUNT_PASSWORD = 1012;
    /** 账号名 */
    public static final int ACCOUNT_NAME = 1013;
    /** 账号头像 */
    public static final int ACCOUNT_HEAD = 1014;
    /** 账号通讯录 */
    public static final int ACCOUNT_CONTACTS = 1021;
    /** 通讯录用户 */
    public static final int CONTACTS_USER = 1022;
    /** 通讯录群组 */
    public static final int CONTACTS_GROUP = 1023;
    /** 组织架构 */
    public static final int ORG_STRUCTURE = 1051;
    /** 账号邮箱 */
    public static final int ACCOUNT_EMAIL = 1101;
}