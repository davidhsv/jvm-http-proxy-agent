package tech.httptoolkit.javaagent

import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.Advice
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatchers.*


class JavaClientTransformer(logger: TransformationLogger): MatchingAgentTransformer(logger) {
    override fun register(builder: AgentBuilder): AgentBuilder {
        return builder
            .type(
                hasSuperType(named("java.net.http.HttpClient"))
            ).and(
                not(isInterface())
            ).transform(this)
    }

    override fun transform(builder: DynamicType.Builder<*>, loadAdvice: (String) -> Advice): DynamicType.Builder<*> {
        return builder
            .visit(loadAdvice("ReturnProxySelectorAdvice")
                .on(hasMethodName("proxy")))
            .visit(loadAdvice("ReturnSslContextAdvice")
                .on(hasMethodName("sslContext")))
            .visit(loadAdvice("ReturnSslContextAdvice")
                .on(hasMethodName("theSSLContext")))
    }
}