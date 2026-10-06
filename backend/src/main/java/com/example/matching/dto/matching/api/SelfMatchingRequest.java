package com.example.matching.dto.matching.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 员工自助发起匹配请求。
 *
 * <p>只包含岗位列表：人员范围由服务端按当前登录身份固定为本人，
 * 不接受 empId 入参，避免员工借该入口对他人发起匹配。</p>
 */
@Data
@Schema(description = "员工自助发起匹配请求：选择要试配的岗位")
public class SelfMatchingRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "请至少选择一个岗位")
    @Schema(description = "要试配的岗位ID列表", example = "[20001, 20002]")
    private List<Long> postIds;
}
