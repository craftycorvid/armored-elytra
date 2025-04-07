package dorkix.armored.elytra.mixin;

import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dorkix.armored.elytra.ArmoredElytra;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

@Mixin(Entity.class)
public abstract class EntityLavaHurtMixin  {
    @Inject(method = "setOnFireFromLava", at = @At("TAIL"))
    private void splitNetheriteInLava(CallbackInfo ci) {
        if ((Entity) (Object) this instanceof ItemEntity) {
            ItemEntity thisObject = (ItemEntity) (Object) this;

            ItemStack itemStack = thisObject.getStack();
            if (itemStack.isOf(Items.ELYTRA)) {
                BundleContentsComponent bundleContents = itemStack.getOrDefault(
                        DataComponentTypes.BUNDLE_CONTENTS, BundleContentsComponent.DEFAULT);
                if (!bundleContents.isEmpty()) {
                    // Handle Vanilla Tweaks data
                    bundleContents.iterate().forEach(item -> {
                        if (item.isOf(Items.NETHERITE_CHESTPLATE)) {
                            thisObject.setStack(item);
                        }
                    });
                } else {
                    // Handle native mod data
                    Optional<NbtCompound> armorDataNbt = itemStack
                            .getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                            .copyNbt().getCompound(ArmoredElytra.CHESTPLATE_DATA.toString());
                    if (armorDataNbt.isEmpty())
                        return;

                    NbtCompound armorData = armorDataNbt.get();
                    if (armorData.isEmpty())
                        return;

                    ItemStack chestplate =
                            ItemStack.fromNbt(thisObject.getWorld().getRegistryManager(), armorData)
                                    .orElse(ItemStack.EMPTY);
                    ((ItemEntity) (Object) this).setStack(chestplate);
                }
                World world = thisObject.getWorld();
                world.playSound((Entity) null, thisObject.getX(), thisObject.getY(),
                        thisObject.getZ(), SoundEvents.ENTITY_GENERIC_BURN,
                        thisObject.getSoundCategory(), 0.4F, 2.0F + thisObject.getRandom().nextFloat() * 0.4F);
            }
        }
    }
}
