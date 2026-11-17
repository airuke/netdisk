package com.airuike.netdisk.backend.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public final class DeadlineInputStream extends FilterInputStream {
    private final long deadline;
    public DeadlineInputStream(InputStream input, long minutes) {
        super(input); deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(minutes);
    }
    private void check() throws IOException {
        if (System.nanoTime() > deadline) throw new IOException("合并读取超过任务租约，请稍后重试");
    }
    @Override public int read() throws IOException { check(); return in.read(); }
    @Override public int read(byte[] b, int offset, int length) throws IOException {
        check(); return in.read(b, offset, length);
    }
}
