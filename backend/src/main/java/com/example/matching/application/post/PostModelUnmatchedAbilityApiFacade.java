package com.example.matching.application.post;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.post.api.PostModelUnmatchedBindRequest;
import com.example.matching.dto.post.api.UnmatchedAbilityDTO;
import com.example.matching.entity.post.PostModelUnmatchedAbility;
import com.example.matching.service.post.PostModelUnmatchedAbilityService;
import com.example.matching.service.post.PostModelVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 岗位模型未匹配能力 API 门面（M-07）
 */
@Service
@RequiredArgsConstructor
public class PostModelUnmatchedAbilityApiFacade {

    private final PostModelUnmatchedAbilityService unmatchedAbilityService;
    private final PostModelVersionService modelVersionService;

    public List<UnmatchedAbilityDTO> listByVersionId(Long versionId) {
        requireVersion(versionId);
        return unmatchedAbilityService.listByVersionId(versionId).stream()
                .map(unmatchedAbilityService::toDto)
                .toList();
    }

    public void bind(Long versionId, Long id, PostModelUnmatchedBindRequest request) {
        requireVersion(versionId);
        if (request == null || request.getTagId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "tagId 不能为空");
        }
        PostModelUnmatchedAbility record = unmatchedAbilityService.getById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "未匹配能力记录不存在: " + id);
        }
        if (!versionId.equals(record.getVersionId())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "未匹配能力记录不属于该版本");
        }
        unmatchedAbilityService.bind(id, request.getTagId());
    }

    public void ignore(Long versionId, Long id) {
        requireVersion(versionId);
        PostModelUnmatchedAbility record = unmatchedAbilityService.getById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "未匹配能力记录不存在: " + id);
        }
        if (!versionId.equals(record.getVersionId())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "未匹配能力记录不属于该版本");
        }
        unmatchedAbilityService.ignore(id);
    }

    private void requireVersion(Long versionId) {
        if (modelVersionService.getById(versionId) == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "岗位模型版本不存在: " + versionId);
        }
    }
}
