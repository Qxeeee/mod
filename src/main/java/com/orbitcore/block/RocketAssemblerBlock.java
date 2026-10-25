package com.orbitcore.block;

import com.orbitcore.block.entity.MachineBlockEntity;
import com.orbitcore.registry.ModBlockEntities;
import net.minecraft.block.entity.BlockEntityType;

/**
 * Сборочный стенд: модули ракеты складываются снизу вверх, стенд считает ступени,
 * тягу и дельта-V и собирает ракету в один предмет.
 */
public class RocketAssemblerBlock extends MachineBlock {
    public RocketAssemblerBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ROCKET_ASSEMBLER;
    }
}
