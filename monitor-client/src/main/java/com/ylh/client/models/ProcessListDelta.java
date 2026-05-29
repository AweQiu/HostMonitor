package com.ylh.client.models;

import lombok.Data;

import java.util.List;

@Data
public class ProcessListDelta {

    private int seq;
    private List<ProcessInfo> added;
    private List<Integer> removed;
    private List<ProcessInfo> changed;

    public ProcessListDelta() {
    }

    public ProcessListDelta(int seq, List<ProcessInfo> added, List<Integer> removed, List<ProcessInfo> changed) {
        this.seq = seq;
        this.added = added;
        this.removed = removed;
        this.changed = changed;
    }
}
