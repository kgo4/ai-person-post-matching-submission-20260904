package com.example.matching.application.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.common.ChangePasswordDTO;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.system.LoginDTO;
import com.example.matching.dto.system.UserSaveDTO;
import com.example.matching.security.AuthRateLimitService;
import com.example.matching.security.TokenInvalidationService;
import com.example.matching.service.system.SysUserService;
import com.example.matching.vo.system.LoginVO;
import com.example.matching.vo.system.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysUserApiFacade {

    private final SysUserService sysUserService;
    private final AuthRateLimitService authRateLimitService;
    private final TokenInvalidationService tokenInvalidationService;

    public void checkLoginAllowed(String clientIp, String username) {
        authRateLimitService.checkLoginAllowed(clientIp, username);
    }

    public void clearLoginFailures(String clientIp, String username) {
        authRateLimitService.clearLoginFailures(clientIp, username);
    }

    public LoginVO login(String username, String password) {
        return sysUserService.login(username, password);
    }

    public void recordLoginFailure(String clientIp, String username) {
        authRateLimitService.recordLoginFailure(clientIp, username);
    }

    public LoginVO register(UserSaveDTO dto) {
        dto.setStatus(1);
        sysUserService.saveUser(dto);
        return sysUserService.login(dto.getUsername(), dto.getPassword());
    }

    public void checkRegistrationAllowed(String clientIp) {
        authRateLimitService.checkRegistrationAllowed(clientIp);
    }

    public PageResponse<UserVO> pageUsers(long current, long size, String keyword, Integer status) {
        IPage<UserVO> page = sysUserService.pageUsers(new Page<>(current, size), keyword, status);
        return new PageResponse<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    public UserVO getUserVOById(Long id) {
        return sysUserService.getUserVOById(id);
    }

    public void saveUser(UserSaveDTO dto) {
        sysUserService.saveUser(dto);
    }

    public void changePassword(Long userId, ChangePasswordDTO dto) {
        sysUserService.changePassword(userId, dto);
    }

    public void resetPassword(Long id) {
        sysUserService.resetPassword(id);
    }

    public void updateStatus(Long id, Integer status) {
        sysUserService.updateStatus(id, status);
    }

    public void removeById(Long id) {
        sysUserService.removeById(id);
    }

    public void invalidateUserTokens(Long userId) {
        tokenInvalidationService.invalidateUserTokens(userId);
    }
}
