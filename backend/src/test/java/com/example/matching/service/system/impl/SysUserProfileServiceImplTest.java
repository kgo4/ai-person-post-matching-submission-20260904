package com.example.matching.service.system.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.system.MyProfileUpdateDTO;
import com.example.matching.entity.system.SysUser;
import com.example.matching.mapper.system.SysUserMapper;
import com.example.matching.security.JwtTokenProvider;
import com.example.matching.security.TokenInvalidationService;
import com.example.matching.security.UserAuthoritiesService;
import com.example.matching.service.system.SysRoleService;
import com.example.matching.vo.system.MyProfileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个人中心「自助资料」的语义测试。
 *
 * <p>重点钉住三件事，它们都是很容易被"顺手简化"掉、且出问题后很难发现的口径：</p>
 * <ol>
 *   <li><b>手机号不脱敏</b>：个人中心要让人编辑自己的手机号，
 *       返回 138****0000 会让用户把掩码写回数据库；</li>
 *   <li><b>null 与空串语义不同</b>：null=不改这一项，空串=清空。
 *       若把两者都当"没传"，用户就永远清不掉已填的手机号/邮箱；</li>
 *   <li><b>换头像要删旧文件</b>：否则 uploads/avatars 会随使用无限增长。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SysUserProfileServiceImplTest {

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TokenInvalidationService tokenInvalidationService;
    @Mock private SysRoleService sysRoleService;
    @Mock private CacheManager cacheManager;
    @Mock private UserAuthoritiesService userAuthoritiesService;
    @Mock private AvatarStorage avatarStorage;
    @Mock private SysUserMapper userMapper;

    private SysUserServiceImpl service;

    private static final Long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        service = new SysUserServiceImpl(passwordEncoder, jwtTokenProvider, tokenInvalidationService,
                sysRoleService, cacheManager, userAuthoritiesService, avatarStorage);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
    }

    private SysUser existingUser() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("zhangsan");
        user.setRealName("张三");
        user.setPhone("13800000000");
        user.setEmail("zhangsan@example.com");
        user.setAvatar("/uploads/avatars/old.png");
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        return user;
    }

    @Test
    void getMyProfile_returnsUnmaskedPhoneForSelfEditing() {
        existingUser();
        when(sysRoleService.getUserRoleIds(USER_ID)).thenReturn(List.of());

        MyProfileVO vo = service.getMyProfile(USER_ID);

        // 关键：不能是 138****0000 —— 用户会照抄这串掩码存回去
        assertThat(vo.getPhone()).isEqualTo("13800000000");
        assertThat(vo.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(vo.getAvatar()).isEqualTo("/uploads/avatars/old.png");
        assertThat(vo.getUsername()).isEqualTo("zhangsan");
    }

    @Test
    void updateMyProfile_nullFieldMeansKeepCurrentValue() {
        SysUser user = existingUser();
        MyProfileUpdateDTO dto = new MyProfileUpdateDTO();
        dto.setEmail("new@example.com");
        // phone / avatar 保持 null：表示本次不改
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

        service.updateMyProfile(USER_ID, dto);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getPhone()).isEqualTo("13800000000");
        assertThat(user.getAvatar()).isEqualTo("/uploads/avatars/old.png");
        // 没换头像就不该动文件
        verify(avatarStorage, never()).deleteQuietly(any());
    }

    @Test
    void updateMyProfile_blankFieldMeansClearValue() {
        SysUser user = existingUser();
        MyProfileUpdateDTO dto = new MyProfileUpdateDTO();
        dto.setPhone("");
        dto.setEmail("   ");
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

        service.updateMyProfile(USER_ID, dto);

        // 空串/纯空白 = 清空（归一为 null，而不是留下空字符串）
        assertThat(user.getPhone()).isNull();
        assertThat(user.getEmail()).isNull();
    }

    @Test
    void updateMyProfile_newAvatarDeletesPreviousFile() {
        SysUser user = existingUser();
        MyProfileUpdateDTO dto = new MyProfileUpdateDTO();
        dto.setAvatar("/uploads/avatars/new.png");
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

        service.updateMyProfile(USER_ID, dto);

        assertThat(user.getAvatar()).isEqualTo("/uploads/avatars/new.png");
        verify(avatarStorage).deleteQuietly("/uploads/avatars/old.png");
    }

    @Test
    void updateMyProfile_sameAvatarDoesNotDeleteAnything() {
        SysUser user = existingUser();
        MyProfileUpdateDTO dto = new MyProfileUpdateDTO();
        dto.setAvatar("/uploads/avatars/old.png");
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

        service.updateMyProfile(USER_ID, dto);

        // 值没变就不该删 —— 否则会把正在用的头像删掉
        verify(avatarStorage, never()).deleteQuietly(any());
    }

    @Test
    void updateMyProfile_rejectsUnknownUser() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.updateMyProfile(USER_ID, new MyProfileUpdateDTO()))
                .isInstanceOf(BusinessException.class);
        // any(SysUser.class) 而非裸 any()：BaseMapper 有 updateById(T) 与
        // updateById(Collection<T>) 两个重载，裸 any() 会让编译器无法选择
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void updateMyProfile_rejectsNullPayload() {
        assertThatThrownBy(() -> service.updateMyProfile(USER_ID, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void uploadAvatar_delegatesToStorage() {
        when(avatarStorage.store(any(), any())).thenReturn("/uploads/avatars/x.png");

        assertThat(service.uploadAvatar(new byte[]{1, 2}, "image/png"))
                .isEqualTo("/uploads/avatars/x.png");
    }
}
