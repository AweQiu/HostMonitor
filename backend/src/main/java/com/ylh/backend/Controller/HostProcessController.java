package com.ylh.backend.Controller;

import com.ylh.backend.Common.Result;
import com.ylh.backend.Dto.ProcessVO;
import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Entity.HostProcess;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Service.HostInfoService;
import com.ylh.backend.Service.HostProcessService;
import com.ylh.backend.Service.ProcessRuleService;
import com.ylh.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/hosts")
@RequiredArgsConstructor
public class HostProcessController {

    private final HostProcessService hostProcessService;
    private final HostInfoService hostInfoService;
    private final UserService userService;
    private final ProcessRuleService processRuleService;

    @GetMapping("/{id}/processes")
    public Result<List<ProcessVO>> getProcesses(@PathVariable Long id) {
        if (!checkHostAccess(id)) {
            return Result.error(403, "无权查看该主机");
        }
        List<HostProcess> processes = hostProcessService.getLatestSnapshot(id);

        Long groupId = null;
        HostInfo host = hostInfoService.getById(id);
        if (host != null && host.getUserId() != null) {
            User hostUser = userService.getById(host.getUserId());
            if (hostUser != null) {
                groupId = hostUser.getGroupId();
            }
        }

        final Long finalGroupId = groupId;
        List<ProcessVO> voList = processes.stream().map(p -> {
            ProcessVO vo = new ProcessVO();
            vo.setId(p.getId());
            vo.setHostId(p.getHostId());
            vo.setPid(p.getPid());
            vo.setPpid(p.getPpid());
            vo.setName(p.getName());
            vo.setUser(p.getUser());
            vo.setCpuUsage(p.getCpuUsage());
            vo.setMemoryUsage(p.getMemoryUsage());
            vo.setMemoryUsed(p.getMemoryUsed());
            vo.setStatus(p.getStatus());
            vo.setStartTime(p.getStartTime());
            vo.setCommandLine(p.getCommandLine());
            vo.setCollectTime(p.getCollectTime());

            if (finalGroupId != null && p.getName() != null) {
                vo.setIsWhitelisted(processRuleService.isWhitelisted(finalGroupId, p.getName()));
                vo.setIsBlacklisted(processRuleService.isBlacklisted(finalGroupId, p.getName()));
            } else {
                vo.setIsWhitelisted(false);
                vo.setIsBlacklisted(false);
            }
            return vo;
        }).collect(Collectors.toList());

        return Result.success(voList);
    }

    private boolean checkHostAccess(Long hostId) {
        Long currentUserId = getCurrentUserId();
        List<String> roles = userService.getUserRoleKeys(currentUserId);
        boolean isSuperAdmin = roles.contains("SUPER_ADMIN");
        boolean isGroupAdmin = roles.contains("GROUP_ADMIN");

        if (isSuperAdmin) {
            return true;
        }

        if (isGroupAdmin) {
            User currentUser = userService.getById(currentUserId);
            if (currentUser.getGroupId() == null) {
                return false;
            }
            List<User> groupMembers = userService.list(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getGroupId, currentUser.getGroupId())
            );
            List<Long> memberIds = groupMembers.stream().map(User::getId).collect(Collectors.toList());
            return hostInfoService.isHostOwnedByUsers(hostId, memberIds);
        }

        return hostInfoService.isHostOwnedBy(hostId, currentUserId);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }
}
