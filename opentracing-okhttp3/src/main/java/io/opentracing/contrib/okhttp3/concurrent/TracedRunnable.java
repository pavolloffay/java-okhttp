package io.opentracing.contrib.okhttp3.concurrent;

import io.opentracing.ActiveSpan;
import io.opentracing.NoopActiveSpanSource;

/**
 * @author Pavol Loffay
 */
public class TracedRunnable implements Runnable {

    private final Runnable delegate;
    private final ActiveSpan.Continuation continuation;
    
    public TracedRunnable(Runnable delegate, ActiveSpan activeSpan) {
        this.delegate = delegate;
        this.continuation = activeSpan != null ? activeSpan.capture() : NoopActiveSpanSource.NoopContinuation.INSTANCE;
    }

    @Override
    public void run() {
        ActiveSpan activeSpan = continuation.activate();
        try {
            delegate.run();
        } finally {
            activeSpan.close();
        }
    }
}
