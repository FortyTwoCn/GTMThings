package com.hepdd.gtmthings.api.machine;

import com.hepdd.gtmthings.api.capability.IBindable;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;

import javax.annotation.Nullable;

public interface IWirelessEnergyContainerHolder extends IBindable {

    void setWirelessEnergyContainerCache(WirelessEnergyContainer container);

    WirelessEnergyContainer getWirelessEnergyContainerCache();

    @Nullable
    default WirelessEnergyContainer getWirelessEnergyContainer() {
        if (getUUID() == null || com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE == null) return null;
        var container = WirelessEnergyContainer.getOrCreateContainer(getUUID());
        if (container != getWirelessEnergyContainerCache()) setWirelessEnergyContainerCache(container);
        return container;
    }
}
