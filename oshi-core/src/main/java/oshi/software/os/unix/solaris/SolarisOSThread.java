/*
 * Copyright 2020-2022 The OSHI Project Contributors
 * SPDX-License-Identifier: MIT
 */
package oshi.software.os.unix.solaris;

import static oshi.software.os.OSProcess.State.INVALID;
import static oshi.util.Memoizer.defaultExpiration;
import static oshi.util.Memoizer.memoize;

import java.util.function.Supplier;

import com.sun.jna.Pointer;

import oshi.annotation.concurrent.ThreadSafe;
import oshi.driver.unix.solaris.PsInfo;
import oshi.jna.platform.unix.SolarisLibc.SolarisLwpsInfo;
import oshi.jna.platform.unix.SolarisLibc.SolarisPrUsage;
import oshi.software.common.AbstractOSThread;
import oshi.software.os.OSProcess;
import oshi.util.Util;

/**
 * OSThread implementation
 */
@ThreadSafe
public class SolarisOSThread extends AbstractOSThread {

    private Supplier<SolarisLwpsInfo> lwpsinfo = memoize(this::queryLwpsInfo, defaultExpiration());
    private Supplier<SolarisPrUsage> prusage = memoize(this::queryPrUsage, defaultExpiration());

    private String name;
    private int threadId;
    private OSProcess.State state = OSProcess.State.INVALID;
    private long startMemoryAddress;
    private long contextSwitches;
    private long kernelTime;
    private long userTime;
    private long startTime;
    private long upTime;
    private int priority;

    public SolarisOSThread(int pid, int lwpid) {
        super(pid);
        this.threadId = lwpid;
        updateAttributes();
    }

    private SolarisLwpsInfo queryLwpsInfo() {
        return PsInfo.queryLwpsInfo(this.getOwningProcessId(), this.getThreadId());
    }

    private SolarisPrUsage queryPrUsage() {
        return PsInfo.queryPrUsage(this.getOwningProcessId(), this.getThreadId());
    }

    @Override
    public String getName() {
        return this.name != null ? name : "";
    }

    @Override
    public int getThreadId() {
        return this.threadId;
    }

    @Override
    public OSProcess.State getState() {
        return this.state;
    }

    @Override
    public long getStartMemoryAddress() {
        return this.startMemoryAddress;
    }

    @Override
    public long getContextSwitches() {
        return this.contextSwitches;
    }

    @Override
    public long getKernelTime() {
        return this.kernelTime;
    }

    @Override
    public long getUserTime() {
        return this.userTime;
    }

    @Override
    public long getUpTime() {
        return this.upTime;
    }

    @Override
    public long getStartTime() {
        return this.startTime;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public boolean updateAttributes() {
        SolarisLwpsInfo info = lwpsinfo.get();
        if (info == null) {
            this.state = INVALID;
            return false;
        }
        SolarisPrUsage usage = prusage.get();
        long now = System.currentTimeMillis();
        this.state = SolarisOSProcess.getStateFromOutput((char) info.pr_sname);
        this.startTime = info.pr_start.tv_sec.longValue() * 1000L + info.pr_start.tv_nsec.longValue() / 1_000_000L;
        // Avoid divide by zero for processes up less than a millisecond
        long elapsedTime = now - this.startTime;
        this.upTime = elapsedTime < 1L ? 1L : elapsedTime;
        this.kernelTime = 0L;
        this.userTime = info.pr_time.tv_sec.longValue() * 1000L + info.pr_time.tv_nsec.longValue() / 1_000_000L;
        this.startMemoryAddress = Pointer.nativeValue(info.pr_addr);
        this.priority = info.pr_pri;
        if (usage != null) {
            this.userTime = usage.pr_utime.tv_sec.longValue() * 1000L + usage.pr_utime.tv_nsec.longValue() / 1_000_000L;
            this.kernelTime = usage.pr_stime.tv_sec.longValue() * 1000L
                    + usage.pr_stime.tv_nsec.longValue() / 1_000_000L;
            this.contextSwitches = usage.pr_ictx.longValue() + usage.pr_vctx.longValue();
        }
        this.name = com.sun.jna.Native.toString(info.pr_name);
        if (Util.isBlank(name)) {
            this.name = com.sun.jna.Native.toString(info.pr_oldname);
        }
        return true;
    }
}
