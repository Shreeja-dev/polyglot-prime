package org.techbd.ingest.integrationtests.tcp.mllp;

import static org.techbd.ingest.integrationtests.util.NettyTcpServerKeepAliveFixtures.GROUP_ID_HL7_MSG1;
import static org.techbd.ingest.integrationtests.util.NettyTcpServerKeepAliveFixtures.GROUP_ID_HL7_MSG2;
import static org.techbd.ingest.integrationtests.util.NettyTcpServerKeepAliveFixtures.HL7_MSG1_ORU;
import static org.techbd.ingest.integrationtests.util.NettyTcpServerKeepAliveFixtures.HL7_MSG2_ADT_A01;

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
 * Integration tests for <b>persistent (keep-alive) TCP connections</b>.
 *
 * <p>Port under test:</p>
 *
 * <h3>Port 5555 — MLLP / outbound-response (keep-alive)</h3>
 * <pre>{@code
 * {
 *   "port": 5555,
 *   "responseType": "outbound",
 *   "protocol": "TCP",
 *   "route": "/hold",
 *   "dataDir": "/outbound",
 *   "metadataDir": "/outbound",
 *   "queue": "test.fifo",
 *   "keepAliveTimeout": 20
 * }
 * }</pre>
 *
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
 * <h3>MLLP test scenarios (port 5555)</h3>
 * <ol>
 *   <li><b>Two MLLP messages, one session</b> — send two HL7 messages on the same TCP
 *       connection, verify two MLLP ACKs ({@code MSA|AA}), two S3+SQS payloads.</li>
 *   <li><b>One MLLP message, channel survives read-timeout window</b> — send one message,
 *       confirm the ACK arrives, then verify the channel is still open after the
 *       default read-timeout (10 s) but closes around the keepAliveTimeout (20 s).</li>
 *   <li><b>No MLLP message sent, channel closes at keepAliveTimeout</b> — open a
 *       connection, send only the HAProxy header, and verify the server holds the
 *       channel open past the default read-timeout (10 s) but closes it near the
 *       keepAliveTimeout (20 s).</li>
 * </ol>
 *
 * <h3>SQS deduplication note</h3>
 * <p>The HL7 fixtures ({@link NettyTcpServerKeepAliveFixtures#HL7_MSG1_ORU} and
 * {@link NettyTcpServerKeepAliveFixtures#HL7_MSG2_ADT_A01}) are intentionally
 * content-distinct so that the SQS FIFO deduplication window (5 minutes) does not
 * suppress the second message within each pair.
 *
 * @see NettyTcpServerKeepAliveFixtures  for shared fixture strings and group-ID constants
 */
class NettyMllpServerKeepAliveITCase extends BaseIntegrationTest {

    // ── Network constants ────────────────────────────────────────────────────

    /** Dispatcher port — HAProxy PROXY-protocol entry point. */
    private static final int    TCP_SERVER_PORT = 7980;
    private static final String TCP_HOST        = "localhost";

    /**
     * MLLP destination port in the HAProxy header.
     * The server resolves the port-config for this port
     * ({@code keepAliveTimeout=20, responseType=outbound, route=/hold, queue=test.fifo}).
     */
    private static final int MLLP_DEST_PORT = 5555;


    private static final String CLIENT_IP        = "203.0.113.10";
    private static final String DEST_IP          = "127.0.0.1";
    private static final int    MLLP_CLIENT_PORT = 55100;

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

    // ── MLLP framing ─────────────────────────────────────────────────────────

    private static final byte MLLP_START = 0x0B;
    private static final byte MLLP_END_1 = 0x1C;
    private static final byte MLLP_END_2 = 0x0D;

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
    // MLLP TESTS — port 5555
    // =========================================================================

    // ── MLLP Test 1 — Two HL7 messages on one persistent MLLP session ─────────

    /**
     * Sends two content-distinct HL7 messages on a single MLLP TCP session and verifies:
     * <ul>
     *   <li>Both messages receive a valid MLLP ACK ({@code MSA|AA}) on the
     *       <em>same socket</em>, proving the channel was not closed between them.</li>
     *   <li>The channel remains open after the second ACK.</li>
     *   <li>Two independent S3 payloads and two SQS entries exist in the hold-flow
     *       bucket and {@code test.fifo} queue respectively.</li>
     * </ul>
     *
     */
    @Test
    @DisplayName("IT[MLLP-5555]: keepAlive outbound — two HL7 messages on one MLLP session — " +
                 "both ACKed (MSA|AA), channel stays open, S3+SQS asserted per message")
    void mllp_shouldProcessTwoMessagesOnSingleSession_channelStaysOpen() throws Exception {
        SoftAssertions softly = new SoftAssertions();

        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            socket.setSoTimeout((KEEP_ALIVE_TIMEOUT_S + GRACE_S) * 1_000);

            // ── Send HAProxy header once — shared by both messages on this session ──
            socket.getOutputStream().write(
                    buildProxyV1Header(CLIENT_IP, DEST_IP, MLLP_CLIENT_PORT, MLLP_DEST_PORT));
            socket.getOutputStream().flush();

            // ── Message 1: ORU^R01 ────────────────────────────────────────────────
            socket.getOutputStream().write(wrapMllp(HL7_MSG1_ORU));
            socket.getOutputStream().flush();

            byte[] ack1 = readMllpFrame(socket);

            softly.assertThat(socket.isConnected())
                    .as("SESSION_ID_KEY persists — socket must remain connected after MLLP ACK-1")
                    .isTrue();
            softly.assertThat(socket.isClosed())
                    .as("KEEP_ALIVE_TIMEOUT_KEY active — server must NOT close channel after MLLP message 1")
                    .isFalse();
            assertMllpAck(ack1, softly, "MLLP Session-1 / Message-1 ACK");

            // Brief pause so message 1 is fully committed to S3/SQS before message 2 arrives
            Thread.sleep(500);

            // ── Message 2: ADT^A01 (same session, no new PROXY header) ────────────
            socket.getOutputStream().write(wrapMllp(HL7_MSG2_ADT_A01));
            socket.getOutputStream().flush();

            byte[] ack2 = readMllpFrame(socket);

            softly.assertThat(socket.isConnected())
                    .as("SESSION_ID_KEY persists — socket must remain connected after MLLP ACK-2")
                    .isTrue();
            softly.assertThat(socket.isClosed())
                    .as("KEEP_ALIVE_TIMEOUT_KEY active — server must NOT close channel after MLLP message 2")
                    .isFalse();
            assertMllpAck(ack2, softly, "MLLP Session-1 / Message-2 ACK");

            softly.assertThat(ack1).as("MLLP ACK-1 must be non-empty").isNotEmpty();
            softly.assertThat(ack2).as("MLLP ACK-2 must be non-empty").isNotEmpty();
            softly.assertThat(ack1)
                    .as("MLLP ACK-1 and ACK-2 must be distinct (INTERACTION_ATTRIBUTE_KEY reset)")
                    .isNotEqualTo(ack2);
        }

        // ── S3 + SQS: assert each MLLP message independently by payload content ──
        assertionHelper().assertHoldFlow(
                mllpHoldFlowParams(HL7_MSG1_ORU, GROUP_ID_HL7_MSG1), softly);

        assertionHelper().assertHoldFlow(
                mllpHoldFlowParams(HL7_MSG2_ADT_A01, GROUP_ID_HL7_MSG2), softly);

        softly.assertAll();
    }

    // ── MLLP Test 2 — One MLLP message, channel survives default read-timeout ──

    /**
     * Sends one HL7 message on an MLLP session and verifies that the channel
     * survives past the default read-timeout (10 s) but closes by the
     * keepAliveTimeout (20 s).
     *
     * <h4>What is verified</h4>
     * <ul>
     *   <li>The ACK for the single message is received and is a valid {@code MSA|AA}.</li>
     *   <li>Channel-attribute {@code KEEP_ALIVE_TIMEOUT_KEY=20} is active — proved by
     *       confirming the socket is still open {@code DEFAULT_READ_TIMEOUT_S + 2 s}
     *       after the ACK.</li>
     *   <li>The channel closes within {@code KEEP_ALIVE_TIMEOUT_S + GRACE_S} seconds.</li>
     *   <li>S3 hold-flow bucket and SQS queue each contain exactly one matching payload.</li>
     * </ul>
     */
    @Test
    @DisplayName("IT[MLLP-5555]: keepAlive outbound — one HL7 MLLP message — channel survives " +
                 "readTimeout(10 s), closes at keepAliveTimeout(20 s)")
    void mllp_shouldKeepChannelOpenAfterOneMessage_closesAtKeepAliveTimeout() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        long testStart = System.currentTimeMillis();

        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            socket.setSoTimeout((KEEP_ALIVE_TIMEOUT_S + GRACE_S) * 1_000);

            socket.getOutputStream().write(
                    buildProxyV1Header(CLIENT_IP, DEST_IP, MLLP_CLIENT_PORT, MLLP_DEST_PORT));
            socket.getOutputStream().write(wrapMllp(HL7_MSG1_ORU));
            socket.getOutputStream().flush();

            byte[] ack = readMllpFrame(socket);
            assertMllpAck(ack, softly, "MLLP single-message keepAlive ACK");

            long afterAckMs = System.currentTimeMillis();

            // Wait past the default read-timeout; ReadTimeoutHandler would have closed by now
            Thread.sleep((DEFAULT_READ_TIMEOUT_S + 2) * 1_000L);

            softly.assertThat(socket.isConnected())
                    .as("KEEP_ALIVE_TIMEOUT_KEY active — MLLP socket must still be connected " +
                        (DEFAULT_READ_TIMEOUT_S + 2) + " s after ACK (past default read-timeout)")
                    .isTrue();

            long elapsedSinceLastData = (System.currentTimeMillis() - afterAckMs) / 1_000;
            long remainingBudgetMs = Math.max(1_000L,
                    (KEEP_ALIVE_TIMEOUT_S - elapsedSinceLastData + GRACE_S) * 1_000L);
            socket.setSoTimeout((int) remainingBudgetMs);

            boolean channelClosed = waitForChannelClose(socket);
            long totalElapsedMs   = System.currentTimeMillis() - testStart;

            softly.assertThat(channelClosed)
                    .as("IdleStateHandler(20 s) must close the MLLP channel after keepAliveTimeout")
                    .isTrue();
            softly.assertThat(totalElapsedMs)
                    .as("MLLP channel must close within keepAliveTimeout(%d s) + grace(%d s)",
                            KEEP_ALIVE_TIMEOUT_S, GRACE_S)
                    .isLessThan((long) (KEEP_ALIVE_TIMEOUT_S + GRACE_S) * 1_000L);
            softly.assertThat(totalElapsedMs)
                    .as("MLLP channel must NOT close before default readTimeout(%d s)",
                            DEFAULT_READ_TIMEOUT_S)
                    .isGreaterThan((long) DEFAULT_READ_TIMEOUT_S * 1_000L);
        }

        assertionHelper().assertHoldFlow(
                mllpHoldFlowParams(HL7_MSG1_ORU, GROUP_ID_HL7_MSG1), softly);

        softly.assertAll();
    }

    // ── MLLP Test 3 — No MLLP message sent, channel closes at keepAliveTimeout ─

    /**
     * Opens a connection to the MLLP port and sends only the HAProxy header — no HL7 payload.
     *
     * <h4>What is verified</h4>
     * <ul>
     *   <li>{@code KEEP_ALIVE_TIMEOUT_KEY=20} is resolved from the HAProxy header's
     *       {@code destPort=5555} before any HL7 message arrives — proved by confirming
     *       the socket is still open {@code DEFAULT_READ_TIMEOUT_S + 2 s} after the
     *       PROXY header.</li>
     *   <li>The channel closes within {@code KEEP_ALIVE_TIMEOUT_S + GRACE_S} seconds.</li>
     *   <li>S3 hold bucket remains empty — no message was ingested.</li>
     * </ul>
     */
    @Test
    @DisplayName("IT[MLLP-5555]: keepAlive outbound — no MLLP message sent — channel survives " +
                 "readTimeout(10 s), closes at keepAliveTimeout(20 s)")
    void mllp_shouldKeepChannelOpenWhenNoMessageSent_closesAtKeepAliveTimeout() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        long testStart = System.currentTimeMillis();

        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            socket.setSoTimeout((KEEP_ALIVE_TIMEOUT_S + GRACE_S) * 1_000);

            // ── Send ONLY the HAProxy header — no HL7 payload ─────────────────────
            socket.getOutputStream().write(
                    buildProxyV1Header(CLIENT_IP, DEST_IP, MLLP_CLIENT_PORT, MLLP_DEST_PORT));
            socket.getOutputStream().flush();

            Thread.sleep((DEFAULT_READ_TIMEOUT_S + 2) * 1_000L);

            softly.assertThat(socket.isConnected())
                    .as("KEEP_ALIVE_TIMEOUT_KEY resolved from PROXY destPort=%d — " +
                        "MLLP channel must survive past default readTimeout=%d s",
                        MLLP_DEST_PORT, DEFAULT_READ_TIMEOUT_S)
                    .isTrue();

            boolean channelClosed = waitForChannelClose(socket);
            long totalElapsedMs   = System.currentTimeMillis() - testStart;

            softly.assertThat(channelClosed)
                    .as("IdleStateHandler(20 s) must eventually close the MLLP channel")
                    .isTrue();
            softly.assertThat(totalElapsedMs)
                    .as("MLLP channel must close within keepAliveTimeout(%d s) + grace(%d s)",
                            KEEP_ALIVE_TIMEOUT_S, GRACE_S)
                    .isLessThan((long) (KEEP_ALIVE_TIMEOUT_S + GRACE_S) * 1_000L);
            softly.assertThat(totalElapsedMs)
                    .as("MLLP channel must NOT close before default readTimeout(%d s)",
                            DEFAULT_READ_TIMEOUT_S)
                    .isGreaterThan((long) DEFAULT_READ_TIMEOUT_S * 1_000L);
        }

        softly.assertThat(assertionHelper().bucketIsEmpty(HOLD_BUCKET))
                .as("HOLD_BUCKET must be empty — no HL7 message was delivered via MLLP")
                .isTrue();

        softly.assertAll();
    }

    // =========================================================================
    // FlowAssertionParams factories
    // =========================================================================

    /**
     * Builds hold-flow assertion params for the MLLP port 5555 / {@code test.fifo}.
     *
     * @param payload   raw HL7 payload that was sent (pre-MLLP-wrap)
     * @param groupId   expected SQS {@code messageGroupId}
     */
    private FlowAssertionParams mllpHoldFlowParams(String payload, String groupId) {
        return FlowAssertionParams.builder()
                .dataBucket(HOLD_BUCKET)
                .metadataBucket(null)
                .holdFlow(true)
                .port(MLLP_DEST_PORT)
                .dataDir("/outbound")
                .metadataDir("/outbound")
                .tenantId(null)
                .queueUrl(queueUrls.get("test.fifo"))
                .expectedMessageGroupId(groupId)
                .expectedPayload(payload)
                .payloadNormalizer(IngestionAssertionHelper::normalizeHl7)
                .ackExpected(true)
                .build();
    }
    // =========================================================================
    // MLLP ACK assertion helpers
    // =========================================================================

    /**
     * Asserts that {@code rawAck} is a valid MLLP-wrapped HL7 ACK ({@code MSA|AA}).
     */
    private void assertMllpAck(byte[] rawAck, SoftAssertions softly, String label) {
        softly.assertThat(rawAck)
                .as(label + ": raw MLLP ACK bytes must not be empty")
                .isNotEmpty();
        if (rawAck == null || rawAck.length == 0) return;

        String ackStr = stripMllpFraming(rawAck);
        softly.assertThat(ackStr).as(label + ": must contain MSH segment").contains("MSH");
        softly.assertThat(ackStr).as(label + ": must contain MSA segment").contains("MSA");
        softly.assertThat(ackStr).as(label + ": acknowledgement code must be AA").contains("MSA|AA");
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
     * Wraps {@code hl7Text} in MLLP framing:
     * {@code <VT>(0x0B) payload <FS>(0x1C)<CR>(0x0D)}.
     */
    private static byte[] wrapMllp(String hl7Text) {
        byte[] payload = hl7Text.getBytes(StandardCharsets.UTF_8);
        byte[] framed  = new byte[1 + payload.length + 2];
        framed[0] = MLLP_START;
        System.arraycopy(payload, 0, framed, 1, payload.length);
        framed[framed.length - 2] = MLLP_END_1;
        framed[framed.length - 1] = MLLP_END_2;
        return framed;
    }

    /**
     * Reads bytes from {@code socket} until the MLLP end-of-frame marker
     * ({@code 0x1C 0x0D}) is detected or the socket times out.
     */
    private static byte[] readMllpFrame(Socket socket) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = socket.getInputStream().read()) != -1) {
            buf.write(b);
            byte[] soFar = buf.toByteArray();
            int    len   = soFar.length;
            if (len >= 2 && soFar[len - 2] == MLLP_END_1 && soFar[len - 1] == MLLP_END_2) break;
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

    /** Removes MLLP framing bytes to produce a readable string. */
    private static String stripMllpFraming(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8)
                .replace(String.valueOf((char) MLLP_START), "")
                .replace(String.valueOf((char) MLLP_END_1), "")
                .replace(String.valueOf((char) MLLP_END_2), "");
    }
}