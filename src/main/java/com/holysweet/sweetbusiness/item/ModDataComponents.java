package com.holysweet.sweetbusiness.item;

import com.holysweet.sweetbusiness.SweetBusiness;
import com.holysweet.sweetbusiness.contract.Contract;
import com.holysweet.sweetbusiness.contract.ContractCodecs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SweetBusiness.MODID);

    // Persistent components also use their codec for network synchronization by default.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Contract>> CONTRACT =
            COMPONENTS.register("contract", () -> DataComponentType.<Contract>builder()
                    .persistent(ContractCodecs.CONTRACT).build());

    private ModDataComponents() {}
}
