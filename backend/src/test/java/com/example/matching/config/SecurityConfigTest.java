package com.example.matching.config;

import com.example.matching.security.JwtTokenProvider;
import com.example.matching.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, SecurityConfigTest.TestBeans.class, SecurityConfigTest.TestController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityProperties securityProperties;

    @Test
    void websocketInterviewPathDoesNotRequireJwt() throws Exception {
        mockMvc.perform(get("/ws/interview/{sessionId}", 9L))
                .andExpect(status().isOk());
    }

    @Test
    void protectedApiStillRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/protected"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void ordinaryUserCannotMutateAgentMemory() throws Exception {
        mockMvc.perform(put("/api/governance/agent-memory/{id}", 9L))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowStandardHeaders() {
        String[] allowed = securityProperties.getAllowedHeaders().split(",");
        assertThat(allowed).contains("Authorization", "Content-Type", "X-Requested-With", "X-Trace-Id", "Idempotency-Key");
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotReadEmployeePage() throws Exception {
        mockMvc.perform(get("/api/employee/page"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotWriteAbilityGovernance() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/ability/governance/change-level")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "EMPLOYEE:READ")
    void adminCanReadEmployeePage() throws Exception {
        mockMvc.perform(get("/api/employee/page"))
                .andExpect(status().isOk());
    }

    /* ============ 岗位体系读写分离（越权写修复） ============
     * 修复前 /api/post/** 用无限定方法的 hasAuthority("POST:READ") 覆盖，
     * 只持 POST:READ 的 HR 可以新增/修改/删除岗位。 */

    @Test
    @WithMockUser(authorities = "POST:READ")
    void hrWithReadOnlyCannotCreateOrUpdatePost() throws Exception {
        mockMvc.perform(post("/api/post").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/post/{id}", 1L).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "POST:READ")
    void hrWithReadOnlyCanStillReadPost() throws Exception {
        mockMvc.perform(get("/api/post/page")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "POST:MANAGE")
    void postArchitectCanCreatePost() throws Exception {
        mockMvc.perform(post("/api/post").contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    /* ============ 人员档案读写分离（越权写修复） ============
     * 修复前 /api/employee/** 同样无方法限定，员工仅凭 ASSESSMENT:SELF
     * 即可调用 PUT /{id}/status 或 DELETE /{id} 改动他人档案。 */

    @Test
    @WithMockUser(authorities = "ASSESSMENT:SELF")
    void employeeCannotMutateEmployeeProfile() throws Exception {
        mockMvc.perform(put("/api/employee/{id}/status", 1L)
                        .contentType("application/json").content("{\"status\":0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/employee/{id}", 1L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ASSESSMENT:SELF")
    void employeeCanStillReadOwnProfile() throws Exception {
        mockMvc.perform(get("/api/employee/me")).andExpect(status().isOk());
    }

    /* ============ 知识图谱归属 AI 配置管理员（权限配反修复） ============
     * 修复前 /api/kg/** 用 POST:READ 放行，而 AI_CONFIG_MANAGER 只持有 AI:CONFIG：
     * 结果是图谱页面（/kg/workbench）接口全部 403，HR 反而能调用图谱写接口。 */

    @Test
    @WithMockUser(authorities = "POST:MANAGE")
    void jobArchitectCanUseKnowledgeGraph() throws Exception {
        // 2026-09-04：/api/kg/** 从 AI:CONFIG 改挂 POST:MANAGE（知识图谱属岗位知识底座）
        mockMvc.perform(get("/api/kg/panorama")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "AI:CONFIG")
    void platformAdminCannotBrowseBusinessKnowledgeGraph() throws Exception {
        // AI:CONFIG 收窄为「平台 AI 基础设施」：不再能浏览业务知识图谱
        mockMvc.perform(get("/api/kg/panorama")).andExpect(status().isForbidden());
        // 但 Agent 记忆（平台侧）仍归 AI:CONFIG
        mockMvc.perform(get("/api/kg/memory-graph")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ASSESSMENT:MANAGE")
    void hrCanOnlyOpenEmployeeSideOfGraph() throws Exception {
        // 人员/岗位分域：HR 只能取员工中心的图
        mockMvc.perform(get("/api/kg/employee/{id}", 7L)).andExpect(status().isOk());
        mockMvc.perform(get("/api/kg/post/{id}", 7L)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/kg/panorama")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ASSESSMENT:MANAGE")
    void hrCannotOperateGraphMaintenance() throws Exception {
        // 运维（时间线 / 健康检查 / 重建）归岗位体系管理员
        mockMvc.perform(get("/api/kg/neo4j/health")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/kg/timeline")).andExpect(status().isForbidden());
    }

    /* ============ 匹配任务只属匹配运营（通知越权修复） ============
     * 修复前 `GET /api/matching/record/**` 同时放行 NOTIFICATION:SELF（本意是让员工读自己的
     * 通知与结果），而任务接口 /api/matching/record/task/** 正好落在该通配范围内 ——
     * 于是每个登录角色都能拉到**全量匹配任务**，前端顶栏铃铛据此同步，
     * 表现为「匹配完成通知所有角色都收到」。
     * 现在任务接口在该通配规则之前单独声明，只认 MATCHING:READ。 */

    @Test
    @WithMockUser(authorities = "NOTIFICATION:SELF")
    void employeeCannotReadMatchingTaskQueue() throws Exception {
        mockMvc.perform(get("/api/matching/record/task/page")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "MATCHING:READ")
    void hrCanReadMatchingTaskQueue() throws Exception {
        mockMvc.perform(get("/api/matching/record/task/page")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "NOTIFICATION:SELF")
    void employeeCanStillReadOwnMatchingRecords() throws Exception {
        // 收紧任务接口不应误伤「员工读本人匹配结果」这条既有放行
        mockMvc.perform(get("/api/matching/record/page")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "POST:READ")
    void hrCannotUseKnowledgeGraph() throws Exception {
        // 知识图谱属 AI 知识资产域；HR 的岗位只读权限不应触及它
        mockMvc.perform(get("/api/kg/panorama")).andExpect(status().isForbidden());
    }

    /* ============ 个人中心自助资料：登录即可，不需要管理端权限 ============
     * 顶栏右上角账号入口进「个人中心」改头像/手机号/邮箱，是所有角色共用的功能。
     * 风险点：管理端规则 GET/PUT `/api/system/user/*`（USER:MANAGE）里的 `*`
     * 会把 `/api/system/user/profile` 一起吞掉 —— 一旦顺序写反，
     * 员工改自己的资料就会 403。这里把「员工能改自己」和
     * 「员工仍读不到他人账号详情」两件事同时钉住。 */

    @Test
    @WithMockUser(authorities = "ASSESSMENT:SELF")
    void employeeCanReadOwnProfileForPersonalCenter() throws Exception {
        mockMvc.perform(get("/api/system/user/profile")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ASSESSMENT:SELF")
    void employeeCanUpdateOwnProfileForPersonalCenter() throws Exception {
        mockMvc.perform(put("/api/system/user/profile")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ASSESSMENT:SELF")
    void employeeCanUploadOwnAvatar() throws Exception {
        // 头像上传同为自助端点，不应被 USER:MANAGE 拦下
        mockMvc.perform(post("/api/system/user/profile/avatar")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ASSESSMENT:SELF")
    void employeeStillCannotReadOtherUsersDetail() throws Exception {
        // 自助端点放行不应把管理端通配一起放宽：读他人账号仍需 USER:MANAGE
        mockMvc.perform(get("/api/system/user/{id}", 9L)).andExpect(status().isForbidden());
    }

    /* ============ 匹配结果修改：不再以审批为前置（2026-09-04 需求变更） ============
     * 需求明确「匹配结果不需要审核」，HR 人工查看/修改匹配结果本身即为审核动作。
     * 修复前 PUT /api/matching/record/** 只认 MATCHING:APPROVE，导致能发起匹配
     * （MATCHING:EXECUTE）的 HR 反而改不了自己发起的结果，前端报 403。 */

    @Test
    @WithMockUser(authorities = "MATCHING:EXECUTE")
    void hrWithExecuteCanModifyMatchingResult() throws Exception {
        mockMvc.perform(put("/api/matching/record/{id}", 1L)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "MATCHING:APPROVE")
    void hrWithApproveCanStillModifyMatchingResult() throws Exception {
        mockMvc.perform(put("/api/matching/record/{id}", 1L)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "MATCHING:EXECUTE")
    void hrWithExecuteCannotDeleteMatchingResult() throws Exception {
        // 删除属更重的动作，仍只认 MATCHING:APPROVE，未一并放宽
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/matching/record/{id}", 1L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "MATCHING:READ")
    void matchingReadOnlyCannotModifyResult() throws Exception {
        // 只读权限仍不能改结果：权限边界未被放宽
        mockMvc.perform(put("/api/matching/record/{id}", 1L)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    /* ============ Agent 记忆：GET 权限补漏（2026-09-04） ============
     * 原先只声明了 POST/PUT/DELETE（AI:CONFIG），**GET 没有任何规则**，
     * 落入 anyRequest().authenticated() —— 任意登录用户（含员工）都能列出 Agent 记忆。
     * 前端只有 /system/agent-memory 一个入口（要求 AI:CONFIG），补上不会误伤业务角色。 */

    @Test
    @WithMockUser(authorities = "AI:CONFIG")
    void platformAdminCanReadAgentMemory() throws Exception {
        mockMvc.perform(get("/api/governance/agent-memory/page")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "NOTIFICATION:SELF")
    void employeeCannotReadAgentMemory() throws Exception {
        mockMvc.perform(get("/api/governance/agent-memory/page")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "POST:MANAGE")
    void jobArchitectCannotReadAgentMemory() throws Exception {
        // Agent 记忆是平台 AI 基础设施（AI 运行规则），岗位体系管理员也不该看到
        mockMvc.perform(get("/api/governance/agent-memory/page")).andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUnknownHeader() {
        String[] allowed = securityProperties.getAllowedHeaders().split(",");
        assertThat(allowed).doesNotContain("X-Admin-Bypass");
    }

    @RestController
    static class TestController {

        @GetMapping("/ws/interview/{sessionId}")
        ResponseEntity<String> websocketPath(@PathVariable Long sessionId) {
            return ResponseEntity.ok("ws-" + sessionId);
        }

        @GetMapping("/api/protected")
        ResponseEntity<String> protectedApi() {
            return ResponseEntity.ok("protected");
        }

        @PutMapping("/api/governance/agent-memory/{id}")
        ResponseEntity<String> updateAgentMemory(@PathVariable Long id) {
            return ResponseEntity.ok("updated-" + id);
        }

        @GetMapping("/api/governance/agent-memory/page")
        ResponseEntity<String> pageAgentMemory() {
            return ResponseEntity.ok("agent-memory-page");
        }

        @GetMapping("/api/employee/page")
        ResponseEntity<String> employeePage() {
            return ResponseEntity.ok("employee-page");
        }

        @GetMapping("/api/employee/me")
        ResponseEntity<String> employeeMe() {
            return ResponseEntity.ok("employee-me");
        }

        @PutMapping("/api/employee/{id}/status")
        ResponseEntity<String> updateEmployeeStatus(@PathVariable Long id, @RequestBody String body) {
            return ResponseEntity.ok("status-updated");
        }

        @DeleteMapping("/api/employee/{id}")
        ResponseEntity<String> archiveEmployee(@PathVariable Long id) {
            return ResponseEntity.ok("archived");
        }

        @GetMapping("/api/post/page")
        ResponseEntity<String> postPage() {
            return ResponseEntity.ok("post-page");
        }

        @PostMapping("/api/post")
        ResponseEntity<String> createPost(@RequestBody String body) {
            return ResponseEntity.ok("post-created");
        }

        @PutMapping("/api/post/{id}")
        ResponseEntity<String> updatePost(@PathVariable Long id, @RequestBody String body) {
            return ResponseEntity.ok("post-updated");
        }

        @GetMapping("/api/kg/panorama")
        ResponseEntity<String> kgPanorama() {
            return ResponseEntity.ok("kg-panorama");
        }

        @GetMapping("/api/kg/memory-graph")
        ResponseEntity<String> kgMemoryGraph() {
            return ResponseEntity.ok("kg-memory-graph");
        }

        @GetMapping("/api/kg/employee/{id}")
        ResponseEntity<String> kgEmployeeGraph(@PathVariable Long id) {
            return ResponseEntity.ok("kg-employee-" + id);
        }

        @GetMapping("/api/kg/post/{id}")
        ResponseEntity<String> kgPostGraph(@PathVariable Long id) {
            return ResponseEntity.ok("kg-post-" + id);
        }

        @GetMapping("/api/kg/neo4j/health")
        ResponseEntity<String> kgNeo4jHealth() {
            return ResponseEntity.ok("kg-neo4j-health");
        }

        @GetMapping("/api/kg/timeline")
        ResponseEntity<String> kgTimeline() {
            return ResponseEntity.ok("kg-timeline");
        }

        /* 个人中心自助端点的安全探针。
         * file 声明为可选：本类只验证「鉴权是否放行」，不解析上传内容 ——
         * 若为必填，未带文件时会得到 400，无法与 403 区分。真实控制器
         * （SysUserController#uploadMyAvatar）里 file 是必填的。 */
        @GetMapping("/api/system/user/profile")
        ResponseEntity<String> myProfile() {
            return ResponseEntity.ok("my-profile");
        }

        @PutMapping("/api/system/user/profile")
        ResponseEntity<String> updateMyProfile(@RequestBody String body) {
            return ResponseEntity.ok("my-profile-updated");
        }

        @PostMapping("/api/system/user/profile/avatar")
        ResponseEntity<String> uploadMyAvatar(
                @org.springframework.web.bind.annotation.RequestParam(value = "file", required = false) Object file) {
            return ResponseEntity.ok("my-avatar-uploaded");
        }

        @GetMapping("/api/system/user/{id}")
        ResponseEntity<String> userDetail(@PathVariable Long id) {
            return ResponseEntity.ok("user-" + id);
        }

        @GetMapping("/api/matching/record/task/page")
        ResponseEntity<String> matchingTaskPage() {
            return ResponseEntity.ok("matching-task-page");
        }

        @GetMapping("/api/matching/record/page")
        ResponseEntity<String> matchingRecordPage() {
            return ResponseEntity.ok("matching-record-page");
        }

        @PostMapping("/api/ability/governance/change-level")
        ResponseEntity<String> changeLevel(@RequestBody String body) {
            return ResponseEntity.ok("changed");
        }

        @PutMapping("/api/matching/record/{id}")
        ResponseEntity<String> modifyMatchingRecord(@PathVariable Long id, @RequestBody String body) {
            return ResponseEntity.ok("matching-record-modified");
        }

        @DeleteMapping("/api/matching/record/{id}")
        ResponseEntity<String> deleteMatchingRecord(@PathVariable Long id) {
            return ResponseEntity.ok("matching-record-deleted");
        }
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(mock(JwtTokenProvider.class), mock(UserDetailsServiceImpl.class),
                    mock(com.example.matching.security.TokenInvalidationService.class));
        }

        @Bean
        SecurityProperties securityProperties() {
            SecurityProperties props = new SecurityProperties();
            props.setAllowedOrigins("http://localhost:3000");
            props.setAllowedHeaders("Authorization,Content-Type,X-Requested-With,X-Trace-Id,Idempotency-Key");
            return props;
        }
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
