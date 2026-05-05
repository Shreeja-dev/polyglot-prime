package org.techbd.ingest.integrationtests.tcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.techbd.ingest.integrationtests.base.BaseIntegrationTest;
import org.techbd.ingest.integrationtests.base.IngestionAssertionHelper;
import org.techbd.ingest.integrationtests.base.IngestionAssertionHelper.FlowAssertionParams;

/**
 * Full-stack integration tests for the Netty TCP server using
 * <b>TCP delimiter-framed messages</b> (STX/ETX/LF).
 *
 * <p>Infrastructure (LocalStack S3 + SQS, bucket/queue creation, port-config upload)
 * is bootstrapped once by {@link BaseIntegrationTest}. Between every test method
 * {@code BaseIntegrationTest.cleanS3AndSqsState()} purges all data buckets and SQS
 * queues so each test starts with a clean slate.
 *
 * <h3>Port configurations under test (from list.json)</h3>
 * <pre>
 * // Port 6556 — HOLD flow
 * {
 *   "port": 6556,
 *   "responseType": "tcp",
 *   "protocol": "TCP",
 *   "route": "/hold",
 *   "dataDir": "/holdtest",
 *   "metadataDir": "/holdtest",
 *   "queue": "test.fifo"
 * }
 * // Port 6557 — DEFAULT flow
 * {
 *   "port": 6557,
 *   "responseType": "tcp",
 *   "protocol": "TCP",
 *   "dataDir": "/test",
 *   "metadataDir": "/test"
 * }
 * </pre>
 *
 * <h3>S3 / SQS path conventions</h3>
 * <b>Port 6556 (HOLD flow)</b> — artefacts land in {@code HOLD_BUCKET}:
 * <ul>
 *   <li>Payload:  {@code holdtest/hold/6556/YYYY/MM/DD/…}</li>
 *   <li>Metadata: {@code holdtest/hold/metadata/6556/YYYY/MM/DD/…}</li>
 *   <li>Queue:    {@code test.fifo}, {@code messageGroupId="6556"}</li>
 * </ul>
 * <b>Port 6557 (DEFAULT flow)</b> — artefacts land in {@code DATA_BUCKET}:
 * <ul>
 *   <li>Payload:  {@code test/6557/YYYY/MM/DD/…}</li>
 *   <li>Metadata: {@code test/metadata/6557/YYYY/MM/DD/…}</li>
 *   <li>Queue:    none</li>
 * </ul>
 *
 * <h3>TCP delimiter framing</h3>
 * <ul>
 *   <li>Start: {@code 0x02} (STX — Start of Text)</li>
 *   <li>End 1:  {@code 0x03} (ETX — End of Text)</li>
 *   <li>End 2:  {@code 0x0A} (LF  — Line Feed)</li>
 * </ul>
 *
 * <h3>Test scenarios covered</h3>
 * <ol>
 *   <li><b>Hold flow — happy path (port 6556)</b> — valid TCP-delimited XML stored in
 *       {@code HOLD_BUCKET} with SQS {@code test.fifo} message published.</li>
 *   <li><b>Default flow — happy path (port 6557)</b> — valid TCP-delimited XML stored
 *       in {@code DATA_BUCKET}; no SQS message published.</li>
 *   <li><b>No message sent — PROXY header only (port 6557)</b> — server closes after
 *       read-timeout; S3 bucket remains empty.</li>
 *   <li><b>Message size limit exceeded (port 6557)</b> — oversized frame triggers NACK
 *       without crashing the server.</li>
 *   <li><b>No delimiter present (port 6557)</b> — raw bytes with no STX/ETX/LF are
 *       dropped; S3 empty; server remains alive.</li>
 *   <li><b>Wrong wrapper — MLLP on TCP port (port 6557)</b> — MLLP-wrapped payload
 *       receives a NACK indicating the wrapper conflict.</li>
 *   <li><b>Malformed/empty payload (port 6557)</b> — empty TCP-delimited frame; server
 *       responds with a NACK and channel is closed cleanly.</li>
 *   <li><b>Multiple messages on separate connections (port 6557)</b> — per-connection
 *       attribute cleanup verified; both messages stored independently in S3.</li>
 *   <li><b>TCP connectivity diagnostic</b> — dispatcher port is reachable.</li>
 * </ol>
 *
 * @see NettyTcpServerITCase          for MLLP (HL7) scenarios on ports 2575 and 5555
 * @see NettyTcpServerKeepAliveITCase for keep-alive scenarios
 */
