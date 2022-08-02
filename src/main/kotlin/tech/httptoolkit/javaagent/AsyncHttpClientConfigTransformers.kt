package tech.httptoolkit.javaagent

import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.Advice
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatchers.*

// For new clients, we just need to override the properties on the convenient config
// class that contains both proxy & SSL configuration.
class AsyncHttpClientConfigTransformer(logger: TransformationLogger) : MatchingAgentTransformer(logger) {
    override fun register(builder: AgentBuilder): AgentBuilder {
        return builder
            .type(
                hasSuperType(named("org.asynchttpclient.AsyncHttpClientConfig"))
            ).and(
                not(isInterface())
            ).transform(this)
    }

    override fun transform(builder: DynamicType.Builder<*>, loadAdvice: (String) -> Advice): DynamicType.Builder<*> {
        return builder
            .visit(
                loadAdvice("AsyncHttpClientReturnSslContextAdvice")
                    .on(hasMethodName("getSslContext")))
            .visit(
                loadAdvice("AsyncHttpClientReturnProxySelectorAdvice")
                    .on(hasMethodName("getProxyServerSelector")))
    }
}

// For existing classes, we need to hook SSL Handler creation, called for
// every new connection
class AsyncHttpChannelManagerTransformer(logger: TransformationLogger) : MatchingAgentTransformer(logger) {
    override fun register(builder: AgentBuilder): AgentBuilder {
        return builder
            .type(
                named("org.asynchttpclient.netty.channel.ChannelManager")
            ).transform(this)
    }

    override fun transform(builder: DynamicType.Builder<*>, loadAdvice: (String) -> Advice): DynamicType.Builder<*> {
        return builder
            .visit(
                loadAdvice("AsyncHttpResetSslEngineFactoryAdvice")
                    .on(hasMethodName("createSslHandler")))
    }
}