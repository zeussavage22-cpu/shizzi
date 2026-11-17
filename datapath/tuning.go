package datapath

import (
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
)

// TCP tuning for the leg between this stack and the tethered clients.
//
// gVisor's defaults are conservative: no SACK, Reno congestion control and
// fixed-size buffers. On a Wi-Fi hop even a fraction of a percent of packet
// loss then collapses throughput, because every loss stalls the window and is
// recovered one segment per round trip. These are the settings a modern Linux
// or Android kernel uses by default.
const (
	minBufferSize     = 4 << 10
	defaultBufferSize = 1 << 20
	maxBufferSize     = 8 << 20
)

func tuneTCP(netStack *stack.Stack) {
	// Selective acknowledgements: recover several lost segments per round trip.
	sack := tcpip.TCPSACKEnabled(true)
	netStack.SetTransportProtocolOption(tcp.ProtocolNumber, &sack)

	// RACK-TLP loss detection (needs SACK): faster, timer-based recovery of tail losses.
	recovery := tcpip.TCPRecovery(tcpip.TCPRACKLossDetection)
	netStack.SetTransportProtocolOption(tcp.ProtocolNumber, &recovery)

	// CUBIC grows the window back much faster than Reno after a loss.
	congestion := tcpip.CongestionControlOption("cubic")
	netStack.SetTransportProtocolOption(tcp.ProtocolNumber, &congestion)

	// Let the receive buffer grow with the connection instead of capping it.
	moderate := tcpip.TCPModerateReceiveBufferOption(true)
	netStack.SetTransportProtocolOption(tcp.ProtocolNumber, &moderate)

	receive := tcpip.TCPReceiveBufferSizeRangeOption{
		Min: minBufferSize, Default: defaultBufferSize, Max: maxBufferSize,
	}
	netStack.SetTransportProtocolOption(tcp.ProtocolNumber, &receive)

	send := tcpip.TCPSendBufferSizeRangeOption{
		Min: minBufferSize, Default: defaultBufferSize, Max: maxBufferSize,
	}
	netStack.SetTransportProtocolOption(tcp.ProtocolNumber, &send)
}
