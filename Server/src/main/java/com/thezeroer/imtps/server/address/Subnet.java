package com.thezeroer.imtps.server.address;


import java.net.InetAddress;
import java.util.Arrays;
import java.util.Objects;

/**
 * 子网
 *
 * @author NiZhanBo
 * @since 2025/08/13
 * @version 1.0.0
 */
public class Subnet {
    private final InetAddress networkAddress;
    private final int subnetMaskLength;
    private final byte[] maskBytes;

    public Subnet(String cidr) {
        try {
            String[] parts = cidr.split("/");
            this.networkAddress = InetAddress.getByName(parts[0]);
            this.subnetMaskLength = Integer.parseInt(parts[1]);
            this.maskBytes = createMask(subnetMaskLength, networkAddress.getAddress().length);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid CIDR: " + cidr, e);
        }
    }

    public boolean contains(InetAddress addr) {
        byte[] addrBytes = addr.getAddress();
        byte[] netBytes = networkAddress.getAddress();

        if (addrBytes.length != netBytes.length) return false;

        for (int i = 0; i < addrBytes.length; i++) {
            if ((addrBytes[i] & maskBytes[i]) != (netBytes[i] & maskBytes[i])) {
                return false;
            }
        }
        return true;
    }

    private byte[] createMask(int prefixLength, int size) {
        byte[] mask = new byte[size];
        for (int i = 0; i < prefixLength; i++) {
            mask[i / 8] |= (byte) (1 << (7 - (i % 8)));
        }
        return mask;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Subnet subnet)) {
            return false;
        }else {
            // 通过IP字节数组和掩码长度判断相等
            return subnetMaskLength == subnet.subnetMaskLength &&
                    Arrays.equals(networkAddress.getAddress(), subnet.networkAddress.getAddress());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(subnetMaskLength, Arrays.hashCode(networkAddress.getAddress()));
    }
}
