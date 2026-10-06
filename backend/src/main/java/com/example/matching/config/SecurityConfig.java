package com.example.matching.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置。
 * <p>
 * 配置公开访问的端点和需要认证的端点。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityProperties securityProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, SecurityProperties securityProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.securityProperties = securityProperties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(securityProperties.getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        String[] allowedHeaders = (securityProperties.getAllowedHeaders() != null
                && !securityProperties.getAllowedHeaders().isBlank())
                ? securityProperties.getAllowedHeaders().split(",")
                : new String[]{"Authorization", "Content-Type", "X-Requested-With", "X-Trace-Id", "Idempotency-Key"};
        config.setAllowedHeaders(Arrays.asList(allowedHeaders));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(contentTypeOptions -> {})
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'")))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // 公开访问的端点
                        .requestMatchers(
                                "/api/system/user/login",
                                "/api/system/user/register",
                                // 【2026-09-04】自助注册（带邮箱验证码 + 角色授权码）及其验证码端点。
                                // 放行是必须的（注册时尚无登录态），安全性由三层承担：
                                //   ① 邮箱验证码（60s 冷却 / 5min 有效 / 错 5 次作废，见 RegisterEmailCodeService）；
                                //   ② 角色授权码（来自服务端环境变量，未配置的角色一律不可注册）；
                                //   ③ 现有注册 IP 限流（AuthRateLimitService.checkRegistrationAllowed）。
                                // 必须逐条精确声明，不要写成 /api/system/user/register/** ——
                                // 通配会连带放行未来挂在同一前缀下的其它端点。
                                "/api/system/user/register/email-code",
                                "/api/system/user/register/v2",
                                "/api/internal/market-jd/crawler-import",
                                // 【2026-09-04】爬虫对接文档 §9：本地爬虫主动轮询领取命令 / 上报心跳与结果。
                                // 与推送入口一致，一律 permitAll + Controller 内用 X-Crawler-Key 校验
                                // （密钥留空 → 401），从而不依赖主系统的 JWT 登录态。
                                // 必须逐条精确声明：通配 /api/internal/crawler/** 会吞掉未来新增的
                                // 同前缀管理端点，导致其绕过鉴权。
                                "/api/internal/crawler/commands",
                                "/api/internal/crawler/heartbeat",
                                "/api/internal/crawler/task-result",
                                // Container and platform liveness/readiness probe.
                                "/actuator/health",
                                // WebSocket 握手端点（由 InterviewWebSocketAuthInterceptor 单独鉴权）
                                "/ws/**",
                                // Swagger/Knife4j API文档
                                // 静态资源
                                "/static/**",
                                "/public/**",
                                "/favicon.ico"
                        ).permitAll()
                        // 其他所有请求需要认证
                        .requestMatchers("/api/system/role/**").hasAuthority("ROLE:MANAGE")
                        .requestMatchers("/actuator/**").hasAuthority("AUDIT:READ")
                        .requestMatchers("/api/system/dlq/**").hasAuthority("AUDIT:READ")
                        /*
                         * 【2026-09-04 修复越权】操作日志此前**没有任何显式规则**，
                         * 落到末尾的 anyRequest().authenticated() —— 也就是**任何已登录用户
                         * 都能翻全站操作日志**（含请求参数、响应片段、IP）。
                         * 它属于审计类只读接口，归 AUDIT:READ（= PLATFORM_ADMIN）。
                         */
                        .requestMatchers("/api/system/operation-log/**").hasAuthority("AUDIT:READ")
                        // 运行审计（token 消耗 / LLM 与工具响应时间 / Prompt 调用明细 / RAG 检索延迟）
                        // 走 /api/admin/runtime-audit/**，已被下面 /api/admin/** 的 AUDIT:READ 覆盖。
                        .requestMatchers("/api/admin/runtime-audit/**").hasAuthority("AUDIT:READ")
                        .requestMatchers("/api/matching/outbox/**").hasAuthority("MATCHING:READ")
                        .requestMatchers("/api/post/evolution/**").hasAuthority("POST:EVOLUTION")
                        // Agent 记忆属「平台 AI 基础设施」（AI 运行规则，不是业务知识），归 PLATFORM_ADMIN。
                        // 【2026-09-04 补漏】原先只声明了 POST/PUT/DELETE，**GET 没有任何规则**，
                        // 落入 anyRequest().authenticated() —— 即**任意登录用户**（含员工）都能列出 Agent 记忆。
                        // 前端只有 /system/agent-memory 一个入口（要求 AI:CONFIG），所以这里补上不会误伤业务角色。
                        .requestMatchers("/api/governance/agent-memory/**").hasAuthority("AI:CONFIG")
                        .requestMatchers(HttpMethod.GET, "/api/system/user/current").authenticated()
                        // 工作台指标：所有角色都有自己的工作台，KPI 环比与团队成员
                        // 由 Facade 按调用方角色收口（员工侧不返回他人档案），
                        // 因此这里只要求登录，不额外挂权限码。
                        .requestMatchers("/api/workbench/**").authenticated()
                        // 站内通知：发送侧只开放「提醒评估」，HR 视角按 EMPLOYEE:READ 收口；
                        // 查收/已读落在 anyRequest().authenticated()（服务层再做归属校验）。
                        .requestMatchers(HttpMethod.POST, "/api/notifications/assessment-reminder").hasAuthority("EMPLOYEE:READ")
                        .requestMatchers("/api/notifications/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/system/user/change-password").authenticated()
                        // 个人中心（自助维护头像/手机号/邮箱）：所有登录用户都能改**自己**的资料，
                        // 人员范围由 SecurityUtils 固定在服务端，URL 不接受 userId。
                        // ⚠️ 必须声明在下面 PUT/GET /api/system/user/*（USER:MANAGE）之前 ——
                        // 通配 `*` 会连 /profile 一起吞掉，否则员工改自己资料会 403。
                        .requestMatchers(HttpMethod.GET, "/api/system/user/profile").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/system/user/profile").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/system/user/profile/avatar").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/system/user/page").hasAuthority("USER:MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/system/user").hasAuthority("USER:MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/system/user/*").hasAuthority("USER:MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/system/user/*").hasAuthority("USER:MANAGE")
                        .requestMatchers(HttpMethod.DELETE, "/api/system/user/*").hasAuthority("USER:MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/matching/record/execute", "/api/matching/record/execute-async").hasAuthority("MATCHING:EXECUTE")
                        .requestMatchers(HttpMethod.POST, "/api/matching/record/*/retry-ai-scoring").hasAuthority("MATCHING:EXECUTE")
                        // 匹配结果修改/锁定/推送/撤回：属 HR 的匹配运营动作。
                        // 除 MATCHING:APPROVE 外同时放行 MATCHING:EXECUTE —— 需求已明确「匹配结果
                        // 不需要审批」（修改结果不再以审批为前置），HR 能发起匹配就应能维护自己
                        // 发起的匹配结果，否则会出现「能发起匹配、却改不了结果」的权限断点（403）。
                        // 两个权限码都只由 HR_SPECIALIST 持有（V147），未放宽到员工侧。
                        .requestMatchers(HttpMethod.PUT, "/api/matching/record/**")
                            .hasAnyAuthority("MATCHING:APPROVE", "MATCHING:EXECUTE")
                        .requestMatchers(HttpMethod.DELETE, "/api/matching/record/**").hasAuthority("MATCHING:APPROVE")
                        // ⚠️ 匹配任务只属于匹配运营（HR）。
                        // 【2026-09-04 修复】下面那条 `GET /api/matching/record/**` 同时放行了
                        // NOTIFICATION:SELF（本意是让员工读自己的通知/结果），而任务接口
                        // /api/matching/record/task/** 正好落在该通配范围内 —— 于是**每个登录角色**
                        // 都能拉到全量匹配任务，前端顶栏铃铛据此同步，表现为
                        // 「匹配完成通知所有角色都收到」。任务接口必须在通配规则之前单独声明。
                        .requestMatchers(HttpMethod.GET, "/api/matching/record/task/**").hasAuthority("MATCHING:READ")
                        .requestMatchers(HttpMethod.GET, "/api/matching/record/**").hasAnyAuthority("MATCHING:READ", "NOTIFICATION:SELF")
                        // 员工自助发起匹配：仅员工本人可调用，人员范围在服务端固定为本人。
                        // 必须声明在 /api/matching/** 之前，否则会先被 MATCHING:READ 拦下。
                        .requestMatchers(HttpMethod.POST, "/api/matching/record/self/execute").hasAnyAuthority("MATCHING:SELF", "MATCHING:EXECUTE")
                        .requestMatchers("/api/system/tag-governance/**").hasAuthority("POST:MANAGE")
                        .requestMatchers("/api/system/ai-model-config/**").hasAuthority("AI:CONFIG")
                        .requestMatchers("/api/matching/scoring-config/**").hasAuthority("MATCHING:CONFIG")
                        .requestMatchers("/api/matching/calibration/export").hasAuthority("MATCHING:CONFIG")
                        .requestMatchers("/api/admin/**").hasAuthority("AUDIT:READ")
                        .requestMatchers(HttpMethod.POST, "/api/system/source-weight/**").hasAuthority("MATCHING:CONFIG")
                        .requestMatchers(HttpMethod.PUT, "/api/system/source-weight/**").hasAuthority("MATCHING:CONFIG")
                        .requestMatchers(HttpMethod.POST, "/api/employee/ability/governance/**").hasAuthority("ASSESSMENT:MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/employee/ability/governance/**").hasAuthority("ASSESSMENT:MANAGE")
                        // 员工本人能力评估链路；PMS 仍属于后台分析，不向员工开放。
                        .requestMatchers("/api/employee/ability/pms/**").hasAnyAuthority("AI:CONFIG", "ASSESSMENT:MANAGE")
                        .requestMatchers("/api/employee/ability/**").hasAnyAuthority("ASSESSMENT:SELF", "ASSESSMENT:MANAGE")
                        // 员工本人评估需要读取“启用岗位”列表与岗位是否已配置能力模型；
                        // 只放开这两个只读入口，岗位维护仍要求 POST:MANAGE。
                        .requestMatchers(HttpMethod.GET, "/api/post/enabled").hasAnyAuthority("POST:READ", "ASSESSMENT:SELF")
                        .requestMatchers(HttpMethod.POST, "/api/post/ability-model/configured-post-ids").hasAnyAuthority("POST:READ", "ASSESSMENT:SELF")
                        .requestMatchers(HttpMethod.POST, "/api/kg/graph/**").hasAuthority("POST:MANAGE")
                        // 能力评估流程控制器使用复数前缀 /api/employees/{empId}/capability-assessments/**，
                        // 与单数 /api/employee/** 是两个不同路径，必须单独声明，否则会落到 anyRequest().authenticated()
                        // 仅靠方法级注解兜底。方法内部另有“仅本人”归属收口。
                        .requestMatchers("/api/employees/**").hasAnyAuthority("ASSESSMENT:SELF", "ASSESSMENT:MANAGE")
                        // 评估工作流以 workflowId 为入参的接口（详情/范围/测试/面试/报告/决策/重试）。
                        // 仅本人调用方的归属校验在 CapabilityAssessmentFacadeImpl 内按工作流归属人员二次收口；
                        // 人工等级确认/拒绝/策略重算为管理端动作，员工侧一律拒绝。
                        .requestMatchers("/api/capability-assessments/**").hasAnyAuthority("ASSESSMENT:SELF", "ASSESSMENT:MANAGE")
                        // 人员档案读写分离：GET 按各自数据范围放行（本人/全员的归属校验在 Facade 内收口）；
                        // 写操作（新增/更新/启用禁用/作废/导入）只允许 EMPLOYEE:READ 或 ASSESSMENT:MANAGE。
                        // 【已修复越权】原实现无限定方法，员工（仅 ASSESSMENT:SELF）可直调
                        // PUT /api/employee/{id}/status 或 DELETE /api/employee/{id} 越权改动他人档案。
                        // 注意 /api/employee/ability/** 已在前面单独声明，员工保存本人能力不受影响。
                        .requestMatchers(HttpMethod.GET, "/api/employee/**")
                            .hasAnyAuthority("EMPLOYEE:READ", "ASSESSMENT:SELF", "ASSESSMENT:MANAGE")
                        .requestMatchers("/api/employee/**")
                            .hasAnyAuthority("EMPLOYEE:READ", "ASSESSMENT:MANAGE")
                        // 学习成果提交 / HR 复核（闭环设计 P4）：员工侧只能提交与查本人记录，
                        // 复核类动作在 Controller 内用 SelfScopeSupport.assertManagementOnly 二次收口。
                        .requestMatchers("/api/learning-outcomes/**")
                            .hasAnyAuthority("ASSESSMENT:SELF", "ASSESSMENT:MANAGE")
                        // 视频终面（闭环设计 P5）：员工侧只读本人记录（/my），
                        // HR 专属动作在 Controller 内用 SelfScopeSupport.assertManagementOnly 收口。
                        .requestMatchers("/api/communication-interviews/**")
                            .hasAnyAuthority("EMPLOYEE:READ", "ASSESSMENT:SELF")
                        .requestMatchers("/api/post/evolution/**").hasAuthority("POST:EVOLUTION")
                        // 岗位体系读写分离：GET 放行 POST:READ（岗位体系查看）；
                        // 写（新增/修改/删除/导入/模型配置）一律要求 POST:MANAGE。
                        // 【已修复越权】原实现是无限定方法的 hasAuthority("POST:READ") 覆盖整个
                        // /api/post/**，导致只持有 POST:READ 的 HR 也能调用岗位新增/修改/删除接口。
                        // 岗位全景图谱的**图谱数据**属岗位知识视图：按 2026-09-04 口径，
                        // HR 只看人员能力相关，不看岗位相关知识，故只对岗位体系管理员开放。
                        // ⚠️ 只收 graph / fact-graph 两个重接口，不收 /overview 与 /filters ——
                        // HR 的工作台通过 getPostPanoramaOverview 取聚合指标，整体收掉会打断工作台。
                        // 必须声明在下面 GET /api/post/**（POST:READ）之前。
                        .requestMatchers(HttpMethod.GET,
                                "/api/post/panorama/graph", "/api/post/panorama/fact-graph").hasAuthority("POST:MANAGE")
                        // 岗位趋势发现（权威材料 → 趋势岗位 / 能力变更）：整族含 GET 一律 POST:MANAGE。
                        // 与 /post/panorama/graph 同理——解析产物是岗位体系知识，不是 HR 的人员数据；
                        // 若落到下面 GET /api/post/** 的 POST:READ，只持有查看权的 HR 就能读到
                        // 政府红头文件解析出的岗位草案。必须声明在下面两条规则之前。
                        .requestMatchers("/api/post/trend/**").hasAuthority("POST:MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/post/**").hasAuthority("POST:READ")
                        .requestMatchers("/api/post/**").hasAuthority("POST:MANAGE")
                        .requestMatchers("/api/matching/**").hasAuthority("MATCHING:READ")
                        // 证据中心：同一套接口同时承载「人员能力证据链」与「岗位需求依据链」，
                        // 两个域分属 HR（ASSESSMENT:MANAGE）与岗位体系管理员（POST:MANAGE）。
                        // 具体看哪个域由前端按角色锁死（HR→人员 / 岗位管理员→岗位），
                        // 见 views/capability-brain/evidence。
                        .requestMatchers("/api/contest/**").hasAnyAuthority("POST:MANAGE", "ASSESSMENT:MANAGE")
                        // 人员能力 harness 审核队列：HR 侧“审核人员 harness / 查看修改该人员能力”与
                        // 管理端的标签、数据治理 harness 共用同一套接口，靠 assessmentOnly 参数区分数据域，
                        // 因此这里必须同时放行两种权限，人员域之外的治理数据仍由 POST:MANAGE / AI:CONFIG 收口。
                        .requestMatchers("/api/ai-governance/harness/**").hasAnyAuthority("POST:MANAGE", "ASSESSMENT:MANAGE")
                        // 知识资产（RAG 知识库与它的 harness）与知识图谱，2026-09-04 起归**岗位体系管理员**：
                        // 它们的内容本质是「岗位—能力—技术栈」的知识底座，与 /post/panorama 同源。
                        // 原先挂 AI:CONFIG（AI 配置管理员），角色合并后该码收窄为「平台 AI 基础设施」，
                        // 不再涵盖业务知识，故改挂 POST:MANAGE。
                        .requestMatchers("/api/rag/harness/**").hasAnyAuthority("POST:MANAGE", "ASSESSMENT:MANAGE")
                        .requestMatchers("/api/rag/**").hasAuthority("POST:MANAGE")
                        // AI 上下文包（提示词上下文、来源引用、快照）属于内部可解释性/治理数据，
                        // 只对管理端开放；员工侧不提供该入口（匹配详情页的 AI 上下文按钮按权限隐藏）。
                        .requestMatchers("/api/ai-context/**").hasAnyAuthority(
                                "MATCHING:READ", "MATCHING:APPROVE", "MATCHING:EXECUTE",
                                "ASSESSMENT:MANAGE", "AI:CONFIG", "POST:MANAGE")
                        // 知识图谱（Neo4j 里的「岗位—能力—员工」关系图）2026-09-04 起按**域**拆分：
                        //   · 岗位域（全景 / 岗位中心 / 运维：重建、快照、健康检查、时间线）→ POST:MANAGE
                        //   · 人员域（员工中心图）→ ASSESSMENT:MANAGE，岗位体系管理员**不可见**
                        //   · Agent 记忆属平台 AI 基础设施（不是业务知识）→ 仍归 AI:CONFIG
                        //   · 其余（跨人员+岗位的路径、业务分析、AI 上下文）两域所有者都可用
                        // ⚠️ 之所以要落到后端：页面里"按角色锁域"只是表现层，
                        // 前端隐藏不是安全边界 —— 否则 HR 拿到 URL 仍能拉到全量岗位图谱。
                        // 必须声明在下面 /api/kg/** 的通用规则之前。
                        .requestMatchers(HttpMethod.GET, "/api/kg/memory-graph").hasAuthority("AI:CONFIG")
                        .requestMatchers(HttpMethod.GET, "/api/kg/employee/**").hasAuthority("ASSESSMENT:MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/kg/panorama", "/api/kg/post/**").hasAuthority("POST:MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/kg/build/**").hasAuthority("POST:MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/kg/snapshots").hasAuthority("POST:MANAGE")
                        .requestMatchers(HttpMethod.GET,
                                "/api/kg/build/**", "/api/kg/neo4j/**", "/api/kg/snapshots/**",
                                "/api/kg/timeline").hasAuthority("POST:MANAGE")
                        .requestMatchers("/api/kg/**").hasAnyAuthority("POST:MANAGE", "ASSESSMENT:MANAGE")
                        .requestMatchers("/api/ability/**").hasAuthority("ASSESSMENT:MANAGE")
                        .requestMatchers("/api/learning/resources/**").hasAuthority("ASSESSMENT:MANAGE")
                        .requestMatchers("/api/learning/**").hasAnyAuthority("LEARNING:SELF", "ASSESSMENT:MANAGE")
                        .requestMatchers("/api/vector/**").hasAuthority("AI:CONFIG")
                        .requestMatchers("/api/ai-interview/**").hasAuthority("ASSESSMENT:MANAGE")
                        // 员工本人可查看自己已发布匹配记录的诊断，并确认本人学习成果；
                        // 归属校验在 CapabilityClosureApiFacade 内按当前登录身份二次收口。
                        .requestMatchers(HttpMethod.GET, "/api/capability-closure/matching/*/diagnosis").hasAnyAuthority("ASSESSMENT:MANAGE", "NOTIFICATION:SELF")
                        .requestMatchers(HttpMethod.GET, "/api/capability-closure/matching/*/comprehensive-diagnosis").hasAnyAuthority("ASSESSMENT:MANAGE", "NOTIFICATION:SELF")
                        .requestMatchers(HttpMethod.POST, "/api/capability-closure/learning/outcome").hasAnyAuthority("ASSESSMENT:MANAGE", "LEARNING:SELF")
                        .requestMatchers("/api/capability-closure/**").hasAuthority("ASSESSMENT:MANAGE")
                        .requestMatchers("/api/capability-brain/**").hasAuthority("ASSESSMENT:MANAGE")
                        .requestMatchers("/api/ai-governance/**").hasAuthority("POST:MANAGE")
                        .requestMatchers("/api/test/**").hasAuthority("AI:CONFIG")
                        // 本地上传文件（简历、资源封面等）静态访问无需认证
                        .requestMatchers("/uploads/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
