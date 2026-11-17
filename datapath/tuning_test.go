package datapath

import (
	"testing"

	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
)

// TestTuneTCP checks the stack actually takes the tuned TCP settings.
func TestTuneTCP(t *testing.T) {
	netStack := newTestStack(t)
	tuneTCP(netStack)

	var sack tcpip.TCPSACKEnabled
	if err := netStack.TransportProtocolOption(tcp.ProtocolNumber, &sack); err != nil || !bool(sack) {
		t.Fatalf("SACK = %v, err %v; want enabled", sack, err)
	}

	var congestion tcpip.CongestionControlOption
	if err := netStack.TransportProtocolOption(tcp.ProtocolNumber, &congestion); err != nil || congestion != "cubic" {
		t.Fatalf("congestion control = %q, err %v; want cubic", congestion, err)
	}

	var moderate tcpip.TCPModerateReceiveBufferOption
	if err := netStack.TransportProtocolOption(tcp.ProtocolNumber, &moderate); err != nil || !bool(moderate) {
		t.Fatalf("receive buffer auto-tuning = %v, err %v; want enabled", moderate, err)
	}

	var receive tcpip.TCPReceiveBufferSizeRangeOption
	if err := netStack.TransportProtocolOption(tcp.ProtocolNumber, &receive); err != nil || receive.Max != maxBufferSize {
		t.Fatalf("receive buffer max = %d, err %v; want %d", receive.Max, err, maxBufferSize)
	}
}
