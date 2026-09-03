package com.example.matching.controller.system;

import com.example.matching.application.system.SysUserApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.result.PageResultVO;
import com.example.matching.common.result.R;
import com.example.matching.dto.common.ChangePasswordDTO;
import com.example.matching.dto.system.LoginDTO;
import com.example.matching.dto.system.UserSaveDTO;
import com.example.matching.utils.SecurityUtils;
import com.example.matching.vo.system.LoginVO;
import com.example.matching.vo.system.UserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserControllerTest {

    private SysUserApiFacade facade;
    private SysUserController controller;

    @BeforeEach
    void setUp() {
        facade = mock(SysUserApiFacade.class);
        controller = new SysUserController(facade);
        SecurityUtils.setCurrentUserId(5L);
    }

    @AfterEach
    void tearDown() {
        SecurityUtils.clear();
    }

    private static LoginDTO loginDto(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private static LoginVO createLoginVO(String token) {
        return LoginVO.builder()
                .token(token)
                .userId(5L)
                .username("admin")
                .realName("系统管理员")
                .roles(List.of("ADMIN"))
                .permissions(List.of("user:list"))
                .build();
    }

    private static UserVO createUserVO(Long id) {
        UserVO vo = new UserVO();
        vo.setId(id);
        vo.setUsername("admin");
        vo.setRealName("系统管理员");
        vo.setStatus(1);
        vo.setRoles(List.of("ADMIN"));
        return vo;
    }

    @Test
    void loginSuccessClearsLoginFailures() {
        when(facade.login("admin", "123456")).thenReturn(createLoginVO("token-1"));

        R<LoginVO> response = controller.login(loginDto("admin", "123456"));

        assertThat(response.getData().getToken()).isEqualTo("token-1");
        verify(facade).checkLoginAllowed("unknown", "admin");
        verify(facade).clearLoginFailures("unknown", "admin");
        verify(facade, never()).recordLoginFailure(anyString(), anyString());
    }

    @Test
    void loginFailureRecordsFailureAndRethrows() {
        when(facade.login("admin", "bad"))
                .thenThrow(new BusinessException(ErrorCodeEnum.USER_PASSWORD_ERROR));

        assertThatThrownBy(() -> controller.login(loginDto("admin", "bad")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCodeEnum.USER_PASSWORD_ERROR.getMessage());

        verify(facade).recordLoginFailure("unknown", "admin");
        verify(facade, never()).clearLoginFailures(anyString(), anyString());
    }

    @Test
    void registerReturnsLoginResult() {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setUsername("zhangsan");
        dto.setPassword("Abc@12345");
        dto.setRealName("张三");
        when(facade.register(dto)).thenReturn(createLoginVO("token-2"));

        R<LoginVO> response = controller.register(dto);

        assertThat(response.getData().getUsername()).isEqualTo("admin");
        verify(facade).checkRegistrationAllowed("unknown");
        verify(facade).register(dto);
    }

    @Test
    void pageReturnsUserPage() {
        UserVO vo = createUserVO(1L);
        PageResponse<UserVO> page = new PageResponse<>(List.of(vo), 1, 1, 10, 1);
        when(facade.pageUsers(1, 10, null, null)).thenReturn(page);

        R<PageResultVO<UserVO>> response = controller.page(1, 10, null, null);

        assertThat(response.getData().getRecords()).containsExactly(vo);
        assertThat(response.getData().getTotal()).isEqualTo(1);
        assertThat(response.getData().getCurrent()).isEqualTo(1);
        assertThat(response.getData().getSize()).isEqualTo(10);
    }

    @Test
    void getByIdReturnsUserDetail() {
        UserVO vo = createUserVO(1L);
        when(facade.getUserVOById(1L)).thenReturn(vo);

        R<UserVO> response = controller.getById(1L);

        assertThat(response.getData()).isEqualTo(vo);
    }

    @Test
    void currentReturnsCurrentUser() {
        UserVO vo = createUserVO(5L);
        when(facade.getUserVOById(5L)).thenReturn(vo);

        R<UserVO> response = controller.current();

        assertThat(response.getData()).isEqualTo(vo);
        verify(facade).getUserVOById(5L);
    }

    @Test
    void saveCreatesUser() {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setUsername("zhangsan");
        dto.setRealName("张三");

        R<Void> response = controller.save(dto);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).saveUser(dto);
    }

    @Test
    void updateSetsIdOnDto() {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setUsername("zhangsan");
        dto.setRealName("张三");

        R<Void> response = controller.update(3L, dto);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(dto.getId()).isEqualTo(3L);
        verify(facade).saveUser(dto);
    }

    @Test
    void changePasswordUsesSecurityUserId() {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setOldPassword("old");
        dto.setNewPassword("new");

        R<Void> response = controller.changePassword(dto);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).changePassword(5L, dto);
    }

    @Test
    void resetPasswordResetsPassword() {
        R<Void> response = controller.resetPassword(1L);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).resetPassword(1L);
    }

    @Test
    void updateStatusUpdatesUserStatus() {
        R<Void> response = controller.updateStatus(1L, 0);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).updateStatus(1L, 0);
    }

    @Test
    void deleteRemovesUser() {
        R<Void> response = controller.delete(1L);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).removeById(1L);
    }

    @Test
    void logoutWithBearerHeaderInvalidatesTokens() {
        R<Void> response = controller.logout("Bearer token-1");

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).invalidateUserTokens(5L);
    }

    @Test
    void logoutWithoutHeaderSkipsInvalidation() {
        R<Void> response = controller.logout(null);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade, never()).invalidateUserTokens(anyLong());
    }

    @Test
    void logoutWithNonBearerHeaderSkipsInvalidation() {
        R<Void> response = controller.logout("Basic dXNlcjpwYXNz");

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade, never()).invalidateUserTokens(anyLong());
    }
}
