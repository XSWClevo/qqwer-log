package collector

import (
	"net"
	"testing"
)

func TestSelectPreferredIPAddressPrefersNonLoopbackIPv4(t *testing.T) {
	candidates := []net.IP{
		net.ParseIP("127.0.0.1"),
		net.ParseIP("::1"),
		net.ParseIP("192.168.111.129"),
	}

	got := selectPreferredIPAddress(candidates)

	if got != "192.168.111.129" {
		t.Fatalf("selectPreferredIPAddress() = %q, want %q", got, "192.168.111.129")
	}
}

func TestSelectPreferredIPAddressFallsBackToLoopback(t *testing.T) {
	candidates := []net.IP{
		net.ParseIP("::1"),
		net.ParseIP("127.0.0.1"),
	}

	got := selectPreferredIPAddress(candidates)

	if got != "127.0.0.1" {
		t.Fatalf("selectPreferredIPAddress() = %q, want %q", got, "127.0.0.1")
	}
}
