package io.opentracing.contrib.okhttp3;

import okhttp3.OkHttpClient;

/**
 * @author Pavol Loffay
 */
public class InterceptorTest extends AbstractOkHttpTest {

    public InterceptorTest() {
        super(TracingInterceptor.addTracing(new OkHttpClient.Builder(), AbstractOkHttpTest.mockTracer));
    }
}
