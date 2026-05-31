package com.ylh.client.utils;

import com.ylh.client.models.ProcessInfo;
import com.ylh.client.models.ProcessListDelta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ProcessDeltaCalculator {

    private static final double DEFAULT_THRESHOLD = 1.0;

    private ProcessDeltaCalculator() {
    }

    public static ProcessListDelta compute(Map<Integer, ProcessInfo> lastSnapshot,
                                           List<ProcessInfo> currentList, int seq) {
        return compute(lastSnapshot, currentList, seq, DEFAULT_THRESHOLD);
    }

    public static ProcessListDelta compute(Map<Integer, ProcessInfo> lastSnapshot,
                                           List<ProcessInfo> currentList, int seq,
                                           double threshold) {
        Map<Integer, ProcessInfo> currentMap = new HashMap<>();
        for (ProcessInfo p : currentList) {
            currentMap.put(p.getPid(), p);
        }

        List<ProcessInfo> added = new ArrayList<>();
        List<ProcessInfo> changed = new ArrayList<>();
        List<Integer> removed = new ArrayList<>();

        for (ProcessInfo current : currentList) {
            ProcessInfo last = lastSnapshot.get(current.getPid());
            if (last == null) {
                added.add(current);
            } else if (isSignificantlyChanged(last, current, threshold)) {
                changed.add(current);
            }
        }

        for (Integer pid : lastSnapshot.keySet()) {
            if (!currentMap.containsKey(pid)) {
                removed.add(pid);
            }
        }

        return new ProcessListDelta(seq, added, removed, changed);
    }

    private static boolean isSignificantlyChanged(ProcessInfo last, ProcessInfo current, double threshold) {
        if (!strEq(last.getName(), current.getName())) return true;
        if (!strEq(last.getUser(), current.getUser())) return true;
        if (!strEq(last.getStatus(), current.getStatus())) return true;
        if (!strEq(last.getCommandLine(), current.getCommandLine())) return true;
        if (Math.abs(last.getCpuUsage() - current.getCpuUsage()) >= threshold) return true;
        if (Math.abs(last.getMemoryUsage() - current.getMemoryUsage()) >= threshold) return true;
        return false;
    }

    private static boolean strEq(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
