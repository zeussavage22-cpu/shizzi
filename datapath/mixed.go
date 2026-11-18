package datapath

import (
	"context"
	"encoding/binary"
	"fmt"
	"net"
	"net/netip"
	"runtime"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/sys/unix"
	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv6"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/icmp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
)

// StartMixed is Start with a faster path for IPv4 TCP.
//
// Every packet is read from the TUN here. IPv4 TCP goes through the "system
// stack" (see systemStack): its addresses are rewritten so the kernel's own TCP
// terminates the connection on a local listener, and the engine splices that
// socket to an ordinary outbound one. Everything else (IPv6, UDP, ICMP) is
// handed to the same gVisor stack Start uses.
//
// The system path proves itself at start with a probe connection. If the kernel
// does not deliver it (a platform that blocks local delivery from the TUN), the
// system path is switched off and all traffic uses gVisor, exactly like Start.
//
// tunAddr4 is the TUN's IPv4 address; fakeAddr4 is an unused address in the
// same subnet the kernel routes back into the TUN.
func StartMixed(tunFD int, mtu int, tunAddr4 string, fakeAddr4 string) (*Session, error) {
	tunAddr, err := netip.ParseAddr(tunAddr4)
	if err != nil || !tunAddr.Is4() {
		return nil, fmt.Errorf("datapath.StartMixed: bad tun address %q", tunAddr4)
	}
	fakeAddr, err := netip.ParseAddr(fakeAddr4)
	if err != nil || !fakeAddr.Is4() {
		return nil, fmt.Errorf("datapath.StartMixed: bad fake address %q", fakeAddr4)
	}

	netStack := stack.New(stack.Options{
		NetworkProtocols: []stack.NetworkProtocolFactory{ipv4.NewProtocol, ipv6.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{
			tcp.NewProtocol, udp.NewProtocol, icmp.NewProtocol4, icmp.NewProtocol6,
		},
	})
	tuneTCP(netStack)

	link := channel.New(1024, uint32(mtu), "")
	if tcpipErr := netStack.CreateNIC(nicID, link); tcpipErr != nil {
		netStack.Close()
		return nil, fmt.Errorf("datapath.StartMixed: create NIC: %v", tcpipErr)
	}
	netStack.SetPromiscuousMode(nicID, true)
	netStack.SetSpoofing(nicID, true)
	netStack.SetRouteTable([]tcpip.Route{
		{Destination: header.IPv4EmptySubnet, NIC: nicID},
		{Destination: header.IPv6EmptySubnet, NIC: nicID},
	})

	binding := &networkBinding{}
	dialer := &net.Dialer{Timeout: dialTimeout, Control: binding.control}
	installForwarders(netStack, dialer)

	ctx, cancel := context.WithCancel(context.Background())
	session := &Session{stack: netStack, binding: binding, cancel: cancel}

	sys, sysErr := newSystemStack(tunFD, tunAddr, fakeAddr, dialer)
	if sysErr == nil {
		session.sys = sys
	}

	m := &mixedLink{fd: tunFD, link: link, sys: sys}
	go m.toTun(ctx)
	go m.fromTun(ctx)

	if sys != nil && !sys.probe(2*time.Second) {
		sys.disable()
	}
	return session, nil
}

// FastPathActive reports whether IPv4 TCP is going through the system stack.
func (s *Session) FastPathActive() bool {
	return s.sys != nil && s.sys.enabled.Load()
}

// FastBytesUp is the client→internet traffic the system path carried. The TUN's
// own counters see each such packet twice (read, then written back rewritten),
// so real upload = TUN tx − FastBytesDown and real download = TUN rx − FastBytesUp.
func (s *Session) FastBytesUp() int64 {
	if s.sys == nil {
		return 0
	}
	return s.sys.bytesUp.Load()
}

// FastBytesDown is the internet→client traffic the system path carried.
func (s *Session) FastBytesDown() int64 {
	if s.sys == nil {
		return 0
	}
	return s.sys.bytesDown.Load()
}

type mixedLink struct {
	fd   int
	link *channel.Endpoint
	sys  *systemStack
}

// fromTun reads every packet off the TUN and hands it to a worker. Packets of
// one flow always go to the same worker, so a flow is never reordered, while
// different flows are processed on different cores.
func (m *mixedLink) fromTun(ctx context.Context) {
	queues := make([]chan *packetBuf, mixedWorkers())
	for i := range queues {
		queues[i] = make(chan *packetBuf, 512)
		go m.worker(ctx, queues[i])
	}
	defer func() {
		for _, q := range queues {
			close(q)
		}
	}()

	for ctx.Err() == nil {
		b := packetPool.Get().(*packetBuf)
		n, err := unix.Read(m.fd, b.data[:])
		if err != nil {
			packetPool.Put(b)
			if err == unix.EINTR || err == unix.EAGAIN {
				continue
			}
			return // fd closed: the session is over
		}
		if n < 1 {
			packetPool.Put(b)
			continue
		}
		b.n = n
		queues[flowHash(b.data[:n])%uint32(len(queues))] <- b
	}
}

type packetBuf struct {
	data [65536]byte
	n    int
}

var packetPool = sync.Pool{New: func() any { return new(packetBuf) }}

func mixedWorkers() int {
	n := runtime.NumCPU() / 2
	if n < 2 {
		n = 2
	}
	if n > 4 {
		n = 4
	}
	return n
}

func (m *mixedLink) worker(ctx context.Context, q chan *packetBuf) {
	for b := range q {
		m.dispatch(b.data[:b.n])
		packetPool.Put(b)
	}
}

func (m *mixedLink) dispatch(pkt []byte) {
	if m.sys != nil && m.sys.handle(pkt) {
		return
	}
	var proto tcpip.NetworkProtocolNumber
	switch pkt[0] >> 4 {
	case 4:
		proto = ipv4.ProtocolNumber
	case 6:
		proto = ipv6.ProtocolNumber
	default:
		return
	}
	pb := stack.NewPacketBuffer(stack.PacketBufferOptions{
		Payload: buffer.MakeWithData(append([]byte(nil), pkt...)),
	})
	m.link.InjectInbound(proto, pb)
	pb.DecRef()
}

// flowHash maps a packet to its flow: same addresses and ports, same value.
func flowHash(p []byte) uint32 {
	var h uint32
	switch {
	case len(p) >= 24 && p[0]>>4 == 4:
		ihl := int(p[0]&0x0f) * 4
		h = binary.BigEndian.Uint32(p[12:16]) ^ binary.BigEndian.Uint32(p[16:20])
		if len(p) >= ihl+4 {
			h ^= binary.BigEndian.Uint32(p[ihl : ihl+4])
		}
	case len(p) >= 44 && p[0]>>4 == 6:
		for i := 8; i < 40; i += 4 {
			h ^= binary.BigEndian.Uint32(p[i : i+4])
		}
		h ^= binary.BigEndian.Uint32(p[40:44])
	}
	h ^= h >> 16
	h *= 0x45d9f3b
	h ^= h >> 16
	return h
}

// toTun writes what gVisor sends back out to the TUN.
func (m *mixedLink) toTun(ctx context.Context) {
	for {
		pb := m.link.ReadContext(ctx)
		if pb == nil {
			return
		}
		view := pb.ToView()
		unix.Write(m.fd, view.AsSlice())
		view.Release()
		pb.DecRef()
	}
}

// ---- system stack -----------------------------------------------------------

type flowKey struct {
	src, dst         netip.Addr
	srcPort, dstPort uint16
}

type sysSession struct {
	key     flowKey
	natPort uint16
	created time.Time
	probe   bool
}

type systemStack struct {
	fd         int
	tunAddr    netip.Addr
	fakeAddr   netip.Addr
	listenPort uint16
	listener   *net.TCPListener
	dialer     *net.Dialer
	enabled    atomic.Bool

	mu      sync.RWMutex
	byKey   map[flowKey]*sysSession
	byPort  map[uint16]*sysSession
	nextNat uint16

	probeOK chan struct{}

	// Bytes the system path moved, client→listener and listener→client. Each of
	// those packets crosses the TUN twice, so the interface counters include them
	// in both directions; see Session.FastBytesUp.
	bytesUp, bytesDown atomic.Int64
}

const (
	natPortFirst = 20000
	natPortLast  = 60000
	probePort    = 19999

	lingerAfterClose = 60 * time.Second
)

func newSystemStack(fd int, tunAddr, fakeAddr netip.Addr, dialer *net.Dialer) (*systemStack, error) {
	ln, err := net.ListenTCP("tcp4", &net.TCPAddr{IP: tunAddr.AsSlice(), Port: 0})
	if err != nil {
		return nil, err
	}
	s := &systemStack{
		fd:         fd,
		tunAddr:    tunAddr,
		fakeAddr:   fakeAddr,
		listenPort: uint16(ln.Addr().(*net.TCPAddr).Port),
		listener:   ln,
		dialer:     dialer,
		byKey:      map[flowKey]*sysSession{},
		byPort:     map[uint16]*sysSession{},
		nextNat:    natPortFirst,
		probeOK:    make(chan struct{}, 1),
	}
	s.enabled.Store(true)
	go s.acceptLoop()
	go s.gcLoop()
	return s, nil
}

func (s *systemStack) disable() {
	s.enabled.Store(false)
	s.listener.Close()
}

func (s *systemStack) close() {
	s.disable()
}

// probe writes a SYN as the rewrite would produce it and waits for the kernel to
// hand it to the listener. It proves the platform delivers rewritten packets.
func (s *systemStack) probe(timeout time.Duration) bool {
	probe := &sysSession{natPort: probePort, created: time.Now(), probe: true}
	s.mu.Lock()
	s.byPort[probePort] = probe
	s.mu.Unlock()
	defer func() {
		s.mu.Lock()
		delete(s.byPort, probePort)
		s.mu.Unlock()
	}()

	syn := buildSyn(s.fakeAddr, probePort, s.tunAddr, s.listenPort)
	deadline := time.After(timeout)
	retry := time.NewTicker(500 * time.Millisecond)
	defer retry.Stop()
	unix.Write(s.fd, syn)
	for {
		select {
		case <-s.probeOK:
			return true
		case <-retry.C:
			unix.Write(s.fd, syn)
		case <-deadline:
			return false
		}
	}
}

// handle takes IPv4 TCP packets; it returns false for anything gVisor should get.
func (s *systemStack) handle(pkt []byte) bool {
	if !s.enabled.Load() || len(pkt) < 20 || pkt[0]>>4 != 4 || pkt[9] != unix.IPPROTO_TCP {
		return false
	}
	ihl := int(pkt[0]&0x0f) * 4
	if ihl < 20 || len(pkt) < ihl+20 {
		return false
	}
	// Fragments carry no ports; leave them to gVisor.
	if binary.BigEndian.Uint16(pkt[6:8])&0x3fff != 0 {
		return false
	}
	src := netip.AddrFrom4([4]byte(pkt[12:16]))
	dst := netip.AddrFrom4([4]byte(pkt[16:20]))
	tcpHdr := pkt[ihl:]
	srcPort := binary.BigEndian.Uint16(tcpHdr[0:2])
	dstPort := binary.BigEndian.Uint16(tcpHdr[2:4])

	if dst == s.fakeAddr {
		// From the local listener back to the client.
		s.mu.RLock()
		sess := s.byPort[dstPort]
		s.mu.RUnlock()
		if sess != nil && sess.probe && srcPort == s.listenPort && tcpHdr[13]&0x12 == 0x12 {
			// Complete the probe's handshake: ACK the listener's SYN-ACK.
			seq := binary.BigEndian.Uint32(tcpHdr[4:8])
			unix.Write(s.fd, buildTCP4(s.fakeAddr, probePort, s.tunAddr, s.listenPort, 2, seq+1, 0x10))
			return true
		}
		if sess == nil || sess.probe || srcPort != s.listenPort {
			return true // swallow: never leak the fake address to gVisor
		}
		rewriteTCP4(pkt, ihl, sess.key.dst, sess.key.dstPort, sess.key.src, sess.key.srcPort)
		unix.Write(s.fd, pkt)
		s.bytesDown.Add(int64(len(pkt)))
		return true
	}

	key := flowKey{src: src, dst: dst, srcPort: srcPort, dstPort: dstPort}
	s.mu.RLock()
	sess := s.byKey[key]
	s.mu.RUnlock()
	if sess == nil {
		if tcpHdr[13]&0x12 != 0x02 {
			return false // not a fresh SYN: a flow gVisor already owns (or stray); let it decide
		}
		s.mu.Lock()
		sess = s.byKey[key]
		if sess == nil {
			sess = s.newSession(key)
		}
		s.mu.Unlock()
		if sess == nil {
			return false // NAT table full: fall back to gVisor
		}
	}
	rewriteTCP4(pkt, ihl, s.fakeAddr, sess.natPort, s.tunAddr, s.listenPort)
	unix.Write(s.fd, pkt)
	s.bytesUp.Add(int64(len(pkt)))
	return true
}

func (s *systemStack) newSession(key flowKey) *sysSession {
	for i := 0; i <= natPortLast-natPortFirst; i++ {
		s.nextNat++
		if s.nextNat > natPortLast || s.nextNat < natPortFirst {
			s.nextNat = natPortFirst
		}
		if s.byPort[s.nextNat] == nil {
			sess := &sysSession{key: key, natPort: s.nextNat, created: time.Now()}
			s.byKey[key] = sess
			s.byPort[sess.natPort] = sess
			return sess
		}
	}
	return nil
}

func (s *systemStack) drop(sess *sysSession) {
	s.mu.Lock()
	if s.byPort[sess.natPort] == sess {
		delete(s.byPort, sess.natPort)
	}
	if s.byKey[sess.key] == sess {
		delete(s.byKey, sess.key)
	}
	s.mu.Unlock()
}

func (s *systemStack) acceptLoop() {
	for {
		conn, err := s.listener.AcceptTCP()
		if err != nil {
			if !s.enabled.Load() {
				return
			}
			continue
		}
		go s.serve(conn)
	}
}

func (s *systemStack) serve(client *net.TCPConn) {
	port := uint16(client.RemoteAddr().(*net.TCPAddr).Port)
	s.mu.RLock()
	sess := s.byPort[port]
	s.mu.RUnlock()
	if sess == nil {
		client.Close()
		return
	}
	if sess.probe {
		select {
		case s.probeOK <- struct{}{}:
		default:
		}
		client.SetLinger(0)
		client.Close()
		return
	}
	// Keep the mapping a while after the relay ends, so the closing FIN/ACK
	// exchange is still translated instead of reaching gVisor as a stray.
	defer time.AfterFunc(lingerAfterClose, func() { s.drop(sess) })

	upstream, err := s.dialer.Dial("tcp", netip.AddrPortFrom(sess.key.dst, sess.key.dstPort).String())
	if err != nil {
		client.SetLinger(0) // reset, so the client fails fast like a refused connection
		client.Close()
		return
	}
	relayHalfClose(client, upstream.(*net.TCPConn))
}

// relayHalfClose splices two kernel sockets and propagates half-close both ways.
func relayHalfClose(a, b *net.TCPConn) {
	var wg sync.WaitGroup
	wg.Add(2)
	go func() { copyBuffered(b, a); b.CloseWrite(); wg.Done() }()
	go func() { copyBuffered(a, b); a.CloseWrite(); wg.Done() }()
	wg.Wait()
	a.Close()
	b.Close()
}

// copyBuffered copies with io.Copy semantics; between two *net.TCPConn the
// runtime uses splice(2), so the data never passes through this process.
func copyBuffered(dst *net.TCPConn, src *net.TCPConn) {
	dst.ReadFrom(src)
}

// gcLoop drops sessions whose handshake never reached the listener.
func (s *systemStack) gcLoop() {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()
	for range ticker.C {
		if !s.enabled.Load() {
			return
		}
		s.mu.Lock()
		for k, sess := range s.byKey {
			if time.Since(sess.created) > 10*time.Minute {
				delete(s.byKey, k)
				delete(s.byPort, sess.natPort)
			}
		}
		s.mu.Unlock()
	}
}

// rewriteTCP4 changes addresses and ports in place, updating both checksums
// incrementally (RFC 1624).
func rewriteTCP4(pkt []byte, ihl int, src netip.Addr, srcPort uint16, dst netip.Addr, dstPort uint16) {
	tcpHdr := pkt[ihl:]
	ipSum := binary.BigEndian.Uint16(pkt[10:12])
	tcpSum := binary.BigEndian.Uint16(tcpHdr[16:18])

	s4, d4 := src.As4(), dst.As4()
	for i := 0; i < 4; i += 2 {
		for _, f := range [2]struct {
			off int
			val uint16
		}{{12 + i, binary.BigEndian.Uint16(s4[i:])}, {16 + i, binary.BigEndian.Uint16(d4[i:])}} {
			old := binary.BigEndian.Uint16(pkt[f.off:])
			ipSum = checksumAdjust(ipSum, old, f.val)
			tcpSum = checksumAdjust(tcpSum, old, f.val)
			binary.BigEndian.PutUint16(pkt[f.off:], f.val)
		}
	}
	tcpSum = checksumAdjust(tcpSum, binary.BigEndian.Uint16(tcpHdr[0:2]), srcPort)
	binary.BigEndian.PutUint16(tcpHdr[0:2], srcPort)
	tcpSum = checksumAdjust(tcpSum, binary.BigEndian.Uint16(tcpHdr[2:4]), dstPort)
	binary.BigEndian.PutUint16(tcpHdr[2:4], dstPort)

	binary.BigEndian.PutUint16(pkt[10:12], ipSum)
	binary.BigEndian.PutUint16(tcpHdr[16:18], tcpSum)
}

func checksumAdjust(sum, old, new uint16) uint16 {
	s := uint32(^sum) + uint32(^old) + uint32(new)
	s = (s & 0xffff) + (s >> 16)
	s = (s & 0xffff) + (s >> 16)
	return ^uint16(s)
}

// buildSyn makes a minimal IPv4 TCP SYN (used by the probe).
func buildSyn(src netip.Addr, srcPort uint16, dst netip.Addr, dstPort uint16) []byte {
	return buildTCP4(src, srcPort, dst, dstPort, 1, 0, 0x02)
}

// buildTCP4 makes a minimal IPv4 TCP segment with no payload.
func buildTCP4(src netip.Addr, srcPort uint16, dst netip.Addr, dstPort uint16, seq, ack uint32, flags byte) []byte {
	pkt := make([]byte, 40)
	pkt[0] = 0x45
	binary.BigEndian.PutUint16(pkt[2:], 40)
	pkt[8] = 64
	pkt[9] = unix.IPPROTO_TCP
	s4, d4 := src.As4(), dst.As4()
	copy(pkt[12:16], s4[:])
	copy(pkt[16:20], d4[:])
	binary.BigEndian.PutUint16(pkt[10:], ipChecksum(pkt[:20], 0))

	tcpHdr := pkt[20:]
	binary.BigEndian.PutUint16(tcpHdr[0:], srcPort)
	binary.BigEndian.PutUint16(tcpHdr[2:], dstPort)
	binary.BigEndian.PutUint32(tcpHdr[4:], seq)
	binary.BigEndian.PutUint32(tcpHdr[8:], ack)
	tcpHdr[12] = 5 << 4
	tcpHdr[13] = flags
	binary.BigEndian.PutUint16(tcpHdr[14:], 65535)

	pseudo := uint32(0)
	for i := 12; i < 20; i += 2 {
		pseudo += uint32(binary.BigEndian.Uint16(pkt[i:]))
	}
	pseudo += unix.IPPROTO_TCP + 20
	binary.BigEndian.PutUint16(tcpHdr[16:], ipChecksum(tcpHdr, pseudo))
	return pkt
}

func ipChecksum(b []byte, initial uint32) uint16 {
	sum := initial
	for i := 0; i+1 < len(b); i += 2 {
		sum += uint32(binary.BigEndian.Uint16(b[i:]))
	}
	if len(b)%2 == 1 {
		sum += uint32(b[len(b)-1]) << 8
	}
	for sum>>16 != 0 {
		sum = (sum & 0xffff) + (sum >> 16)
	}
	return ^uint16(sum)
}