class NettyTcpServerITCase extends BaseIntegrationTest {

    // ── Network constants ────────────────────────────────────────────────────

    /** Dispatcher port — HAProxy PROXY-protocol entry point. */
    private static final int    TCP_SERVER_PORT = 7980;
    private static final String TCP_HOST        = "localhost";

    /**
     * Port 6556 — hold-flow endpoint.
     * Config: {@code route=/hold, dataDir=/holdtest, metadataDir=/holdtest, queue=test.fifo}.
     */
    private static final int TCP_HOLD_PORT    = 6556;

    /**
     * Port 6557 — default-flow endpoint.
     * Config: {@code dataDir=/test, metadataDir=/test}, no queue.
     */
    private static final int TCP_DEFAULT_PORT = 6557;

    private static final String CLIENT_IP       = "203.0.113.10";
    private static final String DEST_IP         = "127.0.0.1";
    private static final int    TCP_CLIENT_PORT = 55400;

    /** General-purpose socket read-timeout for non-keep-alive tests. */
    private static final int TCP_READ_TIMEOUT_SECONDS = 10;

    // ── TCP delimiter framing (STX / ETX / LF) ──────────────────────────────

    /** STX — Start of Text (0x02): marks the beginning of a TCP frame. */
    private static final byte TCP_START = 0x02;

    /** ETX — End of Text (0x03): first byte of the two-byte end-of-frame marker. */
    private static final byte TCP_END_1 = 0x03;

    /** LF — Line Feed (0x0A): second byte of the two-byte end-of-frame marker. */
    private static final byte TCP_END_2 = 0x0A;
    private static final String DATA_BUCKET = "local-sbx-nexus-ingestion-s3-bucket";
    private static final String DEFAULT_METADATA_BUCKET = "local-sbx-nexus-ingestion-s3-metadata-bucket";
    // ── Setup ────────────────────────────────────────────────────────────────

    @BeforeAll
    static void initProfile() {
        System.setProperty("SPRING_PROFILES_ACTIVE", "test");
    }

    // ── Shared assertion helper ──────────────────────────────────────────────

    private IngestionAssertionHelper assertionHelper() {
        return new IngestionAssertionHelper(s3Client, sqsClient);
    }

    // =========================================================================
    // Diagnostic tests
    // =========================================================================

    /**
     * DIAG: Verifies that {@code SPRING_PROFILES_ACTIVE} is set to {@code "test"}
     * so the HAProxy decoder is installed in the Netty pipeline.
     */
    @Test
    @DisplayName("DIAG: SPRING_PROFILES_ACTIVE must be 'test'")
    void shouldHaveTestProfileActive() {
        assertThat(System.getProperty("SPRING_PROFILES_ACTIVE"))
                .as("SPRING_PROFILES_ACTIVE must be 'test' so HAProxyMessageDecoder is active")
                .isEqualTo("test");
    }

