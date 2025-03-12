package imtp.server.datapacket.code;

/**
 * 方式
 *
 * @author NiZhanBo
 * @since 2025/01/25
 * @version 1.0.0
 */
public class Way {
    /** 默认 */
    public static final int DEFAULT = 0;
    /** 正常响应 */
    public static final int ANSWER_OK = 101;
    /** 服务器出错 */
    public static final int ANSWER_ERROR = 102;
    /** 连接未验证 */
    public static final int ANSWER_NOT_VERIFY = 103;
    /** 访问服务越界 */
    public static final int ANSWER_NOT_ACCESS = 104;
    /** 心跳包 */
    public static final int HEART_BEAT = 111;
    /** DH公钥 */
    public static final int DH_KEY = 112;
    /** Token验证 */
    public static final int TOKEN_VERIFY = 113;
    /** 建立连接 */
    public static final int BUILD_LINK = 123;

    /** 发送数据 */
    public static final int SEND_DATA = 201;
    /** 请求数据 */
    public static final int REQUEST_DATA = 202;
    /** 修改数据 */
    public static final int CHANGE_DATA = 203;
    /** 重置数据 */
    public static final int RESET_DATA = 204;

    /** 登录 */
    public static final int LOGIN = 1001;
    /** 登出 */
    public static final int LOGOUT = 1002;
}