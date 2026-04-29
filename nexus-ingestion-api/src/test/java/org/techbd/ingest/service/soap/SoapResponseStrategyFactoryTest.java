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
        SoapResponseStrategy strategy = factory.resolve(responseType, "INT-1","/ws",false);
        assertInstanceOf(DefaultSoapResponseStrategy.class, strategy);
    }

    @Test
    void shouldResolveDefaultStrategy_whenResponseTypeUnknown() {
        SoapResponseStrategy strategy = factory.resolve("unknown_type", "INT-1", "/ws",false);
        assertInstanceOf(DefaultSoapResponseStrategy.class, strategy);
    }

    @Test
    void shouldResolveMtomStrategy_whenResponseTypeMtom() {
        SoapResponseStrategy strategy = factory.resolve("mtom", "INT-1", "/ws",false);
        assertInstanceOf(MtomSoapResponseStrategy.class, strategy);
    }

    @Test
    void shouldResolveMtomStrategy_caseInsensitive() {
        assertInstanceOf(MtomSoapResponseStrategy.class, factory.resolve("MTOM", "INT-1", "/ws",false));
        assertInstanceOf(MtomSoapResponseStrategy.class, factory.resolve("Mtom", "INT-1", "/ws",false));
        assertInstanceOf(MtomSoapResponseStrategy.class, factory.resolve("  mtom  ", "INT-1", "/ws",false));
    }

    @Test
    void shouldResolveTruBridgeStrategy_whenResponseTypeMtomTrubridge() {
        SoapResponseStrategy strategy = factory.resolve("mtom_trubridge", "INT-1", "/ws",false);
        assertInstanceOf(TruBridgeMtomSoapResponseStrategy.class, strategy);
    }

    @Test
    void shouldResolveTruBridgeStrategy_caseInsensitive() {
        assertInstanceOf(TruBridgeMtomSoapResponseStrategy.class,
                factory.resolve("MTOM_TRUBRIDGE", "INT-1", "/ws",false));
        assertInstanceOf(TruBridgeMtomSoapResponseStrategy.class,
                factory.resolve("Mtom_TruBridge", "INT-1", "/ws",false));
        assertInstanceOf(TruBridgeMtomSoapResponseStrategy.class,
                factory.resolve("  mtom_trubridge  ", "INT-1", "/ws",false));
    }

    @Test
    void shouldReturnSameInstance_forRepeatedCalls() {
        // Strategies are singletons — same instance returned each call
        SoapResponseStrategy first  = factory.resolve("mtom", "INT-1", "/ws",false);
        SoapResponseStrategy second = factory.resolve("mtom", "INT-1", "/ws",false);
        assertSame(first, second);

        SoapResponseStrategy tb1 = factory.resolve("mtom_trubridge", "INT-1", "/ws",false);
        SoapResponseStrategy tb2 = factory.resolve("mtom_trubridge", "INT-1", "/ws",false);
        assertSame(tb1, tb2);
    }

    @Test
    void shouldResolveDefaultStrategy_whenRequestUriIsXDSbRepositoryWS() {
        // When requestUri matches XDSbRepositoryWS, should return default strategy regardless of responseType
        SoapResponseStrategy strategy = factory.resolve("mtom", "INT-1", "/xds/XDSbRepositoryWS",false);
        assertInstanceOf(DefaultSoapResponseStrategy.class, strategy);
    }

    @Test
    void shouldResolveDefaultStrategy_whenRequestUriIsXDSbRepositoryAndResponseTypeMtom() {
        // requestUri check should be case-insensitive
        SoapResponseStrategy strategy = factory.resolve("mtom", "INT-1", "/XDS/XDSbRepositoryWS",false);
        assertInstanceOf(DefaultSoapResponseStrategy.class, strategy);
    }
    @Test
    void shouldResolveDefaultStrategy_whenRequestUriIsXDSbRepositoryAndResponseTypeMtomTruBridge() {
        // requestUri check should be case-insensitive
        SoapResponseStrategy strategy = factory.resolve("mtom_trubridge", "INT-1", "/XDS/XDSbRepositoryWS",false);
        assertInstanceOf(DefaultSoapResponseStrategy.class, strategy);
    }
    @Test
    void shouldResolveDefaultStrategy_whenRequestIsPix() {
        // requestUri check should be case-insensitive
        SoapResponseStrategy strategy = factory.resolve("mtom_trubridge", "INT-1", "/ws",true);
        assertInstanceOf(DefaultSoapResponseStrategy.class, strategy);
    }
    @Test
    void shouldResolveMtomStrategy_whenRequestUriIsNotXDSbRepositoryWS() {
        // When requestUri is different, should resolve based on responseType
        SoapResponseStrategy strategy = factory.resolve("mtom", "INT-1", "/other/Service",false);
        assertInstanceOf(MtomSoapResponseStrategy.class, strategy);
    }
}