package com.orbitcore.registry;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Материалы брони мода. */
public final class ModArmorMaterials {
    /**
     * Скафандр Т1. Чуть слабее железной брони: он держит вакуум, а не удары.
     * Имя без пространства имён: по нему игра ищет текстуру
     * {@code minecraft:textures/models/armor/orbitcore_spacesuit_layer_N.png}.
     */
    public static final ArmorMaterial SPACESUIT = new ArmorMaterial() {
        @Override
        public int getDurability(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> 195;
                case CHESTPLATE -> 240;
                case LEGGINGS -> 225;
                case BOOTS -> 165;
            };
        }

        @Override
        public int getProtection(ArmorItem.Type type) {
            return switch (type) {
                case HELMET, BOOTS -> 2;
                case CHESTPLATE -> 5;
                case LEGGINGS -> 4;
            };
        }

        @Override
        public int getEnchantability() {
            return 9;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ITEM_ARMOR_EQUIP_IRON;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.ofItems(ModItems.TITANIUM_PLATE);
        }

        @Override
        public String getName() {
            return "orbitcore_spacesuit";
        }

        @Override
        public float getToughness() {
            return 0;
        }

        @Override
        public float getKnockbackResistance() {
            return 0;
        }
    };

    private ModArmorMaterials() {
    }
}
