package com.tom.storagemod.emi;

import com.tom.storagemod.Content;
import com.tom.storagemod.gui.AbstractStorageTerminalScreen;
import com.tom.storagemod.gui.StorageTerminalMenu;
import com.tom.storagemod.util.IAutoFillTerminal;
import com.tom.storagemod.util.IAutoFillTerminal.ISearchHandler;

import com.tom.storagemod.util.StoredItemStack;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.render.EmiSlotOverlay;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

@SuppressWarnings("rawtypes")
@EmiEntrypoint
public class EMIPlugin implements EmiPlugin {

	@SuppressWarnings("unchecked")
    @Override
	public void register(EmiRegistry registry) {
		registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(Content.craftingTerminal.get()));
		registry.addRecipeHandler(Content.craftingTerminalCont.get(), new EmiTransferHandler());
		registry.addGenericDragDropHandler(new EmiGhostIngredientHandler());
		registry.addGenericStackProvider((scr, x, y) -> {
			if(scr instanceof AbstractStorageTerminalScreen<?> t) {
				net.minecraft.world.inventory.Slot sl = t.getSlotUnderMouse();
				if(sl != null)return new EmiStackInteraction(EmiStack.of(sl.getItem()), null, false);
			}
			return EmiStackInteraction.EMPTY;
		});

		Consumer listener = s -> {
			if (s instanceof AbstractStorageTerminalScreen<?> ast) {
				ast.markDirty();
			}
		};
		EmiSlotOverlay.addChangeListener(Content.storageTerminal.get(), listener);
		EmiSlotOverlay.addChangeListener(Content.craftingTerminalCont.get(), listener);
	}

	static {
		IAutoFillTerminal.updateSearch.add(new ISearchHandler() {

			@Override
			public void setSearch(String set) {
				EmiApi.setSearchText(set);
			}

			@Override
			public String getSearch() {
				return EmiApi.getSearchText();
			}

			@Override
			public String getName() {
				return "EMI";
			}
		});

		AbstractStorageTerminalScreen.postRenders.add((
				screen,
				slotsGetter,
				draw,
				mouseX,
				mouseY
		) -> {
			EmiSlotOverlay.Context context = EmiSlotOverlay.getContext();
			if (context.isEmpty()) return;

			draw.pose().pushPose();
			draw.pose().translate(0, 0, EmiSlotOverlay.OVERLAY_Z);
			for (StorageTerminalMenu.SlotStorage slot : slotsGetter.get()) {
				StoredItemStack stack = slot.stack;
				if (stack == null) continue;

				int color = context.apply(EmiStack.of(stack.getStack()));
				if (color != 0) {
					EmiSlotOverlay.renderPreTranslated(draw, slot.xDisplayPosition, slot.yDisplayPosition, color);
				}
			}
			draw.pose().popPose();
		});

		AbstractStorageTerminalScreen.externalSortProviders.add(() -> {
			Function<StoredItemStack, Integer> extractor;
			if (EmiApi.isSearchHighlightActive()) {
				Predicate<EmiStack> query = EmiApi.getSearchQueryPredicate();
				if (query == null) return null;
				extractor = s -> query.test(EmiStack.of(s.getStack())) ? -1 : 0;
			} else if (EmiApi.isInCraftingMode()) {
				Set<EmiStack> synfavs = EmiApi.getActiveSyntheticFavorites();
				if (synfavs.isEmpty()) return null;
				Object2IntMap<EmiStack> map = new Object2IntOpenHashMap<>(synfavs.size());
				int counter = -Integer.MAX_VALUE;
				for (EmiStack stack : synfavs) {
					map.put(stack, counter++);
				}
				extractor = s -> map.getInt(EmiStack.of(s.getStack()));
			} else {
				return null;
			}

			Map<StoredItemStack, Integer> cache = new IdentityHashMap<>();
			return Comparator.<StoredItemStack, Integer>comparing(
					o -> cache.computeIfAbsent(o, extractor)
			);
		});
	}
}
