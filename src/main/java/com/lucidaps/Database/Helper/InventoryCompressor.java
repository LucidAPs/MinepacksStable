package com.lucidaps.Database.Helper;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class InventoryCompressor {
	private ItemStack[] targetStacks, inputStacks;
	private int filled = 0;
	private ItemStack lastStack = null;
	private List<ItemStack> toMuch;

	public InventoryCompressor(ItemStack[] stacks) {
		this(stacks, stacks.length);
	}

	public InventoryCompressor(ItemStack[] stacks, int targetSize) {
		inputStacks = stacks;
		targetStacks = new ItemStack[targetSize];
		toMuch = new ArrayList<>(inputStacks.length - targetStacks.length);
	}

	public InventoryCompressor(ItemStack[] input, ItemStack[] output) {
		inputStacks = input;
		targetStacks = output;
		toMuch = new ArrayList<>(inputStacks.length - targetStacks.length);
	}

	public ItemStack[] getTargetStacks() {
		return targetStacks;
	}

	public ItemStack[] getInputStacks() {
		return inputStacks;
	}

	public List<ItemStack> getToMuch() {
		return toMuch;
	}

	public List<ItemStack> sort() {
		for (int i = 0; i < inputStacks.length; i++) {
			ItemStack stack = inputStacks[i];
			if (stack == null || stack.getType() == Material.AIR || stack.getAmount() < 1) continue;
			add(stack);
			move(stack, i + 1);
		}
		return toMuch;
	}

	private void move(ItemStack stack, int start) {
		// Search items that are the same
		int differentMetaStart = -1;
		for (int i = start; i < inputStacks.length; i++) {
			ItemStack stack2 = inputStacks[i];
			if (stack2 == null || stack2.getType() == Material.AIR || stack2.getAmount() < 1) continue;
			if (stack.isSimilar(stack2)) {
				add(stack2); // Add item to sorted array
				inputStacks[i] = null; // Remove item from input
			} else if (differentMetaStart == -1 && stack.getType() == stack2.getType()) {
				// Same material but different meta
				differentMetaStart = i;
			}
		}
		if (differentMetaStart >= 0)
			move(inputStacks[differentMetaStart], differentMetaStart);
	}

	private void add(ItemStack stack) {
		if (lastStack != null && stack.isSimilar(lastStack) && lastStack.getAmount() < lastStack.getMaxStackSize()) {
			// There is still space on the last stack, try to add it
			int free = lastStack.getMaxStackSize() - lastStack.getAmount();
			int place = Math.min(free, stack.getAmount());
			lastStack.setAmount(lastStack.getAmount() + place);
			stack.setAmount(stack.getAmount() - place);
		}
		if (stack.getAmount() < 1) return;
		if (filled == targetStacks.length) {
			// The new item stack is full, add it to overfill list
			toMuch.add(stack);
		} else {
			// Add the rest to the new inventory
			targetStacks[filled++] = stack;
			lastStack = stack;
		}
	}

	public List<ItemStack> compress() {
		for (ItemStack stack : inputStacks) {
			if (stack == null || stack.getType() == Material.AIR || stack.getAmount() < 1) continue;
			tryToStack(stack);
			if (stack.getAmount() == 0) continue;
			if (filled == targetStacks.length) {
				toMuch.add(stack);
			} else {
				targetStacks[filled++] = stack;
			}
		}
		return toMuch;
	}

	private void tryToStack(ItemStack stack) {
		if (stack.getAmount() >= stack.getMaxStackSize()) return;
		for (int i = 0; i < filled && stack.getAmount() > 0; i++) {
			if (stack.isSimilar(targetStacks[i]) && targetStacks[i].getAmount() < targetStacks[i].getMaxStackSize()) {
				// Same material and none full stack
				int move = targetStacks[i].getMaxStackSize() - targetStacks[i].getAmount();
				move = Math.min(stack.getAmount(), move);
				targetStacks[i].setAmount(targetStacks[i].getAmount() + move);
				stack.setAmount(stack.getAmount() - move);
			}
		}
	}

	public List<ItemStack> fast() {
		for (ItemStack stack : inputStacks) {
			if (stack == null || stack.getType() == Material.AIR || stack.getAmount() < 1) continue;
			if (filled == targetStacks.length) {
				toMuch.add(stack);
			} else {
				targetStacks[filled++] = stack;
			}
		}
		return toMuch;
	}
}
