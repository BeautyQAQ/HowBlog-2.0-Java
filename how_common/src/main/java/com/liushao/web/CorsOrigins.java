package com.liushao.web;

import java.util.Arrays;

/**
 * 跨域来源配置解析。
 *
 * @author huangshen
 */
public final class CorsOrigins {
    private static final String[] ANY = {"*"};

    private CorsOrigins() {
    }

    /**
     * 逗号分隔的来源列表；未配置或全部为空白时允许所有来源。
     */
    public static String[] allowedOrigins(String configured) {
        if (configured == null) {
            return ANY.clone();
        }
        String[] origins = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toArray(String[]::new);
        return origins.length == 0 ? ANY.clone() : origins;
    }
}
