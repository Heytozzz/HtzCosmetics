package com.heytozzz.htzcosmetics.core.signal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeSignalPolicyTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void senderRespectsCooldownButNeverLosesTheLastChange() {
        ChangeSignalPolicy.Sender s = new ChangeSignalPolicy.Sender();
        assertTrue(s.request(0));
        assertFalse(s.request(1000));     // too soon: postponed
        assertFalse(s.request(2000));     // coalesced with the previous one
        assertFalse(s.poll(4999));
        assertTrue(s.poll(5000));         // sent once the cooldown ends
        assertFalse(s.poll(20_000));      // nothing pending anymore
    }

    @Test
    void receiverIgnoresFirstSightAndUnchangedBits() {
        ChangeSignalPolicy.Receiver r = new ChangeSignalPolicy.Receiver();
        assertFalse(r.observe(A, true, 0));  // first time we see this player
        assertFalse(r.observe(A, true, 100));
        assertTrue(r.observe(A, false, 200)); // flip = signal
    }

    @Test
    void spammingPlayerIsLimitedPerPlayerAndCoalesced() {
        ChangeSignalPolicy.Receiver r = new ChangeSignalPolicy.Receiver();
        r.observe(A, false, 0);
        int fetches = 0;
        boolean bit = false;
        // An attacker flips the bit every tick (20 times per second) for 30 seconds.
        for (long t = 1; t <= 30_000; t += 50) {
            bit = !bit;
            if (r.observe(A, bit, t)) fetches++;
            fetches += r.pollDeferred(t).size();
        }
        assertTrue(fetches <= 30_000 / ChangeSignalPolicy.PER_PLAYER_COOLDOWN_MS + 1, "fetches: " + fetches);
    }

    @Test
    void globalBudgetCapsManyPlayers() {
        ChangeSignalPolicy.Receiver r = new ChangeSignalPolicy.Receiver();
        int fetches = 0;
        for (int i = 0; i < 100; i++) {
            UUID p = UUID.randomUUID();
            r.observe(p, false, 0);
            if (r.observe(p, true, 1)) fetches++;
        }
        assertEquals(ChangeSignalPolicy.MAX_FRESH_PER_MINUTE, fetches);
        assertEquals(0, r.pollDeferred(30_000).size());                                 // still within the minute
        assertEquals(ChangeSignalPolicy.MAX_FRESH_PER_MINUTE, r.pollDeferred(61_000).size()); // next minute's budget
    }

    @Test
    void deferredSignalIsDeliveredAfterCooldown() {
        ChangeSignalPolicy.Receiver r = new ChangeSignalPolicy.Receiver();
        r.observe(B, false, 0);
        assertTrue(r.observe(B, true, 1));
        assertFalse(r.observe(B, false, 2_000)); // within cooldown: deferred
        assertEquals(List.of(), r.pollDeferred(5_000));
        assertEquals(List.of(B), r.pollDeferred(10_001));
        r.forget(B);
        assertFalse(r.observe(B, true, 20_000)); // forgotten: first sight again
    }
}
