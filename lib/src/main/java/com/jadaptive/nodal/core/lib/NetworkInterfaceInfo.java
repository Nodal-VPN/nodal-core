package com.jadaptive.nodal.core.lib;

public interface NetworkInterfaceInfo<INFO extends InterfaceAddressInfo> {

	String getName();

	String getDisplayName();

	String getHardwareAddress();

	int getMtu();

	int getIndex();

	INFO[] getInterfaceAddresses();

}