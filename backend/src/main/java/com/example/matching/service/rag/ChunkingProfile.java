package com.example.matching.service.rag;

/**
 * 按来源类型区分的分块配置。
 */
public record ChunkingProfile(String name, int chunkSize, int overlapSize, int minChunkLength) {

    public static final ChunkingProfile JD = new ChunkingProfile("JD", 600, 120, 30);
    public static final ChunkingProfile EVIDENCE = new ChunkingProfile("EVIDENCE", 400, 80, 30);
    public static final ChunkingProfile LEARNING = new ChunkingProfile("LEARNING", 700, 100, 30);
    public static final ChunkingProfile GENERAL = new ChunkingProfile("GENERAL", 800, 120, 30);

    /**
     * 按来源类型选择配置。
     */
    public static ChunkingProfile forSourceType(String sourceType) {
        if (sourceType == null) {
            return GENERAL;
        }
        return switch (sourceType) {
            case "POST_ABILITY_MODEL", "JD_IMPORT", "POST_PROTOTYPE" -> JD;
            case "CONTEST_EVIDENCE", "EMP_ABILITY" -> EVIDENCE;
            case "LEARNING_RESOURCE" -> LEARNING;
            default -> GENERAL;
        };
    }
}
