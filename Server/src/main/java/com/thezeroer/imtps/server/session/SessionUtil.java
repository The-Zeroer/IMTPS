package com.thezeroer.imtps.server.session;

import com.thezeroer.imtps.server.util.Tool;

import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 会话实用程序
 *
 * @author NiZhanBo
 * @since 2025/06/29
 * @version 1.0.0
 */
public class SessionUtil {

    public static String creationSessionId() throws NoSuchAlgorithmException {
        return Tool.getStringHashValue(System.currentTimeMillis() + UUID.randomUUID().toString(), "SHA-256");
    }
}
