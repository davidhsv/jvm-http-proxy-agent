package tech.httptoolkit.javaagent

import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatchers.*

// To intercept HTTPS, we need to change the trust manager in TLSConfig instances. We don't want
// to mess with server config though, so we clone the config argument and replace it with one that
// uses our custom trust manager instead, every time a new TLS session is opened:
class KtorClientTlsTransformer(logger: TransformationLogger) : MatchingAgentTransformer(logger) {
    override fun register(builder: AgentBuilder): AgentBuilder {
        return builder
            .type(
                named("io.ktor.network.tls.TLSClientSessionJvmKt")
            ).transform(this)
    }

    override fun transform(builder: DynamicType.Builder<*>, loadAdvice: (String) -> Advice): DynamicType.Builder<*> {
        return builder
            .visit(loadAdvice("KtorResetTlsClientTrustAdvice")
                .on(hasMethodName<MethodDescription>("openTLSSession")
                    .and(takesArgument(3, named("io.ktor.network.tls.TLSConfig")))))
    }
}

// Proxy configuration for new clients is easy: we just hook getProxy() in the engine
// configuration to return our proxy.
class KtorClientEngineConfigTransformer(logger: TransformationLogger) : MatchingAgentTransformer(logger) {
    override fun register(builder: AgentBuilder): AgentBuilder {
        return builder
            .type(
                named("io.ktor.client.engine.HttpClientEngineConfig")
            ).transform(this)
    }

    override fun transform(builder: DynamicType.Builder<*>, loadAdvice: (String) -> Advice): DynamicType.Builder<*> {
        return builder
            .visit(loadAdvice("ReturnProxyAdvice")
                .on(hasMethodName("getProxy")))
        }
}

// Proxy configuration for existing clients is only mildly harder: we hook individual engines
// elsewhere anyway, so it shouldn't matter much, but not CIO which is ktor specific. For CIO,
// we just need one more hook that resets the proxy field to the value from config (hooked above)
// before any requests are executed:
class KtorCioEngineTransformer(logger: TransformationLogger) : MatchingAgentTransformer(logger) {
    override fun register(builder: AgentBuilder): AgentBuilder {
        return builder
            .type(
                named("io.ktor.client.engine.cio.CIOEngine")
            ).transform(this)
    }

    override fun transform(builder: DynamicType.Builder<*>, loadAdvice: (String) -> Advice): DynamicType.Builder<*> {
        return builder
            .visit(loadAdvice("KtorResetProxyFieldAdvice")
                .on(hasMethodName("execute")))
    }
}