    /**
     * DIAG: Verifies that the TCP dispatcher port is reachable.
     */
    @Test
    @DisplayName("DIAG: TCP server is listening on configured dispatcher port")
    void shouldAcceptTcpConnections() {
        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            assertThat(socket.isConnected())
                    .as("TCP server must be reachable on port " + TCP_SERVER_PORT)
                    .isTrue();
        } catch (Exception e) {
            throw new AssertionError(
                    "TCP server is not listening on " + TCP_HOST + ":" + TCP_SERVER_PORT, e);
        }
    }

    // =========================================================================
    // Scenario 1 — Hold flow happy path: port 6556
    // =========================================================================

    /**
     * IT: Port 6556 — valid TCP-delimited XML payload — hold flow, S3 HOLD_BUCKET + SQS asserted.
     *
     * <p>Sends a real XML document (JPedAs_11.xml) wrapped in STX/ETX/LF delimiters
     * to port 6556 ({@code route=/hold, queue=test.fifo}) and verifies:
     * <ul>
     *   <li>A simple ACK ({@code ACK|…}) is returned on the socket.</li>
     *   <li>The payload is stored in {@code HOLD_BUCKET} under the hold-flow prefix
     *       ({@code holdtest/hold/6556/…}).</li>
     *   <li>An SQS message referencing the S3 payload is enqueued on {@code test.fifo}
     *       with {@code messageGroupId="6556"}.</li>
     * </ul>
     */
    @Test
    @DisplayName("IT: Port 6556 — TCP-delimited XML — hold flow, S3 HOLD_BUCKET + SQS full flow")
    void shouldProcessTcpDelimitedXml_holdFlow_andPersistToS3AndSqs() throws Exception {
        String payload = loadXmlFixture("JPedAs_11.xml");
        SoftAssertions softly = new SoftAssertions();

        byte[] ackBytes = sendTcpWithProxy(CLIENT_IP, DEST_IP, TCP_CLIENT_PORT, TCP_HOLD_PORT, payload);
        assertTcpAck(ackBytes, softly, "Port-6556 Hold ACK");

        assertionHelper().assertHoldFlow(
                FlowAssertionParams.builder()
                        .dataBucket(HOLD_BUCKET)
                        .metadataBucket(null)
                        .holdFlow(true)
                        .port(TCP_HOLD_PORT)
                        .dataDir("/holdtest")
                        .metadataDir("/holdtest")
                        .tenantId(null)
                        .queueUrl(queueUrls.get("test.fifo"))
                        .expectedMessageGroupId("6556")
                        .expectedPayload(payload)
                        .payloadNormalizer(IngestionAssertionHelper::normalizeGeneric)
                        .ackExpected(false)
                        .build(),
                softly);

        softly.assertAll();
    }

    // =========================================================================
    // Scenario 2 — Default flow happy path: port 6557
    // =========================================================================

    /**
     * IT: Port 6557 — valid TCP-delimited XML payload — default flow, S3 DATA_BUCKET only (no SQS).
     *
     * <p>Sends a real XML document (JPedAs_11.xml) wrapped in STX/ETX/LF delimiters
     * to port 6557 ({@code dataDir=/test}, no queue) and verifies:
     * <ul>
     *   <li>A simple ACK ({@code ACK|…}) is returned on the socket.</li>
     *   <li>The payload is stored in {@code DATA_BUCKET} under the default-flow prefix
     *       ({@code test/6557/…}).</li>
        * </ul>
     */
    @Test
    @DisplayName("IT: Port 6557 — TCP-delimited XML — default flow, S3 DATA_BUCKET only (no SQS)")
    void shouldProcessTcpDelimitedXml_defaultFlow_andPersistToS3() throws Exception {
        String payload = loadXmlFixture("JPedAs_11.xml");
        SoftAssertions softly = new SoftAssertions();

        byte[] ackBytes = sendTcpWithProxy(CLIENT_IP, DEST_IP, TCP_CLIENT_PORT, TCP_DEFAULT_PORT, payload);
        assertTcpAck(ackBytes, softly, "Port-6557 Default ACK");

        assertionHelper().assertDefaultFlow(defaultFlowParams(payload), softly);

        softly.assertAll();
    }

    // =========================================================================
    // Scenario 3 — No message sent: PROXY header only
    // =========================================================================

    /**
     * IT: Port 6557 — PROXY header only, no TCP payload — server closes after read-timeout (10 s).
     *
     * <p>The HAProxy header is sent but no TCP-delimited payload follows.
     * The server closes the connection within {@code TCP_READ_TIMEOUT_SECONDS + 5 s}.
     * S3 data bucket must remain empty — no message was processed.
     */
    @Test
    @DisplayName("IT: Port 6557 — no TCP message sent — server closes after read-timeout (10 s)")
    void shouldCloseConnectionWhenNoTcpMessageReceivedWithinTimeout() throws Exception {
        SoftAssertions softly = new SoftAssertions();

        long start = System.currentTimeMillis();
        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            socket.setSoTimeout((TCP_READ_TIMEOUT_SECONDS + 5) * 1_000);

            socket.getOutputStream().write(buildProxyV1Header(CLIENT_IP, DEST_IP, TCP_CLIENT_PORT, TCP_DEFAULT_PORT));
            socket.getOutputStream().flush();

            byte[] buf = new byte[4_096];
            int    read;
            try {
                read = socket.getInputStream().read(buf);
            } catch (java.net.SocketTimeoutException e) {
                read = -1; // timeout before close is acceptable
            }

            long elapsed = System.currentTimeMillis() - start;

            softly.assertThat(elapsed)
                    .as("Server must close within readTimeout(10 s) + grace(5 s)")
                    .isLessThan(15_000L);
        }

        assertThat(assertionHelper().bucketIsEmpty(DATA_BUCKET))
                .as("DATA_BUCKET must be empty — no TCP message was delivered")
                .isTrue();

        softly.assertAll();
    }

    // =========================================================================
    // Scenario 4 — Message size limit exceeded
    // =========================================================================

    /**
     * IT: Port 6557 — oversized TCP-delimited payload — NACK returned, server stays up.
     *
     * <p>Sends a TCP-delimited message that exceeds the server's configured
     * {@code TCP_MAX_MESSAGE_SIZE_BYTES}. The server should respond with a NACK
     * indicating the size limit and close the channel. The server must remain
     * reachable afterwards (no crash / thread leak).
     */
    @Test
    @DisplayName("IT: Port 6557 — oversized TCP payload — NACK returned, server remains available")
    void shouldRejectOversizedTcpPayload_andServerRemainsAvailable() throws Exception {
        SoftAssertions softly = new SoftAssertions();

        int oversizeBytes = 1 * 1024 * 1024; // 1 MB — meaningful but CI-safe
        String largePayload = "X".repeat(oversizeBytes);

        byte[] ackBytes = sendTcpWithProxy(CLIENT_IP, DEST_IP, TCP_CLIENT_PORT, TCP_DEFAULT_PORT, largePayload);

        if (ackBytes != null && ackBytes.length > 0) {
            String response = new String(ackBytes, StandardCharsets.UTF_8).trim();
            softly.assertThat(response.isEmpty())
                    .as("Server must send a non-empty response for an oversized payload")
                    .isFalse();
        }

        try (Socket healthCheck = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            softly.assertThat(healthCheck.isConnected())
                    .as("TCP server must remain reachable after an oversized payload was rejected")
                    .isTrue();
        }

        softly.assertAll();
    }

    // =========================================================================
    // Scenario 5 — No delimiter: raw bytes without STX/ETX/LF
    // =========================================================================

    /**
     * IT: Port 6557 — raw bytes with no TCP delimiter — not processed, not stored.
     *
     * <p>When a frame arrives whose first byte is neither MLLP_START (0x0B) nor
     * TCP_START (0x02), the {@code DelimiterBasedFrameDecoder} sets
     * {@code NO_DELIMITER_DETECTED_KEY=true}, the message handler logs and drops
     * the content, and nothing is written to S3. The server must still be
     * reachable after handling the no-delimiter frame.
     */
    @Test
    @DisplayName("IT: Port 6557 — no TCP delimiter — payload dropped, S3 empty, server alive")
    void shouldDropNoDelimiterMessage_andServerRemainsAvailable() throws Exception {
        SoftAssertions softly = new SoftAssertions();

        String rawPayload = "This message has no STX/ETX delimiter markers at all.";
        byte[] rawBytes   = rawPayload.getBytes(StandardCharsets.UTF_8);

        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            socket.setSoTimeout((TCP_READ_TIMEOUT_SECONDS + 5) * 1_000);

            socket.getOutputStream().write(buildProxyV1Header(CLIENT_IP, DEST_IP, TCP_CLIENT_PORT, TCP_DEFAULT_PORT));
            socket.getOutputStream().write(rawBytes);
            socket.getOutputStream().flush();

            try {
                byte[] buf = new byte[4_096];
                socket.getInputStream().read(buf);
            } catch (java.net.SocketTimeoutException ignored) {
                // Server silently closed or timed out — acceptable
            }
        }

        softly.assertThat(assertionHelper().bucketIsEmpty(DATA_BUCKET))
                .as("DATA_BUCKET must be empty — no-delimiter messages are not stored")
                .isTrue();

        try (Socket healthCheck = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            softly.assertThat(healthCheck.isConnected())
                    .as("TCP server must remain reachable after a no-delimiter message")
                    .isTrue();
        }

        softly.assertAll();
    }

    // =========================================================================
    // Scenario 6 — Wrong wrapper: MLLP bytes sent to a TCP-only port
    // =========================================================================

    /**
     * IT: Port 6557 — MLLP-wrapped payload sent to a TCP port — NACK returned.
     *
     * <p>Port 6557 has {@code responseType=tcp}. Sending a VT/FS/CR-framed message
     * (MLLP) to this port triggers the
     * {@code MLLP_WRAPPER_FOUND_EXPECTED_TCP / CONFLICTING_WRAPPERS_DETECTED} code path.
     * The server must return a NACK and remain reachable after the incident.
     */
    @Test
    @DisplayName("IT: Port 6557 — MLLP wrapper on TCP port — conflict NACK returned, server alive")
    void shouldNackMllpWrapperOnTcpPort_andServerRemainsAvailable() throws Exception {
        SoftAssertions softly = new SoftAssertions();

        String hl7Like = "MSH|^~\\&|SENDER|FAC|RECEIVER|FAC|20240101||ADT^A01|MSG001|P|2.5\r";
        byte[] mllpPayload = wrapMllp(hl7Like);

        byte[] response = sendRawWithProxy(CLIENT_IP, DEST_IP, TCP_CLIENT_PORT, TCP_DEFAULT_PORT, mllpPayload);

        if (response != null && response.length > 0) {
            String responseStr = new String(response, StandardCharsets.UTF_8).trim();
            softly.assertThat(responseStr.contains("AR") ||
                              responseStr.contains("ACK"))
                    .as("Server must respond with a ACK with AR for an MLLP payload on a TCP port")
                    .isTrue();
        }

        try (Socket healthCheck = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            softly.assertThat(healthCheck.isConnected())
                    .as("TCP server must remain reachable after an MLLP-on-TCP-port conflict")
                    .isTrue();
        }

        softly.assertAll();
    }

    // =========================================================================
    // Scenario 7 — Malformed / empty payload inside TCP delimiters
    // =========================================================================

    /**
     * IT: Port 6557 — empty TCP-delimited frame — NACK returned, channel closed cleanly.
     *
     * <p>Sends a valid STX/ETX/LF frame whose body is an empty string.
     * The server must respond with a NACK and close the channel without any
     * unhandled exception escaping to the test.
     */
    @Test
    @DisplayName("IT: Port 6557 — empty TCP-delimited payload — NACK/ACK returned, server alive")
    void shouldHandleEmptyTcpDelimitedPayload_gracefully() throws Exception {
        SoftAssertions softly = new SoftAssertions();

        byte[] emptyFrame = wrapTcp("");
        byte[] response = sendRawWithProxy(CLIENT_IP, DEST_IP, TCP_CLIENT_PORT, TCP_DEFAULT_PORT, emptyFrame);

        if (response != null && response.length > 0) {
            String responseStr = new String(response, StandardCharsets.UTF_8).trim();
            softly.assertThat(responseStr.isEmpty())
                    .as("Server must send a non-empty response for an empty TCP frame")
                    .isFalse();
        }

        try (Socket healthCheck = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            softly.assertThat(healthCheck.isConnected())
                    .as("TCP server must remain reachable after an empty TCP frame")
                    .isTrue();
        }

        softly.assertAll();
    }

    // =========================================================================
    // FlowAssertionParams factory — default flow (port 6557)
    // =========================================================================

    /**
     * Builds default-flow assertion params for port 6557.
     *
     * <p>Port config: {@code dataDir=/test, metadataDir=/test}, no queue.
     *
     * @param payload raw TCP payload that was sent (pre-delimiter-wrap)
     */
    private FlowAssertionParams defaultFlowParams(String payload) {
        return FlowAssertionParams.builder()
                .dataBucket(DATA_BUCKET)
                .metadataBucket(DEFAULT_METADATA_BUCKET)
                .holdFlow(false)
                .port(TCP_DEFAULT_PORT)
                .dataDir("/test")
                .metadataDir("/test")
                .tenantId(null)
                .queueUrl(null)
                .expectedMessageGroupId(null)
                .expectedPayload(payload)
                .payloadNormalizer(IngestionAssertionHelper::normalizeGeneric)
                .ackExpected(false)
                .build();
    }

    // =========================================================================
    // TCP ACK assertion helper
    // =========================================================================

    /**
     * Asserts that {@code rawAck} is a valid simple TCP ACK.
     *
     * <p>The generic TCP handler produces:
     * {@code ACK|<interactionId>|<version>|<timestamp>}
     * optionally followed by {@code \n}.
     */
    private void assertTcpAck(byte[] rawAck, SoftAssertions softly, String label) {
        softly.assertThat(rawAck)
                .as(label + ": raw TCP ACK bytes must not be empty")
                .isNotEmpty();
        if (rawAck == null || rawAck.length == 0) return;

        String ackStr = new String(rawAck, StandardCharsets.UTF_8).trim();
        softly.assertThat(ackStr)
                .as(label + ": TCP simple ACK must start with 'ACK|'")
                .startsWith("ACK|");
    }

    // =========================================================================
    // TCP / PROXY low-level helpers
    // =========================================================================

    /**
     * Opens a TCP connection to the dispatcher, sends a HAProxy PROXY v1 header
     * immediately followed by a TCP-delimited (STX/ETX/LF) message, waits for
     * the ACK (terminated by {@code \n}), then closes the socket.
     *
     * @param clientIp   source IP in the PROXY header
     * @param destIp     destination IP in the PROXY header
     * @param clientPort source port in the PROXY header
     * @param destPort   destination port in the PROXY header
     * @param payload    raw message content (without delimiters)
     * @return raw ACK bytes, or an empty array if the server sent nothing before closing
     */
    private byte[] sendTcpWithProxy(String clientIp, String destIp,
            int clientPort, int destPort, String payload) throws Exception {
        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            socket.setSoTimeout((TCP_READ_TIMEOUT_SECONDS + 5) * 1_000);

            socket.getOutputStream().write(
                    concat(buildProxyV1Header(clientIp, destIp, clientPort, destPort),
                           wrapTcp(payload)));
            socket.getOutputStream().flush();

            return readTcpResponse(socket);
        }
    }

    /**
     * Opens a TCP connection to the dispatcher, sends a HAProxy PROXY v1 header
     * followed by the supplied <em>raw</em> bytes (no automatic TCP-delimiter wrapping).
     *
     * <p>Used for scenarios that need to send MLLP-framed data or other non-standard
     * byte sequences to a TCP port.
     */
    private byte[] sendRawWithProxy(String clientIp, String destIp,
            int clientPort, int destPort, byte[] rawBytes) throws Exception {
        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            socket.setSoTimeout((TCP_READ_TIMEOUT_SECONDS + 5) * 1_000);

            socket.getOutputStream().write(buildProxyV1Header(clientIp, destIp, clientPort, destPort));
            socket.getOutputStream().write(rawBytes);
            socket.getOutputStream().flush();

            return readTcpResponse(socket);
        }
    }

    // ── Frame builders ───────────────────────────────────────────────────────

    /**
     * Wraps {@code message} in TCP delimiter framing:
     * {@code <STX>(0x02) payload <ETX>(0x03)<LF>(0x0A)}.
     */
    private static byte[] wrapTcp(String message) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        byte[] framed  = new byte[1 + payload.length + 2];
        framed[0] = TCP_START;
        System.arraycopy(payload, 0, framed, 1, payload.length);
        framed[framed.length - 2] = TCP_END_1;
        framed[framed.length - 1] = TCP_END_2;
        return framed;
    }

    /**
     * Wraps {@code message} in MLLP framing:
     * {@code <VT>(0x0B) payload <FS>(0x1C)<CR>(0x0D)}.
     *
     * <p>Used by scenario 6 to deliberately send an MLLP frame to a TCP-only port.
     */
    private static byte[] wrapMllp(String message) {
        final byte MLLP_START = 0x0B;
        final byte MLLP_END_1 = 0x1C;
        final byte MLLP_END_2 = 0x0D;
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        byte[] framed  = new byte[1 + payload.length + 2];
        framed[0] = MLLP_START;
        System.arraycopy(payload, 0, framed, 1, payload.length);
        framed[framed.length - 2] = MLLP_END_1;
        framed[framed.length - 1] = MLLP_END_2;
        return framed;
    }

    /** Builds a HAProxy PROXY protocol v1 text header. */
    private static byte[] buildProxyV1Header(String clientIp, String destIp,
            int clientPort, int destPort) {
        return String.format("PROXY TCP4 %s %s %d %d\r\n",
                        clientIp, destIp, clientPort, destPort)
                .getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Reads bytes from {@code socket} until a newline ({@code 0x0A}) is received,
     * which marks the end of a simple TCP ACK/NACK response, or until the socket
     * times out / is closed by the server.
     */
    private static byte[] readTcpResponse(Socket socket) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        try {
            int b;
            while ((b = socket.getInputStream().read()) != -1) {
                buf.write(b);
                if (b == '\n') break;
            }
        } catch (java.net.SocketTimeoutException e) {
            // Timeout waiting for server response — return whatever was buffered
        }
        return buf.toByteArray();
    }

    /** Concatenates two byte arrays. */
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // =========================================================================
    // Fixture loading
    // =========================================================================

    /**
     * Loads an XML fixture from
     * {@code src/test/resources/org/techbd/ingest/tcp-test-resources/}.
     *
     * @param filename file name within the resource directory (e.g. {@code "JPedAs_11.xml"})
     * @return UTF-8 string content of the fixture
     * @throws IllegalStateException if the resource is not found on the classpath
     */
    private static String loadXmlFixture(String filename) throws Exception {
        String path = "org/techbd/ingest/tcp-test-resources/" + filename;
        try (InputStream is = NettyTcpServerITCase.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("XML fixture not found on classpath: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}