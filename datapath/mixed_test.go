package datapath

import (
	"encoding/binary"
	"net/netip"
	"testing"
)

func validChecksums(t *testing.T, pkt []byte) {
	t.Helper()
	if ipChecksum(pkt[:20], 0) != 0 {
		t.Fatalf("bad IPv4 header checksum")
	}
	pseudo := uint32(0)
	for i := 12; i < 20; i += 2 {
		pseudo += uint32(binary.BigEndian.Uint16(pkt[i:]))
	}
	pseudo += 6 + uint32(len(pkt)-20)
	if ipChecksum(pkt[20:], pseudo) != 0 {
		t.Fatalf("bad TCP checksum")
	}
}

func TestBuildTCP4HasValidChecksums(t *testing.T) {
	pkt := buildTCP4(netip.MustParseAddr("192.0.2.3"), 19999, netip.MustParseAddr("192.0.2.2"), 41000, 1, 0, 0x02)
	validChecksums(t, pkt)
}

func TestRewriteKeepsChecksumsValid(t *testing.T) {
	pkt := buildTCP4(netip.MustParseAddr("192.0.2.2"), 51234, netip.MustParseAddr("142.250.80.46"), 443, 7, 9, 0x18)
	pkt = append(pkt, []byte("hola")...) // a little payload
	binary.BigEndian.PutUint16(pkt[2:], uint16(len(pkt)))
	binary.BigEndian.PutUint16(pkt[10:], 0)
	binary.BigEndian.PutUint16(pkt[10:], ipChecksum(pkt[:20], 0))
	binary.BigEndian.PutUint16(pkt[36:], 0)
	pseudo := uint32(0)
	for i := 12; i < 20; i += 2 {
		pseudo += uint32(binary.BigEndian.Uint16(pkt[i:]))
	}
	binary.BigEndian.PutUint16(pkt[36:], ipChecksum(pkt[20:], pseudo+6+uint32(len(pkt)-20)))
	validChecksums(t, pkt)

	rewriteTCP4(pkt, 20, netip.MustParseAddr("192.0.2.3"), 20001, netip.MustParseAddr("192.0.2.2"), 40000)
	validChecksums(t, pkt)
	if got := netip.AddrFrom4([4]byte(pkt[16:20])); got.String() != "192.0.2.2" {
		t.Fatalf("destination = %v", got)
	}
	if binary.BigEndian.Uint16(pkt[20:]) != 20001 || binary.BigEndian.Uint16(pkt[22:]) != 40000 {
		t.Fatalf("ports not rewritten")
	}

	// And back again, as the reply path does.
	rewriteTCP4(pkt, 20, netip.MustParseAddr("142.250.80.46"), 443, netip.MustParseAddr("192.0.2.2"), 51234)
	validChecksums(t, pkt)
}

func TestFlowHashIsStablePerFlow(t *testing.T) {
	a := buildTCP4(netip.MustParseAddr("192.0.2.2"), 50000, netip.MustParseAddr("1.1.1.1"), 443, 1, 0, 0x02)
	b := buildTCP4(netip.MustParseAddr("192.0.2.2"), 50000, netip.MustParseAddr("1.1.1.1"), 443, 99, 5, 0x10)
	if flowHash(a) != flowHash(b) {
		t.Fatalf("packets of one flow hashed apart")
	}
}

func TestNatTableAllocatesUniquePorts(t *testing.T) {
	s := &systemStack{byKey: map[flowKey]*sysSession{}, byPort: map[uint16]*sysSession{}, nextNat: natPortFirst}
	seen := map[uint16]bool{}
	for i := 0; i < 1000; i++ {
		key := flowKey{src: netip.MustParseAddr("192.0.2.2"), dst: netip.MustParseAddr("1.1.1.1"), srcPort: uint16(1000 + i), dstPort: 443}
		sess := s.newSession(key)
		if sess == nil || seen[sess.natPort] {
			t.Fatalf("duplicate or missing NAT port at %d", i)
		}
		seen[sess.natPort] = true
	}
}
