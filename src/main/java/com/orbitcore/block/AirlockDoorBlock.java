package com.orbitcore.block;

import net.minecraft.block.BlockSetType;
import net.minecraft.block.DoorBlock;

/**
 * Шлюзовая дверь: стальная, с уплотнителем. Закрытая держит воздух
 * в загерметизированном отсеке, открытая — выпускает его.
 */
public class AirlockDoorBlock extends DoorBlock {
    public AirlockDoorBlock(Settings settings, BlockSetType type) {
        super(settings, type);
    }
}
