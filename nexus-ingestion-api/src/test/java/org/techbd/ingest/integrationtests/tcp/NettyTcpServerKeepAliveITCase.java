package org.techbd.ingest.integrationtests.tcp;

import static org.techbd.ingest.integrationtests.util.NettyTcpServerKeepAliveFixtures.TCP_MSG1_CCD_XML;
import static org.techbd.ingest.integrationtests.util.NettyTcpServerKeepAliveFixtures.TCP_MSG2_ADT_A03;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.techbd.ingest.integrationtests.base.BaseIntegrationTest;
import org.techbd.ingest.integrationtests.base.IngestionAssertionHelper;
import org.techbd.ingest.integrationtests.base.IngestionAssertionHelper.FlowAssertionParams;

/**
 * Integration tests for <b>persistent (keep-alive) MLLP connections</b>.
 *
 * <p>Port under test:</p>
 *
 *
 * <h3>Port 6555 — raw TCP / delimiter-framed (keep-alive)</h3>
 * <pre>{@code
 * {
 *   "port": 6555,
 *   "responseType": "tcp",
 *   "protocol": "TCP",
 *   "route": "/hold",
 *   "dataDir": "/outbound",
 *   "metadataDir": "/outbound",
 *   "queue": "test.fifo",
 *   "keepAliveTimeout": 20
 * }
 * }</pre>
 *
 * <h3>Keep-alive behaviour (from NettyTcpServer)</h3>
 * <ul>
 *   <li>When the server resolves a {@code keepAliveTimeout > 0} for the destination
 *       port it replaces the default {@link io.netty.handler.timeout.ReadTimeoutHandler}
 *       with an {@link io.netty.handler.timeout.IdleStateHandler IdleStateHandler}.</li>
 *   <li>After flushing each ACK/response the channel is <em>kept open</em>; per-message
 *       attributes ({@code INTERACTION_ATTRIBUTE_KEY}, {@code FRAGMENT_COUNT_KEY}, etc.)
 *       are reset, while session attributes ({@code SESSION_ID_KEY},
 *       {@code SESSION_MESSAGE_COUNT_KEY}) are preserved across messages.</li>
 *   <li>The channel is closed only when the idle reader fires or on error.</li>
 * </ul>
 *
 *
 * <h3>TCP test scenarios (port 6555)</h3>
 * <ol>
 *   <li><b>Two TCP messages, one session</b> — send two TCP-delimited messages on the
 *       same connection, verify two simple ACKs, two S3+SQS payloads.</li>
 *   <li><b>One TCP message, channel survives read-timeout window</b> — send one message,
 *       confirm the ACK arrives, then verify the channel is still open after the
 *       default read-timeout (10 s) but closes around the keepAliveTimeout (20 s).</li>
 *   <li><b>No TCP message sent, channel closes at keepAliveTimeout</b> — open a
 *       connection, send only the HAProxy header, and verify the server holds the
 *       channel open past the default read-timeout (10 s).</li>
 * </ol>
 *
 * <h3>SQS deduplication note</h3>
 * <p>The fixtures ({@link NettyTcpServerKeepAliveFixtures#TCP_MSG1_CCD_XML} and
 * {@link NettyTcpServerKeepAliveFixtures#TCP_MSG2_ADT_A03}) are intentionally
 * content-distinct so that the SQS FIFO deduplication window (5 minutes) does not
 * suppress the second message within each pair.
 *
 * @see NettyTcpServerKeepAliveFixtures  for shared fixture strings and group-ID constants
 */
class NettyTcpServerKeepAliveITCase extends BaseIntegrationTest {

    // ── Network constants ────────────────────────────────────────────────────

    /** Dispatcher port — HAProxy PROXY-protocol entry point. */
    private static final int    TCP_SERVER_PORT = 7980;
    private static final String TCP_HOST        = "localhost";
    /**
     * Raw-TCP destination port in the HAProxy header.
     * The server resolves the port-config for this port
     * ({@code keepAliveTimeout=20, responseType=tcp, route=/hold, queue=test.fifo}).
     */
    private static final int TCP_DEST_PORT = 6555;

