package com.example.matching.controller.post;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.matching.common.result.R;
import com.example.matching.service.post.PostAbilityInspectionService;
import com.example.matching.vo.post.PostAbilityInspectionItemVO;
import com.example.matching.vo.post.PostAbilityInspectionPostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 岗位能力巡检（入库后的 AI 幻觉巡检台）。
 * <p>
 * 以岗位为单位展示岗位能力表（post_ability_model）的能力与风险标注。
 * 修改/删除能力请复用 /api/post/ability-model 接口，本接口只读聚合。
 */
@Tag(name = "岗位能力巡检", description = "入库后的岗位能力 AI 幻觉巡检：按岗位聚合能力与风险标注")
@RestController
@RequestMapping("/api/system/post-ability-inspection")
@RequiredArgsConstructor
public class PostAbilityInspectionController {

    private final PostAbilityInspectionService inspectionService;

    @Operation(summary = "全岗位巡检汇总", description = "返回岗位数、能力总数、风险能力数、高风险数、AI 来源能力数")
    @GetMapping("/summary")
    public R<Map<String, Long>> summary() {
        return R.ok(inspectionService.summary());
    }

    @Operation(summary = "分页查询岗位聚合列表", description = "按岗位聚合岗位能力表数据，返回能力总数、风险能力数、AI 来源能力数")
    @GetMapping("/posts")
    public R<IPage<PostAbilityInspectionPostVO>> pagePosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "岗位名称/编码关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "只看有风险能力的岗位") @RequestParam(required = false) Boolean onlyRisky,
            @Parameter(description = "只看包含 AI 来源能力的岗位") @RequestParam(required = false) Boolean onlyAi) {
        return R.ok(inspectionService.pagePosts(keyword, onlyRisky, onlyAi, current, size));
    }

    @Operation(summary = "查询岗位能力明细（含风险标注）", description = "返回单个岗位全部能力，每条带风险标签、治理准入判定、提取台账状态与证据")
    @GetMapping("/{postId}/abilities")
    public R<List<PostAbilityInspectionItemVO>> listAbilities(
            @Parameter(description = "岗位ID", required = true) @PathVariable Long postId) {
        return R.ok(inspectionService.listAbilities(postId));
    }
}
