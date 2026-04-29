package org.techbd.ingest.service.soap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.SoapResponseUtil;
import org.techbd.ingest.util.TemplateLogger;

@ExtendWith(MockitoExtension.class)
class SoapResponseStrategyFactoryTest {

    @Mock private SoapResponseUtil soapResponseUtil;
    @Mock private AppLogger appLogger;
    @Mock private TemplateLogger templateLogger;

    private SoapResponseStrategyFactory factory;

    @BeforeEach
    void setup() {
        when(appLogger.getLogger(any())).thenReturn(templateLogger);
        factory = new SoapResponseStrategyFactory(soapResponseUtil, appLogger);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void shouldResolveDefaultStrategy_whenResponseTypeAbsentOrBlank(String responseType) {
        SoapResponseStrategy strategy = factory.resolve(responseType);
        assertInstanceOf(DefaultSoapResponseStrategy.class, strategy);
    }

    @Test
    void shouldResolveDefaultStrategy_whenResponseTypeUnknown() {
        SoapResponseStrategy strategy = factory.resolve("unknown_type");
        assertInstanceOf(DefaultSoapResponseStrategy.class, strategy);
    }

    @Test
    void shouldResolveMtomStrategy_whenResponseTypeMtom() {
        SoapResponseStrategy strategy = factory.resolve("mtom");
        assertInstanceOf(MtomSoapResponseStrategy.class, strategy);
    }

    @Test
    void shouldResolveMtomStrategy_caseInsensitive() {
        assertInstanceOf(MtomSoapResponseStrategy.class, factory.resolve("MTOM"));
        assertInstanceOf(MtomSoapResponseStrategy.class, factory.resolve("Mtom"));
        assertInstanceOf(MtomSoapResponseStrategy.class, factory.resolve("  mtom  "));
    }

    @Test
    void shouldResolveTruBridgeStrategy_whenResponseTypeMtomTrubridge() {
        SoapResponseStrategy strategy = factory.resolve("mtom_trubridge");
        assertInstanceOf(TruBridgeMtomSoapResponseStrategy.class, strategy);
    }

    @Test
    void shouldResolveTruBridgeStrategy_caseInsensitive() {
        assertInstanceOf(TruBridgeMtomSoapResponseStrategy.class,
                factory.resolve("MTOM_TRUBRIDGE"));
        assertInstanceOf(TruBridgeMtomSoapResponseStrategy.class,
                factory.resolve("Mtom_TruBridge"));
        assertInstanceOf(TruBridgeMtomSoapResponseStrategy.class,
                factory.resolve("  mtom_trubridge  "));
    }

    @Test
    void shouldReturnSameInstance_forRepeatedCalls() {
        // Strategies are singletons — same instance returned each call
        SoapResponseStrategy first  = factory.resolve("mtom");
        SoapResponseStrategy second = factory.resolve("mtom");
        assertSame(first, second);

        SoapResponseStrategy tb1 = factory.resolve("mtom_trubridge");
        SoapResponseStrategy tb2 = factory.resolve("mtom_trubridge");
        assertSame(tb1, tb2);
    }
}