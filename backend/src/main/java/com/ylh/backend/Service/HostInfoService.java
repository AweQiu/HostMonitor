package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ylh.backend.Dto.HostVO;
import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Mapper.HostInfoMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HostInfoService extends ServiceImpl<HostInfoMapper, HostInfo> {

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
        return vo;
    }

    public List<HostVO> toVOList(List<HostInfo> hosts) {
        return hosts.stream().map(this::toVO).collect(Collectors.toList());
    }
}