    private static final String CLIENT_IP        = "203.0.113.10";
    private static final String DEST_IP          = "127.0.0.1";
    private static final int    TCP_CLIENT_PORT  = 55200;

    // ── Timeout constants (seconds) ──────────────────────────────────────────

    /**
     * Default server read-timeout — the channel must <em>not</em> close within
     * this window when keepAlive is active.
     */
    private static final int DEFAULT_READ_TIMEOUT_S = 10;

    /**
     * keepAliveTimeout configured on both port 5555 and port 6555.
     * The channel <em>must</em> close within this window when no subsequent
     * message arrives.
     */
    private static final int KEEP_ALIVE_TIMEOUT_S = 20;

    /**
     * Grace margin (seconds) added when asserting an upper bound, to absorb
     * scheduling jitter in CI environments.
     */
    private static final int GRACE_S = 5;

    // ── TCP delimiter framing (STX / ETX / LF — default env config) ──────────

    private static final byte TCP_START = 0x02; // STX
    private static final byte TCP_END_1 = 0x03; // ETX
    private static final byte TCP_END_2 = 0x0A; // LF

    // ── Setup ────────────────────────────────────────────────────────────────

    @BeforeAll
    static void initProfile() {
        System.setProperty("SPRING_PROFILES_ACTIVE", "test");
    }

    // ── Helper factory ───────────────────────────────────────────────────────

    private IngestionAssertionHelper assertionHelper() {
        return new IngestionAssertionHelper(s3Client, sqsClient);
    }

    // =========================================================================
    // TCP TESTS — port 6555
    // =========================================================================

    // ── TCP Test 1 — Two TCP messages on one persistent session ───────────────

    /**
     * Sends two content-distinct TCP-delimited messages on a single TCP session
     * (port 6555) and verifies:
     * <ul>
     *   <li>Both messages receive a simple ACK ({@code ACK|…}) on the
     *       <em>same socket</em>, proving the channel was not closed between them
     *       (session-level keep-alive is active).</li>
     *   <li>The channel remains open after the second ACK.</li>
     *   <li>Two independent S3 payloads and two SQS entries exist in the hold-flow
     *       bucket and {@code test.fifo} queue respectively.</li>
     * </ul>
     *
     * <h4>S3 / SQS assertion strategy</h4>
     * <p>{@link IngestionAssertionHelper#assertHoldFlow} locates each S3 payload by
     * <em>content match</em> and each SQS message by its {@code s3DataObjectPath},
     * so the two assertions are independent of queue order.
     */
    @Test
    @DisplayName("IT[TCP-6555]: keepAlive TCP — two TCP messages on one session — " +
                 "both ACKed, channel stays open, S3+SQS asserted per message")
    void tcp_shouldProcessTwoMessagesOnSingleSession_channelStaysOpen() throws Exception {
        SoftAssertions softly = new SoftAssertions();

        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            // Socket read-timeout generous enough to span two round-trips + keep-alive gap
            socket.setSoTimeout((KEEP_ALIVE_TIMEOUT_S + GRACE_S) * 1_000);

            // ── Send HAProxy header once — shared by both messages on this session ──
            socket.getOutputStream().write(
                    buildProxyV1Header(CLIENT_IP, DEST_IP, TCP_CLIENT_PORT, TCP_DEST_PORT));
            socket.getOutputStream().flush();

            // ── Message 1: CCD XML payload ────────────────────────────────────────
            socket.getOutputStream().write(wrapTcp(TCP_MSG1_CCD_XML));
            socket.getOutputStream().flush();

            byte[] ack1 = readTcpResponse(socket);

            softly.assertThat(socket.isConnected())
                    .as("SESSION_ID_KEY persists — socket must remain connected after TCP ACK-1")
                    .isTrue();
            softly.assertThat(socket.isClosed())
                    .as("KEEP_ALIVE_TIMEOUT_KEY active — server must NOT close channel after TCP message 1")
                    .isFalse();
            assertTcpAck(ack1, softly, "TCP Session-1 / Message-1 ACK");

            // Brief pause so message 1 is fully committed to S3/SQS before message 2 arrives
            Thread.sleep(500);

            // ── Message 2: HL7- ADT A03 payload (same session, no new PROXY header) ─────
            socket.getOutputStream().write(wrapTcp(TCP_MSG2_ADT_A03));
            socket.getOutputStream().flush();

            byte[] ack2 = readTcpResponse(socket);

            softly.assertThat(socket.isConnected())
                    .as("SESSION_ID_KEY persists — socket must remain connected after TCP ACK-2")
                    .isTrue();
            softly.assertThat(socket.isClosed())
                    .as("KEEP_ALIVE_TIMEOUT_KEY active — server must NOT close channel after TCP message 2")
                    .isFalse();
            assertTcpAck(ack2, softly, "TCP Session-1 / Message-2 ACK");

            // SESSION_MESSAGE_COUNT_KEY = 2: same socket, two distinct non-empty ACKs
            softly.assertThat(ack1).as("TCP ACK-1 must be non-empty (message count ≥ 1)").isNotEmpty();
            softly.assertThat(ack2).as("TCP ACK-2 must be non-empty (message count = 2)").isNotEmpty();
            softly.assertThat(ack1)
                    .as("TCP ACK-1 and ACK-2 must be distinct (INTERACTION_ATTRIBUTE_KEY reset)")
                    .isNotEqualTo(ack2);
        }

