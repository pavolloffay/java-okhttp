package io.opentracing.contrib.okhttp3.concurrent;

import java.util.concurrent.Callable;

import io.opentracing.ActiveSpan;
import io.opentracing.NoopActiveSpanSource;

/**
 * @author Pavol Loffay
 */
public class TracedCallable<V> implements Callable<V> {

    private final Callable<V> delegate;
    private final ActiveSpan.Continuation continuation;

    public TracedCallable(Callable<V> delegate, ActiveSpan activeSpan) {
        this.delegate = delegate;
        this.continuation = activeSpan != null ? activeSpan.capture() : NoopActiveSpanSource.NoopContinuation.INSTANCE;
    }

    @Override
    public V call() throws Exception {
        ActiveSpan activeSpan = continuation.activate();
        try {
            return delegate.call();
        } finally {
            activeSpan.close();
        }
    }
}
