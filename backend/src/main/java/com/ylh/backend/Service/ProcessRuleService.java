package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ylh.backend.Entity.ProcessRule;
import com.ylh.backend.Mapper.ProcessRuleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProcessRuleService extends ServiceImpl<ProcessRuleMapper, ProcessRule> {

    public List<ProcessRule> getRulesByGroupId(Long groupId) {
        return list(new LambdaQueryWrapper<ProcessRule>()
                .eq(ProcessRule::getGroupId, groupId)
                .orderByDesc(ProcessRule::getCreateTime));
    }

    public List<ProcessRule> getAllRules() {
        return list(new LambdaQueryWrapper<ProcessRule>()
                .orderByDesc(ProcessRule::getCreateTime));
    }

    public boolean existsByGroupIdAndProcessName(Long groupId, String processName) {
        return count(new LambdaQueryWrapper<ProcessRule>()
                .eq(ProcessRule::getGroupId, groupId)
                .eq(ProcessRule::getProcessName, processName)) > 0;
    }

    public boolean isWhitelisted(Long groupId, String processName) {
        return count(new LambdaQueryWrapper<ProcessRule>()
                .eq(ProcessRule::getGroupId, groupId)
                .eq(ProcessRule::getRuleType, "WHITELIST")
                .eq(ProcessRule::getProcessName, processName)) > 0;
    }

    public boolean isBlacklisted(Long groupId, String processName) {
        return count(new LambdaQueryWrapper<ProcessRule>()
                .eq(ProcessRule::getGroupId, groupId)
                .eq(ProcessRule::getRuleType, "BLACKLIST")
                .eq(ProcessRule::getProcessName, processName)) > 0;
    }

    public List<ProcessRule> getBlacklistByGroupId(Long groupId) {
        return list(new LambdaQueryWrapper<ProcessRule>()
                .eq(ProcessRule::getGroupId, groupId)
                .eq(ProcessRule::getRuleType, "BLACKLIST"));
    }

    public List<ProcessRule> getWhitelistByGroupId(Long groupId) {
        return list(new LambdaQueryWrapper<ProcessRule>()
                .eq(ProcessRule::getGroupId, groupId)
                .eq(ProcessRule::getRuleType, "WHITELIST"));
    }
}
