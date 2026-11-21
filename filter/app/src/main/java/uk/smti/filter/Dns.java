package uk.smti.filter;

/** Minimal IPv4 / UDP / DNS packet handling for the filter (only what is needed to read a query and answer it). */
final class Dns {

    private Dns() {
    }

    /** A DNS query found in an IPv4/UDP packet. */
    static final class Query {
        byte[] srcIp = new byte[4], dstIp = new byte[4];
        int srcPort, dstPort;
        byte[] payload;   // the DNS message
        String name;      // the site asked for
        int type;         // 1 = A, 28 = AAAA ...
    }

    /** Returns the query, or null if this is not a DNS query to port 53. */
    static Query parse(byte[] pkt, int len) {
        if (len < 28 || (pkt[0] >> 4) != 4) return null;
        int ihl = (pkt[0] & 0x0F) * 4;
        if (pkt[9] != 17 || len < ihl + 8) return null; // UDP only
        int dstPort = u16(pkt, ihl + 2);
        if (dstPort != 53) return null;
        Query q = new Query();
        System.arraycopy(pkt, 12, q.srcIp, 0, 4);
        System.arraycopy(pkt, 16, q.dstIp, 0, 4);
        q.srcPort = u16(pkt, ihl);
        q.dstPort = dstPort;
        int udpLen = u16(pkt, ihl + 4);
        int dnsLen = Math.min(udpLen - 8, len - ihl - 8);
        if (dnsLen < 12) return null;
        q.payload = new byte[dnsLen];
        System.arraycopy(pkt, ihl + 8, q.payload, 0, dnsLen);
        if (u16(q.payload, 4) < 1) return null;
        StringBuilder sb = new StringBuilder();
        int i = 12;
        while (i < dnsLen) {
            int l = q.payload[i] & 0xFF;
            if (l == 0) { i++; break; }
            if ((l & 0xC0) != 0 || i + 1 + l > dnsLen) return null;
            if (sb.length() > 0) sb.append('.');
            for (int k = 0; k < l; k++) sb.append((char) (q.payload[i + 1 + k] & 0xFF));
            i += 1 + l;
        }
        if (i + 4 > dnsLen) return null;
        q.type = u16(q.payload, i);
        q.name = sb.toString().toLowerCase(java.util.Locale.ROOT);
        return q;
    }

    /** "No such site" answer to a query (only the question is kept). */
    static byte[] blockedAnswer(byte[] query) {
        int end = 12;
        while (end < query.length && query[end] != 0) end += 1 + (query[end] & 0xFF);
        end += 5; // zero byte + type + class
        end = Math.min(end, query.length);
        byte[] r = new byte[end];
        System.arraycopy(query, 0, r, 0, end);
        int rd = query[2] & 0x01;
        r[2] = (byte) (0x80 | (query[2] & 0x78) | rd); // answer, same opcode, recursion desired
        r[3] = (byte) 0x83;                            // recursion available, NXDOMAIN
        r[4] = 0; r[5] = 1;                            // 1 question
        r[6] = r[7] = r[8] = r[9] = r[10] = r[11] = 0; // no answers
        return r;
    }

    /** Wraps a DNS answer in IPv4/UDP, going back from the DNS address to the app. */
    static byte[] reply(Query q, byte[] dns) {
        int total = 20 + 8 + dns.length;
        byte[] p = new byte[total];
        p[0] = 0x45;
        put16(p, 2, total);
        put16(p, 4, (int) (System.nanoTime() & 0xFFFF));
        p[6] = 0x40; // don't fragment
        p[8] = 64;   // TTL
        p[9] = 17;   // UDP
        System.arraycopy(q.dstIp, 0, p, 12, 4);
        System.arraycopy(q.srcIp, 0, p, 16, 4);
        put16(p, 10, checksum(p, 0, 20));
        put16(p, 20, q.dstPort);
        put16(p, 22, q.srcPort);
        put16(p, 24, 8 + dns.length);
        // UDP checksum 0 = not used (allowed for IPv4)
        System.arraycopy(dns, 0, p, 28, dns.length);
        return p;
    }

    private static int checksum(byte[] b, int off, int len) {
        long sum = 0;
        for (int i = off; i < off + len - 1; i += 2) sum += ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
        if ((len & 1) == 1) sum += (b[off + len - 1] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    static int u16(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    private static void put16(byte[] b, int i, int v) {
        b[i] = (byte) (v >> 8);
        b[i + 1] = (byte) v;
    }
}
