package dev.shizzi

/** Addresses the mixed engine needs: the TUN's IPv4 address and an unused one in its subnet. */
data class FastPath(val tunAddress: String, val fakeAddress: String)
