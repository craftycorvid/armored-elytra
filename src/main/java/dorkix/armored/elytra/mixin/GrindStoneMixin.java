package dorkix.armored.elytra.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dorkix.armored.elytra.ArmoredElytra;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

@Mixin(GrindstoneScreenHandler.class)
public abstract class GrindStoneMixin extends ScreenHandler {

    protected GrindStoneMixin(ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

    @Shadow
    @Final
    private ScreenHandlerContext context;

    @Shadow
    @Final
    private Inventory result;

    @Shadow
    @Final
    Inventory input;

    @Inject(method = "Lnet/minecraft/screen/GrindstoneScreenHandler;updateResult()V", at = @At("RETURN"))
    private void replaceArmoredElytraResult(CallbackInfo ci) {
        var inputItem1 = this.input.getStack(0);
        var inputItem2 = this.input.getStack(1);
        if (inputItem1.isOf(Items.ELYTRA)) {
            showSplitResult(inputItem1, 0);

        } else if (inputItem2.isOf(Items.ELYTRA)) {
            showSplitResult(inputItem2, 1);
        }

    }

    private void showSplitResult(ItemStack inputItem, int slot) {

        // get the saved chestplate ItemStack as nbt
        Optional<NbtCompound> armorDataNbt = inputItem
                .getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .copyNbt().getCompound(ArmoredElytra.CHESTPLATE_DATA.toString());
        Optional<NbtCompound> elytraDataNbt = inputItem
                .getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .copyNbt().getCompound(ArmoredElytra.ELYTRA_DATA.toString());

        // Vanilla Tweaks Compatibility
        BundleContentsComponent bundleContents = inputItem
                .getOrDefault(DataComponentTypes.BUNDLE_CONTENTS, BundleContentsComponent.DEFAULT);
        if (!bundleContents.isEmpty()) {
            bundleContents.iterate().forEach(item -> {
                if (item.isIn(ItemTags.CHEST_ARMOR)) {
                    this.context.run((world, blockpos) -> {
                        this.result.setStack(slot, item);
                    });
                    sendContentUpdates();
                    return;
                }
            });
        }

        if (elytraDataNbt.isEmpty() || armorDataNbt.isEmpty()) {
            return;
        }

        NbtCompound elytraData = elytraDataNbt.get();
        NbtCompound armorData = armorDataNbt.get();

        // if any of the source item data is missing skip this action
        if (armorData.isEmpty() || elytraData.isEmpty())
            return;

        // if found set the GrindStone result slot to contain the chestplate item
        this.context.run((world, blockpos) -> {
            this.result.setStack(slot,
                    ItemStack.fromNbt(world.getRegistryManager(), armorData).orElse(ItemStack.EMPTY));
        });

        sendContentUpdates();
    }

    // to access the Grindstone screen and its data in the ResultSlotMixin
    @Mixin(GrindstoneScreenHandler.class)
    public interface GrindstoneScreenHandlerAccessor {
        @Accessor
        Inventory getResult();

        @Accessor
        Inventory getInput();

        @Accessor
        ScreenHandlerContext getContext();
    }

    // target GrindstoneScreenHandler's result slot
    @Mixin(targets = "net/minecraft/screen/GrindstoneScreenHandler$4")
    public static abstract class ResultSlotMixin extends Slot {
        public ResultSlotMixin(Inventory inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
        }

        @Shadow
        @Final
        GrindstoneScreenHandler field_16780;

        // try split the elytra for the given slot
        private boolean trySplitArmoredElytra(int slot) {
            var armoredElytra = ((GrindstoneScreenHandlerAccessor) field_16780).getInput().getStack(slot);
            // get the armored elytra source items nbt data
            NbtCompound customData = armoredElytra
                    .getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                    .copyNbt();

            Optional<NbtCompound> elytraDataNbt = customData.getCompound(ArmoredElytra.ELYTRA_DATA.toString());
            Optional<NbtCompound> armorDataNbt = customData.getCompound(ArmoredElytra.CHESTPLATE_DATA.toString());

            if (elytraDataNbt.isEmpty() || armorDataNbt.isEmpty()) {
                return false;
            }

            NbtCompound elytraData = elytraDataNbt.get();
            NbtCompound armorData = armorDataNbt.get();

            // if not an armored elytra return to normal functioning
            if (elytraData.isEmpty() || armorData.isEmpty()) {
                return false;
            }
            var context = ((GrindstoneScreenHandlerAccessor) field_16780).getContext();

            context.run((world, blockPos) -> {
                // spawn a little xp
                if (world instanceof ServerWorld) {
                    ExperienceOrbEntity.spawn((ServerWorld) world, blockPos.up().toCenterPos(), 1);
                }

                // play the grindstone sound
                world.playSound(null, blockPos, SoundEvents.BLOCK_GRINDSTONE_USE,
                        SoundCategory.BLOCKS);

                // replace the input armored elytra with the source elytra
                var sourceElytra = ItemStack
                        .fromNbt(world.getRegistryManager(),
                                elytraData)
                        .orElse(ItemStack.EMPTY);
                var sourceArmor = ItemStack
                        .fromNbt(world.getRegistryManager(),
                                elytraData)
                        .orElse(ItemStack.EMPTY);

                // check for compatible later added enchants
                var currentEnchants = armoredElytra.getEnchantments().getEnchantments();
                for (var ce : currentEnchants) {
                    if (!ce.value().isSupportedItem(sourceElytra)) {
                        continue;
                    }

                    int currentLevel = armoredElytra.getEnchantments().getLevel(ce);
                    int elytraLevel = sourceElytra.getEnchantments().getLevel(ce);
                    int armorLevel = sourceArmor.getEnchantments().getLevel(ce);

                    if (currentLevel > elytraLevel && elytraLevel > 0 && elytraLevel >= armorLevel) {
                        sourceElytra.addEnchantment(ce, currentLevel);
                    }

                }

                ((GrindstoneScreenHandlerAccessor) field_16780).getInput().setStack(slot,
                        sourceElytra);
            });
            return true;
        }

        // try split the elytra for the given slot (Vanilla Tweaks Format)
        private boolean trySplitVTArmoredElytra(int slot) {
            BundleContentsComponent bundleContents = ((GrindstoneScreenHandlerAccessor) field_16780)
                    .getInput().getStack(slot).getOrDefault(DataComponentTypes.BUNDLE_CONTENTS,
                            BundleContentsComponent.DEFAULT);
            if (bundleContents.isEmpty())
                return false;
 
            var context = ((GrindstoneScreenHandlerAccessor) field_16780).getContext();
            bundleContents.iterate().forEach(item -> {
                if (item.isOf(Items.ELYTRA)) {
                    context.run((world, blockPos) -> {
                        world.playSound(null, blockPos, SoundEvents.BLOCK_GRINDSTONE_USE,
                                SoundCategory.BLOCKS);
                        ((GrindstoneScreenHandlerAccessor) field_16780).getInput().setStack(slot,
                                item);
                    });
                }
            });
            return true;
        }

        // When the user takes out result chestplate from the
        // GrindStoneMixin.showSplitResult() try getting the source elytry from the
        // input slots, replace the armored elytra with the source elytra and cancel the
        // takout function so that the grindstone does not destroy the source elytra in
        // the input slot.
        // If the input slots do not contain an armored elytra just return to normal
        // grindstone function
        @Inject(method = "onTakeItem", at = @At("HEAD"), cancellable = true)
        private void takeSeparatedChestplate(PlayerEntity player, ItemStack stack,
                CallbackInfo ci) {

            if (!trySplitArmoredElytra(0) && !trySplitArmoredElytra(1)
                    && !trySplitVTArmoredElytra(0) && !trySplitVTArmoredElytra(1)) {
                return;
            }

            ci.cancel();
        }
    }
}
