package com.ylh.backend.Controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ylh.backend.Common.PageResult;
import com.ylh.backend.Common.Result;
import com.ylh.backend.Config.AccessChecker;
import com.ylh.backend.Dto.HostVO;
import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Mapper.HostMetricMapper;
import com.ylh.backend.Service.HostInfoService;
import com.ylh.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/hosts")
@RequiredArgsConstructor
public class HostInfoController {

    private final HostInfoService hostInfoService;
    private final HostMetricMapper hostMetricMapper;
    private final UserService userService;
    private final AccessChecker accessChecker;

    @GetMapping
    public Result<PageResult<HostVO>> listHosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {

        Long currentUserId = accessChecker.getCurrentUserId();
        List<String> roles = accessChecker.getCurrentUserRoles();
        boolean isSuperAdmin = roles.contains("SUPER_ADMIN");
        boolean isGroupAdmin = roles.contains("GROUP_ADMIN");

        Page<HostInfo> pageResult;

        if (isSuperAdmin) {
            pageResult = hostInfoService.pageList(page, size, keyword, status);
        } else if (isGroupAdmin) {
            User currentUser = userService.getById(currentUserId);
            if (currentUser.getGroupId() == null) {
                return Result.success(new PageResult<>(List.of(), 0, page, size));
            }
            List<User> groupMembers = userService.list(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getGroupId, currentUser.getGroupId())
            );
            List<Long> memberIds = groupMembers.stream().map(User::getId).collect(Collectors.toList());
            if (memberIds.isEmpty()) {
                return Result.success(new PageResult<>(List.of(), 0, page, size));
            }
            pageResult = hostInfoService.pageListByUserIds(page, size, keyword, status, memberIds);
        } else {
            pageResult = hostInfoService.pageListByUserId(page, size, keyword, status, currentUserId);
        }

        List<HostVO> records = hostInfoService.toVOList(pageResult.getRecords());
        return Result.success(PageResult.of(pageResult, records));
    }

    @GetMapping("/{id}")
    public Result<HostVO> getHost(@PathVariable Long id) {
        if (!checkHostAccess(id)) {
            return Result.error(403, "无权查看该主机");
        }

        HostInfo host = hostInfoService.getById(id);
        if (host == null) {
            return Result.error("主机不存在");
        }
        return Result.success(hostInfoService.toVO(host));
    }

    @GetMapping("/{id}/metrics/latest")
    public Result<HostVO.MetricVO> getLatestMetric(@PathVariable Long id) {
        if (!checkHostAccess(id)) {
            return Result.error(403, "无权查看该主机");
        }

        HostVO.MetricVO metric = hostInfoService.getLatestMetric(id);
        if (metric == null) {
            return Result.success(new HostVO.MetricVO());
        }
        return Result.success(metric);
    }

    @GetMapping("/{id}/metrics/history")
    public Result<List<HostVO.MetricHistoryVO>> getMetricHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1h") String range) {
        if (!checkHostAccess(id)) {
            return Result.error(403, "无权查看该主机");
        }

        List<HostVO.MetricHistoryVO> history = hostInfoService.getMetricHistory(id, range);
        return Result.success(history);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteHost(@PathVariable Long id) {
        if (!checkHostAccess(id)) {
            return Result.error(403, "无权删除该主机");
        }

        HostInfo host = hostInfoService.getById(id);
        if (host == null) {
            return Result.error("主机不存在");
        }

        hostMetricMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ylh.backend.Entity.HostMetric>()
                        .eq(com.ylh.backend.Entity.HostMetric::getHostId, id)
        );

        hostInfoService.removeById(id);
        log.info("用户 {} 删除了主机 {} ({})", accessChecker.getCurrentUserId(), id, host.getHostname());
        return Result.success("删除成功", null);
    }

    private boolean checkHostAccess(Long hostId) {
        Long currentUserId = accessChecker.getCurrentUserId();
        List<String> roles = accessChecker.getCurrentUserRoles();
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
}
