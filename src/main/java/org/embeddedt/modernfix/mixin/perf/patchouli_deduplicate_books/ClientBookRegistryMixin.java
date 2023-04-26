package org.embeddedt.modernfix.mixin.perf.patchouli_deduplicate_books;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vazkii.patchouli.client.book.BookContents;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.BookPage;
import vazkii.patchouli.client.book.ClientBookRegistry;
import vazkii.patchouli.client.book.page.PageTemplate;
import vazkii.patchouli.client.book.template.BookTemplate;
import vazkii.patchouli.client.book.template.TemplateComponent;
import vazkii.patchouli.client.book.template.component.ComponentItemStack;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Mixin(ClientBookRegistry.class)
public class ClientBookRegistryMixin {
    @Inject(method = "reload", at = @At("RETURN"), remap = false)
    private void performDeduplication(CallbackInfo ci) {
        Field templateField = ObfuscationReflectionHelper.findField(PageTemplate.class, "template");
        Field contentsField = ObfuscationReflectionHelper.findField(Book.class, "contents");
        Field componentsField = ObfuscationReflectionHelper.findField(BookTemplate.class, "components");
        Field itemsField = ObfuscationReflectionHelper.findField(ComponentItemStack.class, "items");
        int numItemsCleared = 0;
        for(Book book : BookRegistry.INSTANCE.books.values()) {
            try {
                BookContents contents = (BookContents)contentsField.get(book);
                for(BookEntry entry : contents.entries.values()) {
                    for (BookPage page : entry.getPages()) {
                        if (page instanceof PageTemplate) {
                            List<TemplateComponent> components;

                            BookTemplate template = (BookTemplate) templateField.get(page);
                            components = (List<TemplateComponent>) componentsField.get(template);
                            for (TemplateComponent component : components) {
                                if (component instanceof ComponentItemStack) {
                                    ItemStack[] items = (ItemStack[]) itemsField.get(component);
                                    for (ItemStack item : items) {
                                        if (item.getItem() == Items.AIR) {
                                            // remove any NBT
                                            CompoundTag tag = item.getTag();
                                            if (tag != null) {
                                                numItemsCleared++;
                                                List<String> keys = new ArrayList<>(tag.getAllKeys());
                                                for (String key : keys)
                                                    item.removeTagKey(key);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch(ReflectiveOperationException e) {
                continue;
            }
        }
        ModernFix.LOGGER.info("Cleared {} unneeded book NBT tags", numItemsCleared);
    }
}