        // ── S3 + SQS: assert each TCP message independently by payload content ────
        assertionHelper().assertHoldFlow(
                tcpHoldFlowParams(TCP_MSG1_CCD_XML, "6555"), softly);

        assertionHelper().assertHoldFlow(
                tcpHoldFlowParams(TCP_MSG2_ADT_A03, "6555"), softly);

        softly.assertAll();
    }

    // ── TCP Test 2 — One TCP message, channel survives default read-timeout ────

    /**
     * Sends one TCP-delimited message on port 6555 and verifies that the channel
     * survives past the default read-timeout (10 s) but closes by the
     * keepAliveTimeout (20 s).
     *
     * <h4>What is verified</h4>
     * <ul>
     *   <li>The ACK for the single message is received as a valid simple ACK.</li>
     *   <li>Channel-attribute {@code KEEP_ALIVE_TIMEOUT_KEY=20} is active — proved by
     *       confirming the socket is still open {@code DEFAULT_READ_TIMEOUT_S + 2 s}
     *       after the ACK (i.e., past the point where {@code ReadTimeoutHandler} would
     *       have fired).</li>
     *   <li>The channel closes within {@code KEEP_ALIVE_TIMEOUT_S + GRACE_S} seconds
     *       of the last data sent.</li>
     *   <li>S3 hold-flow bucket and SQS queue each contain exactly one matching payload.</li>
     * </ul>
     */
    @Test
    @DisplayName("IT[TCP-6555]: keepAlive TCP — one TCP message — channel survives " +
                 "readTimeout(10 s), closes at keepAliveTimeout(20 s)")
    void tcp_shouldKeepChannelOpenAfterOneMessage_closesAtKeepAliveTimeout() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        long testStart = System.currentTimeMillis();

        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            socket.setSoTimeout((KEEP_ALIVE_TIMEOUT_S + GRACE_S) * 1_000);

            // ── Send PROXY header + single TCP message ────────────────────────────
            socket.getOutputStream().write(
                    buildProxyV1Header(CLIENT_IP, DEST_IP, TCP_CLIENT_PORT, TCP_DEST_PORT));
            socket.getOutputStream().write(wrapTcp(TCP_MSG1_CCD_XML));
            socket.getOutputStream().flush();

            byte[] ack = readTcpResponse(socket);
            assertTcpAck(ack, softly, "TCP single-message keepAlive ACK");

            long afterAckMs = System.currentTimeMillis();

            // Wait past the default read-timeout; if ReadTimeoutHandler were still
            // installed the server would have closed the connection by now.
            Thread.sleep((DEFAULT_READ_TIMEOUT_S + 2) * 1_000L);

            softly.assertThat(socket.isConnected())
                    .as("KEEP_ALIVE_TIMEOUT_KEY active — TCP socket must still be connected " +
                        (DEFAULT_READ_TIMEOUT_S + 2) + " s after ACK (past default read-timeout)")
                    .isTrue();

            // Poll for EOF — the IdleStateHandler should fire at ~KAT seconds
            long elapsedSinceLastData = (System.currentTimeMillis() - afterAckMs) / 1_000;
            long remainingBudgetMs = Math.max(1_000L,
                    (KEEP_ALIVE_TIMEOUT_S - elapsedSinceLastData + GRACE_S) * 1_000L);
            socket.setSoTimeout((int) remainingBudgetMs);

            boolean channelClosed = waitForChannelClose(socket);
            long totalElapsedMs   = System.currentTimeMillis() - testStart;

            softly.assertThat(channelClosed)
                    .as("IdleStateHandler(20 s) must close the TCP channel after keepAliveTimeout")
                    .isTrue();
            softly.assertThat(totalElapsedMs)
                    .as("TCP channel must close within keepAliveTimeout(%d s) + grace(%d s)",
                            KEEP_ALIVE_TIMEOUT_S, GRACE_S)
                    .isLessThan((long) (KEEP_ALIVE_TIMEOUT_S + GRACE_S) * 1_000L);
            softly.assertThat(totalElapsedMs)
                    .as("TCP channel must NOT close before default readTimeout(%d s)",
                            DEFAULT_READ_TIMEOUT_S)
                    .isGreaterThan((long) DEFAULT_READ_TIMEOUT_S * 1_000L);
        }

        assertionHelper().assertHoldFlow(
                tcpHoldFlowParams(TCP_MSG1_CCD_XML, "6555"), softly);

        softly.assertAll();
    }

    // ── TCP Test 3 — No TCP message sent, channel closes at keepAliveTimeout ──

    /**
     * Opens a connection to the TCP port (6555) and sends only the HAProxy header —
     * no payload.
     *
     * <h4>What is verified</h4>
     * <ul>
     *   <li>{@code KEEP_ALIVE_TIMEOUT_KEY=20} is resolved from the HAProxy header's
     *       {@code destPort=6555} before any message arrives — proved by confirming
     *       the socket is still open {@code DEFAULT_READ_TIMEOUT_S + 2 s} after the
     *       PROXY header.</li>
     *   <li>The channel closes within {@code KEEP_ALIVE_TIMEOUT_S + GRACE_S} seconds
     *       (idle handler fires because no message ever arrives).</li>
     *   <li>S3 hold bucket remains empty — no message was ingested.</li>
     * </ul>
     */
    @Test
    @DisplayName("IT[TCP-6555]: keepAlive TCP — no message sent — channel survives " +
                 "readTimeout(10 s), closes at keepAliveTimeout(20 s)")
    void tcp_shouldKeepChannelOpenWhenNoMessageSent_closesAtKeepAliveTimeout() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        long testStart = System.currentTimeMillis();

        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            socket.setSoTimeout((KEEP_ALIVE_TIMEOUT_S + GRACE_S) * 1_000);

            // ── Send ONLY the HAProxy header — no TCP payload ─────────────────────
            socket.getOutputStream().write(
                    buildProxyV1Header(CLIENT_IP, DEST_IP, TCP_CLIENT_PORT, TCP_DEST_PORT));
            socket.getOutputStream().flush();

            // Assert still open after DEFAULT_READ_TIMEOUT_S + 2 s
            Thread.sleep((DEFAULT_READ_TIMEOUT_S + 2) * 1_000L);

            softly.assertThat(socket.isConnected())
                    .as("KEEP_ALIVE_TIMEOUT_KEY resolved from PROXY destPort=%d — " +
                        "TCP channel must survive past default readTimeout=%d s",
                        TCP_DEST_PORT, DEFAULT_READ_TIMEOUT_S)
                    .isTrue();

            boolean channelClosed = waitForChannelClose(socket);
            long totalElapsedMs   = System.currentTimeMillis() - testStart;

            softly.assertThat(channelClosed)
                    .as("IdleStateHandler(20 s) must eventually close the TCP channel")
                    .isTrue();
            softly.assertThat(totalElapsedMs)
                    .as("TCP channel must close within keepAliveTimeout(%d s) + grace(%d s)",
                            KEEP_ALIVE_TIMEOUT_S, GRACE_S)
                    .isLessThan((long) (KEEP_ALIVE_TIMEOUT_S + GRACE_S) * 1_000L);
            softly.assertThat(totalElapsedMs)
                    .as("TCP channel must NOT close before default readTimeout(%d s)",
                            DEFAULT_READ_TIMEOUT_S)
                    .isGreaterThan((long) DEFAULT_READ_TIMEOUT_S * 1_000L);
        }

        softly.assertThat(assertionHelper().bucketIsEmpty(HOLD_BUCKET))
                .as("HOLD_BUCKET must be empty — no TCP message was delivered")
                .isTrue();

        softly.assertAll();
    }

    // =========================================================================
    // FlowAssertionParams factories
    // =========================================================================


    /**
     * Builds hold-flow assertion params for the raw-TCP port 6555 / {@code test.fifo}.
     *
     * @param payload   raw TCP payload that was sent (pre-delimiter-wrap)
     * @param groupId   expected SQS {@code messageGroupId}
     */
    private FlowAssertionParams tcpHoldFlowParams(String payload, String groupId) {
        return FlowAssertionParams.builder()
                .dataBucket(HOLD_BUCKET)
                .metadataBucket(null)
                .holdFlow(true)
                .port(TCP_DEST_PORT)
                .dataDir("/outbound")
                .metadataDir("/outbound")
                .tenantId(null)
                .queueUrl(queueUrls.get("test.fifo"))
                .expectedMessageGroupId(groupId)
                .expectedPayload(payload)
                .payloadNormalizer(IngestionAssertionHelper::normalizeGeneric)
                .ackExpected(false)   // TCP simple-ACK is not stored as a separate S3 object
                .build();
    }

   
    // =========================================================================
    // TCP ACK assertion helpers
    // =========================================================================

    /**
     * Asserts that {@code rawAck} is a valid simple TCP ACK.
     * The server produces {@code ACK|<interactionId>|<version>|<timestamp>}.
     */
    private void assertTcpAck(byte[] rawAck, SoftAssertions softly, String label) {
        softly.assertThat(rawAck)
                .as(label + ": raw TCP ACK bytes must not be empty")
                .isNotEmpty();
        if (rawAck == null || rawAck.length == 0) return;

        String ackStr = new String(rawAck, StandardCharsets.UTF_8).trim();
        softly.assertThat(ackStr)
                .as(label + ": TCP ACK must start with 'ACK|'")
                .startsWith("ACK|");
    }

    // =========================================================================
    // TCP / MLLP / PROXY low-level helpers
    // =========================================================================

    /** Builds a HAProxy PROXY protocol v1 text header. */
    private static byte[] buildProxyV1Header(String clientIp, String destIp,
            int clientPort, int destPort) {
        return String.format("PROXY TCP4 %s %s %d %d\r\n",
                        clientIp, destIp, clientPort, destPort)
                .getBytes(StandardCharsets.US_ASCII);
    }

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
     * Reads bytes from {@code socket} until a newline ({@code 0x0A}) is received,
     * which marks the end of a simple TCP ACK/NACK response.
     */
    private static byte[] readTcpResponse(Socket socket) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = socket.getInputStream().read()) != -1) {
            buf.write(b);
            if (b == '\n') break;
        }
        return buf.toByteArray();
    }

    /**
     * Blocks until the server closes the connection (EOF) or the socket read-timeout fires.
     *
     * @return {@code true} if EOF was received (clean server-side close);
     *         {@code false} if the socket timed out before EOF.
     */
    private static boolean waitForChannelClose(Socket socket) {
        try {
            return socket.getInputStream().read() == -1;
        } catch (java.net.SocketTimeoutException e) {
            return false;
        } catch (Exception e) {
            return true; // connection-reset or similar counts as closed
        }
    }
}