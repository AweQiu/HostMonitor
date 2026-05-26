package com.ylh.backend.Controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ylh.backend.Common.Result;
import com.ylh.backend.Dto.HostVO;
import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Mapper.HostMetricMapper;
import com.ylh.backend.Service.HostInfoService;
import com.ylh.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/hosts")
@RequiredArgsConstructor
public class HostInfoController {

    private final HostInfoService hostInfoService;
    private final HostMetricMapper hostMetricMapper;
    private final UserService userService;

    @GetMapping
    public Result<Map<String, Object>> listHosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {

        Page<HostInfo> pageResult = hostInfoService.pageList(page, size, keyword);
        List<HostVO> records = hostInfoService.toVOList(pageResult.getRecords());

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", pageResult.getTotal());
        data.put("page", pageResult.getCurrent());
        data.put("size", pageResult.getSize());

        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<HostVO> getHost(@PathVariable Long id) {
        HostInfo host = hostInfoService.getById(id);
        if (host == null) {
            return Result.error("主机不存在");
        }
        return Result.success(hostInfoService.toVO(host));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteHost(@PathVariable Long id) {
        checkMonitorPermission();

        HostInfo host = hostInfoService.getById(id);
        if (host == null) {
            return Result.error("主机不存在");
        }

        hostMetricMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ylh.backend.Entity.HostMetric>()
                        .eq(com.ylh.backend.Entity.HostMetric::getHostId, id)
        );

        hostInfoService.removeById(id);
        log.info("用户 {} 删除了主机 {} ({})", getCurrentUserId(), id, host.getHostname());
        return Result.success("删除成功", null);
    }

    private void checkMonitorPermission() {
        Long currentUserId = getCurrentUserId();
        List<String> roles = userService.getUserRoleKeys(currentUserId);
        if (!roles.contains("SUPER_ADMIN") && !roles.contains("GROUP_ADMIN")) {
            throw new RuntimeException("没有主机管理权限");
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }
}
