package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ylh.backend.Entity.HostProcess;
import com.ylh.backend.Mapper.HostProcessMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HostProcessService extends ServiceImpl<HostProcessMapper, HostProcess> {

    private static final Logger log = LoggerFactory.getLogger(HostProcessService.class);

    public List<HostProcess> getLatestSnapshot(Long hostId) {
        HostProcess latest = getOne(
                new LambdaQueryWrapper<HostProcess>()
                        .eq(HostProcess::getHostId, hostId)
                        .orderByDesc(HostProcess::getCollectTime)
                        .last("LIMIT 1")
        );
        if (latest == null) {
            return List.of();
        }
        LocalDateTime latestTime = latest.getCollectTime();
        LocalDateTime windowStart = latestTime.minusSeconds(1);
        return list(
                new LambdaQueryWrapper<HostProcess>()
                        .eq(HostProcess::getHostId, hostId)
                        .ge(HostProcess::getCollectTime, windowStart)
                        .le(HostProcess::getCollectTime, latestTime)
                        .orderByAsc(HostProcess::getPid)
        );
    }

    public void batchInsert(Long hostId, List<HostProcess> processes) {
        if (processes == null || processes.isEmpty()) {
            return;
        }
        for (HostProcess p : processes) {
            p.setHostId(hostId);
        }
        saveBatch(processes, 500);
    }
}
