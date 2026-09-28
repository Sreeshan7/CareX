package com.carex.leave.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Informational only (audit/timeline icon). NEVER used for authorization. */
public enum ClientChannel {
    WEB, CHAT, VOICE, SYSTEM;

    public static final String HEADER = "X-Client-Channel";

    /** Resolves the channel of the current HTTP request; WEB when absent/invalid, SYSTEM outside requests. */
    public static ClientChannel current() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return SYSTEM;
        }
        String h = req.getHeader(HEADER);
        if (h == null) {
            return WEB;
        }
        return switch (h.trim().toUpperCase()) {
            case "CHAT" -> CHAT;
            case "VOICE" -> VOICE;
            default -> WEB;
        };
    }

    /** Channel to store on a leave request (DB allows WEB|CHAT|VOICE only). */
    public ClientChannel forRequest() {
        return this == SYSTEM ? WEB : this;
    }

    public static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    public static String currentIp() {
        HttpServletRequest req = currentRequest();
        return req == null ? null : req.getRemoteAddr();
    }
}
