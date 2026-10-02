package com.hepdd.gtmthings.api.misc;

import com.gregtechceu.gtceu.api.cover.filter.Filter;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged;

/** Rehydrate GT8's transient filter after its persisted item inventory has been loaded or synced. */
public final class PersistentFilterHandler<T> extends FilterHandler<T> {

    private boolean restoring;

    public PersistentFilterHandler(ISyncManaged container, Class<T> type) {
        super(container, type);
    }

    private void restoreFilter() {
        if (restoring || super.isFilterPresent() || getFilterItem().isEmpty()) return;
        restoring = true;
        try {
            setFilterItem(getFilterItem().copy());
        } finally {
            restoring = false;
        }
    }

    @Override
    public boolean isFilterPresent() {
        restoreFilter();
        return super.isFilterPresent();
    }

    @Override
    public Filter<T> getFilter() {
        restoreFilter();
        return super.getFilter();
    }
}
