package net.momirealms.craftengine.proxy.common.network.resourcepack;

/**
 * 映射 Minecraft Resource Pack Status 的 wire ordinal 和过滤策略.
 */
public enum ResourcePackResult {

    SUCCESS_DOWNLOAD,
    DECLINED,
    FAILED_DOWNLOAD,
    ACCEPTED,
    DOWNLOADED,
    INVALID_URL,
    FAILED_RELOAD,
    DISCARDED,
    UNKNOWN;

    public boolean isInstalled() {
        return this == SUCCESS_DOWNLOAD;
    }

    // 判断该状态是否已经终止当前下载流程, 且不能用于过滤后续请求.
    public boolean invalidatesGeneration() {
        return switch (this) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED, UNKNOWN -> true;
            default -> false;
        };
    }

    // 将协议 ordinal 映射为状态, 未知值统一按 UNKNOWN fail-open.
    public static ResourcePackResult fromOrdinal(int ordinal) {
        ResourcePackResult[] values = values();
        if (ordinal < 0 || ordinal >= UNKNOWN.ordinal()) {
            return UNKNOWN;
        }
        return values[ordinal];
    }
}
