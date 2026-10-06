package com.example.matching.dto.post;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 「展开调整」保存：人工修改岗位名 / 描述 / 能力清单。
 * <p>
 * 默认流程不需要填写任何字段——本请求只在人工主动调整后才使用。
 * 传入的 abilities 会**整体替换**原有清单（前端抽屉里拿到的就是全量清单）。
 */
public record PostTrendPayloadUpdateRequest(
        String postName,
        String postDescription,
        List<AbilityItem> abilities
) implements Serializable {

    public record AbilityItem(
            String abilityName,
            Long tagId,
            Integer suggestedLevel,
            BigDecimal suggestedWeight,
            Integer isCore
    ) implements Serializable {
    }
}
