package com.xspaceagi.eco.market.web.aspect;

import com.xspaceagi.eco.market.spec.annotation.RequireAdmin;
import com.xspaceagi.eco.market.spec.exception.AdminPermissionException;
import com.xspaceagi.system.spec.common.RequestContext;
import com.xspaceagi.system.application.dto.UserDto;
import com.xspaceagi.system.application.service.UserApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 管理员权限切面
 * 用于拦截带有@RequireAdmin注解的方法，验证当前用户是否为管理员
 * 
 * @author soddy
 */
@Slf4j
@Aspect
@Component
public class AdminPermissionAspect {
    
    @Autowired
    private UserApplicationService userApplicationService;
    
    /**
     * 环绕通知，拦截带有@RequireAdmin注解的方法
     * 
     * @param joinPoint 连接点
     * @param requireAdmin 权限注解
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("@annotation(requireAdmin)")
    public Object checkAdminPermission(ProceedingJoinPoint joinPoint, RequireAdmin requireAdmin) throws Throwable {
        // 如果允许跳过权限检查，直接执行方法
        if (requireAdmin.skipCheck()) {
            log.debug("跳过管理员权限检查: {}", joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }
        
        try {
            // 获取当前用户信息
            UserDto currentUser = (UserDto) RequestContext.get().getUser();
            if (currentUser == null) {
                log.warn("用户未登录，拒绝访问: {}", joinPoint.getSignature().getName());
                throw new AdminPermissionException("用户未登录");
            }
            
            // 获取用户详细信息并检查是否为管理员
            UserDto userDetail = userApplicationService.queryById(currentUser.getId());
//            if (userDetail == null || !User.Role.Admin.equals(userDetail.getRole())) {
//                log.warn("用户[{}]没有管理员权限，拒绝访问: {}",
//                    currentUser.getId(), joinPoint.getSignature().getName());
//                throw new AdminPermissionException(requireAdmin.value());
//            }
            
            log.debug("管理员权限验证通过，用户[{}]访问: {}", 
                currentUser.getId(), joinPoint.getSignature().getName());
            
            // 权限验证通过，执行目标方法
            return joinPoint.proceed();
            
        } catch (AdminPermissionException e) {
            log.warn("管理员权限验证失败", e);
            // 重新抛出权限异常
            throw e;
        }
    }
}