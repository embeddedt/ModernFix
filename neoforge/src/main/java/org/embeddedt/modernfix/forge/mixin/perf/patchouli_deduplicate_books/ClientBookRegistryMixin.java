package org.embeddedt.modernfix.forge.mixin.perf.patchouli_deduplicate_books;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
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
import java.util.List;

@Mixin(ClientBookRegistry.class)
@RequiresMod("patchouli")
@ClientOnlyMixin
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
                if(contents == null || contents.entries == null)
                    continue;
                for(BookEntry entry : contents.entries.values()) {
                    for(BookPage page : entry.getPages()) {
                        if(page instanceof PageTemplate) {
                            List<TemplateComponent> components;
                            BookTemplate template = (BookTemplate) templateField.get(page);
                            if(template == null)
                                continue;
                            components = (List<TemplateComponent>) componentsField.get(template);
                            if(components == null)
                                continue;
                            for (TemplateComponent component : components) {
                                if (component instanceof ComponentItemStack) {
                                    ItemStack[] items = (ItemStack[]) itemsField.get(component);
                                    if(items == null)
                                        continue;
                                    for (int i = 0; i < items.length; i++) {
                                        if (items[i] != null && items[i].getItem() == Items.AIR) {
                                            numItemsCleared++;
                                            items[i] = ItemStack.EMPTY;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch(ReflectiveOperationException ignored) {
            }
        }
        ModernFix.LOGGER.info("Cleared {} unneeded book NBT tags", numItemsCleared);
    }
}
