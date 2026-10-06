package com.example.matching.application.system;

import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.service.system.impl.AbilityTagAdmissionPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 已验证标签准入门面 — 供演化/市场等域在 application 层调用标签准入管线，
 * 避免 service 域间直接依赖形成模块循环（架构门禁：新增 service 域循环必须审批）。
 * <p>
 * 仅暴露 {@link AbilityTagAdmissionPipeline#admitVerifiedNewTag} 的委托，不做额外业务。
 */
@Service
@RequiredArgsConstructor
public class VerifiedAbilityTagAdmissionFacade {

    private final AbilityTagAdmissionPipeline admissionPipeline;

    public TagAdmissionResult admitVerifiedNewTag(TagAdmissionContext context, AiHarnessDecisionDTO decision) {
        return admissionPipeline.admitVerifiedNewTag(context, decision);
    }

    public TagAdmissionResult admitHumanApprovedNewTag(TagAdmissionContext context) {
        return admissionPipeline.admitHumanApprovedNewTag(context);
    }
}
