package io.opentracing.contrib.okhttp3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import io.opentracing.ActiveSpan;
import io.opentracing.Tracer;
import io.opentracing.contrib.okhttp3.concurrent.TracingExecutorService;
import io.opentracing.tag.Tags;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp interceptor to trace client requests. Interceptor adds span context into outgoing requests.
 * Please this instrumentation only when {@link TracingCallFactory} is not possible to use. This
 * instrumentation fails properly infer parent span when doing simultaneously asynchronous calls.
 *
 * <p>Initialization via {@link TracingInterceptor#addTracing(OkHttpClient.Builder, Tracer, List)}
 *
 * <p>or instantiate the interceptor and add it to {@link OkHttpClient.Builder#addInterceptor(Interceptor)} and
 * {@link OkHttpClient.Builder#addNetworkInterceptor(Interceptor)}.
 * @author Pavol Loffay
 */
public class TracingInterceptor implements Interceptor {
    private static final Logger log = Logger.getLogger(TracingInterceptor.class.getName());

    private Tracer tracer;
    private List<OkHttpClientSpanDecorator> decorators;

    public static OkHttpClient addTracing(OkHttpClient.Builder builder, Tracer tracer) {
        TracingInterceptor tracingInterceptor = new TracingInterceptor(tracer);
        builder.interceptors().add(0, tracingInterceptor);
        builder.networkInterceptors().add(0, tracingInterceptor);
        builder.dispatcher(new Dispatcher(new TracingExecutorService(Executors.newFixedThreadPool(10), tracer)));
        return builder.build();
    }

    /**
     * Create tracing interceptor. Interceptor has to be added to {@link OkHttpClient.Builder#addInterceptor(Interceptor)}
     * and {@link OkHttpClient.Builder#addNetworkInterceptor(Interceptor)}.
     *
     * @param tracer tracer
     */
    public TracingInterceptor(Tracer tracer) {
        this(tracer, Collections.singletonList(OkHttpClientSpanDecorator.STANDARD_TAGS));
    }

    /**
     * Create tracing interceptor. Interceptor has to be added to {@link OkHttpClient.Builder#addInterceptor(Interceptor)}
     * and {@link OkHttpClient.Builder#addNetworkInterceptor(Interceptor)}.
     *
     * @param tracer tracer
     * @param decorators decorators
     */
    public TracingInterceptor(Tracer tracer, List<OkHttpClientSpanDecorator> decorators) {
        this.tracer = tracer;
        this.decorators = new ArrayList<>(decorators);
    }

    /**
     * Add tracing interceptors to client builder.
     *
     * @param okBuilder client builder
     * @param tracer tracer
     * @param decorators span decorators
     * @return client builder with added tracing interceptor
     */
    public static OkHttpClient.Builder addTracing(OkHttpClient.Builder okBuilder,
                                                  Tracer tracer, List<OkHttpClientSpanDecorator> decorators) {

        TracingInterceptor tracingInterceptor = new TracingInterceptor(tracer, decorators);
        return okBuilder.addInterceptor(tracingInterceptor)
                    .addNetworkInterceptor(tracingInterceptor);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = null;

        // application interceptor?
        if (chain.connection() == null) {
            ActiveSpan span = tracer.buildSpan(chain.request().method())
                    .withTag(Tags.COMPONENT.getKey(), TracingCallFactory.COMPONENT_NAME)
                    .startActive();

            Request.Builder requestBuilder = chain.request().newBuilder();

            Object tag = chain.request().tag();
            TagWrapper tagWrapper = tag instanceof TagWrapper
                    ? (TagWrapper) tag : new TagWrapper(tag);
            requestBuilder.tag(new TagWrapper(tagWrapper, span));

            try {
                response = chain.proceed(requestBuilder.build());
            } catch (Throwable ex) {
                for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                    spanDecorator.onError(ex, span);
                }
                throw ex;
            } finally {
                span.deactivate();
            }
        } else {
            Object tag = chain.request().tag();
            if (tag instanceof TagWrapper) {
                TagWrapper tagWrapper = (TagWrapper) tag;
                response = new TracingCallFactory.NetworkInterceptor(tracer, tagWrapper.getSpan().context(), decorators)
                        .intercept(chain);
            } else {
                log.severe("tag is null or not an instance of TagWrapper, skipping decorator onResponse()");
            }
        }

        return response;
    }

}
