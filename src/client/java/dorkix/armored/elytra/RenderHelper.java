package dorkix.armored.elytra;

import java.util.Optional;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.ItemTags;

public class RenderHelper {

  public static ItemStack modifyStackWithArmor(ItemStack stack) {
    var player = MinecraftClient.getInstance().player;
    if (!stack.isOf(Items.ELYTRA) || player == null)
      return stack;
    
    // Vanilla Tweaks compatibility
    BundleContentsComponent bundleContents = stack.getOrDefault(DataComponentTypes.BUNDLE_CONTENTS, BundleContentsComponent.DEFAULT);
    if (!bundleContents.isEmpty()) {
      for (ItemStack item : bundleContents.iterate()) {
        if (item.isIn(ItemTags.CHEST_ARMOR)) {
            return item;
        }
      }
    }

    // get the saved chestplate ItemStack as nbt
    Optional<NbtCompound> chestplateDataNbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
        .copyNbt().getCompound(ArmoredElytra.CHESTPLATE_DATA.toString());

    if (chestplateDataNbt.isEmpty()) {
      return stack;
    }

    NbtCompound chestplateData = chestplateDataNbt.get();

    if (chestplateData.isEmpty())
      return stack;

    // Convert the Nbt data to an ItemStack
    var armorItem = ItemStack.fromNbt(player.getRegistryManager(),
        chestplateData);

    if (!armorItem.isPresent())
      return stack;

    return armorItem.get();
  }

  public static ItemStack modifyStackWithElytra(ItemStack stack) {
    var player = MinecraftClient.getInstance().player;
    if (!stack.isOf(Items.ELYTRA) || player == null)
      return stack;
    
    // Vanilla Tweaks compatibility
    BundleContentsComponent bundleContents = stack.getOrDefault(DataComponentTypes.BUNDLE_CONTENTS, BundleContentsComponent.DEFAULT);
    if (!bundleContents.isEmpty()) {
      for (ItemStack item : bundleContents.iterate()) {
        if (item.isOf(Items.ELYTRA)) {
            return item;
        }
      }
    }

    // get the saved elytra ItemStack as nbt
    Optional<NbtCompound> elytraDataNbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
        .copyNbt().getCompound(ArmoredElytra.ELYTRA_DATA.toString());

    if (elytraDataNbt.isEmpty())
      return stack;

    NbtCompound elytraData = elytraDataNbt.get();

    if (elytraData.isEmpty())
      return stack;

    // Convert the Nbt data to an ItemStack
    var elytraItem = ItemStack.fromNbt(player.getRegistryManager(),
        elytraData);

    if (!elytraItem.isPresent())
      return stack;

    return elytraItem.get();
  }
}
