package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ylh.backend.Dto.HostVO;
import com.ylh.backend.Entity.Group;
import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Entity.HostMetric;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Mapper.GroupMapper;
import com.ylh.backend.Mapper.HostInfoMapper;
import com.ylh.backend.Mapper.HostMetricMapper;
import com.ylh.backend.Mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HostInfoService extends ServiceImpl<HostInfoMapper, HostInfo> {

    private final UserMapper userMapper;
    private final GroupMapper groupMapper;
    private final HostMetricMapper hostMetricMapper;

    public Page<HostInfo> pageList(int page, int size, String keyword) {
        Page<HostInfo> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<HostInfo> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w
                    .like(HostInfo::getHostname, keyword)
                    .or()
                    .like(HostInfo::getIpAddress, keyword)
            );
        }
        wrapper.orderByDesc(HostInfo::getCreateTime);
        return page(pageParam, wrapper);
    }

    public Page<HostInfo> pageListByUserIds(int page, int size, String keyword, List<Long> userIds) {
        Page<HostInfo> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<HostInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(HostInfo::getUserId, userIds);
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w
                    .like(HostInfo::getHostname, keyword)
                    .or()
                    .like(HostInfo::getIpAddress, keyword)
            );
        }
        wrapper.orderByDesc(HostInfo::getCreateTime);
        return page(pageParam, wrapper);
    }

    public Page<HostInfo> pageListByUserId(int page, int size, String keyword, Long userId) {
        Page<HostInfo> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<HostInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HostInfo::getUserId, userId);
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w
                    .like(HostInfo::getHostname, keyword)
                    .or()
                    .like(HostInfo::getIpAddress, keyword)
            );
        }
        wrapper.orderByDesc(HostInfo::getCreateTime);
        return page(pageParam, wrapper);
    }

    public boolean isHostOwnedBy(Long hostId, Long userId) {
        HostInfo host = getById(hostId);
        return host != null && host.getUserId().equals(userId);
    }

    public boolean isHostOwnedByUsers(Long hostId, List<Long> userIds) {
        HostInfo host = getById(hostId);
        return host != null && userIds.contains(host.getUserId());
    }

    public HostVO.MetricVO getLatestMetric(Long hostId) {
        HostMetric metric = hostMetricMapper.selectOne(
                new LambdaQueryWrapper<HostMetric>()
                        .eq(HostMetric::getHostId, hostId)
                        .orderByDesc(HostMetric::getCollectTime)
                        .last("LIMIT 1")
        );
        if (metric == null) {
            return null;
        }
        HostVO.MetricVO vo = new HostVO.MetricVO();
        vo.setCpuUsage(metric.getCpuUsage());
        vo.setMemoryUsage(metric.getMemoryUsage());
        vo.setDiskUsage(metric.getDiskUsage());
        vo.setMemoryUsed(metric.getMemoryUsed());
        vo.setDiskUsed(metric.getDiskUsed());
        vo.setCollectTime(metric.getCollectTime());
        return vo;
    }

    public List<HostVO.MetricHistoryVO> getMetricHistory(Long hostId, String range) {
        LocalDateTime since = parseRange(range);
        List<HostMetric> metrics = hostMetricMapper.selectList(
                new LambdaQueryWrapper<HostMetric>()
                        .eq(HostMetric::getHostId, hostId)
                        .ge(HostMetric::getCollectTime, since)
                        .orderByAsc(HostMetric::getCollectTime)
        );
        return metrics.stream().map(m -> {
            HostVO.MetricHistoryVO vo = new HostVO.MetricHistoryVO();
            vo.setCollectTime(m.getCollectTime());
            vo.setCpuUsage(m.getCpuUsage());
            vo.setMemoryUsage(m.getMemoryUsage());
            vo.setDiskUsage(m.getDiskUsage());
            return vo;
        }).collect(Collectors.toList());
    }

    private LocalDateTime parseRange(String range) {
        LocalDateTime now = LocalDateTime.now();
        if (range == null) return now.minusMinutes(5);
        if (range.endsWith("m")) {
            int minutes = Integer.parseInt(range.substring(0, range.length() - 1));
            return now.minusMinutes(Math.min(minutes, 2880));
        }
        if (range.endsWith("h")) {
            int hours = Integer.parseInt(range.substring(0, range.length() - 1));
            return now.minusHours(Math.min(hours, 48));
        }
        return switch (range) {
            case "5m" -> now.minusMinutes(5);
            case "10m" -> now.minusMinutes(10);
            case "30m" -> now.minusMinutes(30);
            case "1h" -> now.minusHours(1);
            default -> now.minusMinutes(5);
        };
    }

    public HostVO toVO(HostInfo host) {
        HostVO vo = new HostVO();
        vo.setId(host.getId());
        vo.setHostname(host.getHostname());
        vo.setIpAddress(host.getIpAddress());
        vo.setCpuModel(host.getCpuModel());
        vo.setCpuCores(host.getCpuCores());
        vo.setMemoryTotal(host.getMemoryTotal());
        vo.setDiskTotal(host.getDiskTotal());
        vo.setOsName(host.getOsName());
        vo.setOsVersion(host.getOsVersion());
        vo.setStatus(host.getStatus());
        vo.setLastHeartbeat(host.getLastHeartbeat());
        vo.setUserId(host.getUserId());
        vo.setCreateTime(host.getCreateTime());

        if (host.getUserId() != null) {
            User user = userMapper.selectById(host.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setGroupId(user.getGroupId());
                if (user.getGroupId() != null) {
                    Group group = groupMapper.selectById(user.getGroupId());
                    if (group != null) {
                        vo.setGroupName(group.getGroupName());
                    }
                }
            }
        }
        return vo;
    }

    public List<HostVO> toVOList(List<HostInfo> hosts) {
        return hosts.stream().map(this::toVO).collect(Collectors.toList());
    }
}
