package com.bgsoftware.wildchests.objects.chests;

import com.bgsoftware.wildchests.task.SuctionTask;
import com.bgsoftware.wildchests.utils.Executor;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Hopper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

import org.bukkit.inventory.ItemStack;
import com.bgsoftware.wildchests.Locale;
import com.bgsoftware.wildchests.WildChestsPlugin;
import com.bgsoftware.wildchests.api.objects.ChestType;
import com.bgsoftware.wildchests.api.objects.chests.Chest;
import com.bgsoftware.wildchests.api.objects.data.ChestData;
import com.bgsoftware.wildchests.api.objects.data.InventoryData;
import com.bgsoftware.wildchests.listeners.InventoryListener;
import com.bgsoftware.wildchests.objects.WInventory;
import com.bgsoftware.wildchests.objects.WLocation;
import com.bgsoftware.wildchests.task.HopperTask;
import com.bgsoftware.wildchests.utils.ChestUtils;
import com.bgsoftware.wildchests.utils.ItemUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("all")
public abstract class WChest implements Chest {

    protected final WildChestsPlugin plugin = WildChestsPlugin.getPlugin();
    public static final Map<UUID, Chest> viewers = Maps.newHashMap();
    public static final Set<UUID> movingBetweenPages = Sets.newHashSet();
    public static Inventory guiConfirm;
    public static String guiConfirmTitle;

    protected final HopperTask hopperTask;
    protected SuctionTask suctionTask;

    protected final UUID placer;
    protected final WLocation location;
    protected final String name;
    protected final List<WInventory> pages;

    public WChest(UUID placer, WLocation location, ChestData chestData) {
        this.hopperTask = new HopperTask(location);

        if(chestData.isAutoSuction())
            this.suctionTask = new SuctionTask(location);

        this.placer = placer;
        this.location = location;
        this.name = chestData.getName();
        this.pages = new ArrayList<>();
        Map<Integer, InventoryData> pagesData = chestData.getPagesData();
        int size = chestData.getDefaultSize();
        for(int i = 0; i < chestData.getDefaultPagesAmount(); i++){
            String title = pagesData.containsKey(i + 1) ? pagesData.get(i + 1).getTitle() : chestData.getDefaultTitle();
            this.pages.add(i, WInventory.of(size, title));
        }
    }

    @Override
    public UUID getPlacer() {
        return placer;
    }

    @Override
    public Location getLocation() {
        return location.getLocation();
    }

    @Override
    public ChestData getData() {
        return plugin.getChestsManager().getChestData(name);
    }

    @Override
    public ChestType getChestType() {
        return getData().getChestType();
    }

    @Override
    public Inventory getPage(int page) {
        if(page < 0 || page >= pages.size()) return null;
        WInventory pageInv = pages.get(page);
        pageInv.setTitle(getData().getTitle(page + 1).replace("{0}", pages.size() + ""));
        return pageInv.getInventory();
    }

    @Override
    public Inventory[] getPages() {
        List<Inventory> inventories = new ArrayList<>();
        pages.forEach(inventory -> inventories.add(inventory.getInventory()));
        return inventories.stream().filter(inv -> inv != null).collect(Collectors.toList()).toArray(new Inventory[0]);
    }

    @Override
    public void setPage(int page, Inventory inventory) {
        ChestData chestData = getData();
        while(page >= pages.size()){
            pages.add(WInventory.of(chestData.getDefaultSize(), chestData.getDefaultTitle()));
        }
        pages.set(page, WInventory.of(inventory));
    }

    @Override
    public void openPage(Player player, int page) {
        viewers.put(player.getUniqueId(), this);
        player.openInventory(getPage(page));
    }

    @Override
    public void closePage(Player player) {
        viewers.remove(player.getUniqueId());
        player.closeInventory();
    }

    @Override
    public int getPagesAmount() {
        return pages.size();
    }

    @Override
    public int getPageIndex(Inventory inventory) {
        for(int i = 0; i < pages.size(); i++){
            if(pages.get(i).getInventory().equals(inventory))
                return i;
        }

        return 0;
    }

    @Override
    public void remove(){
        plugin.getChestsManager().removeChest(this);
    }

    @Override
    public Map<Integer, ItemStack> addItems(ItemStack... itemStacks) {
        Map<Integer, ItemStack> additionalItems = addRawItems(itemStacks);
        ChestData chestData = getData();
        if(chestData.isSellMode())
            ChestUtils.trySellChest(this);
        if(chestData.isAutoCrafter())
            ChestUtils.tryCraftChest(this);
        return additionalItems;
    }

    @Override
    public Map<Integer, ItemStack> addRawItems(ItemStack... itemStacks) {
        Map<Integer, ItemStack> additionalItems = new HashMap<>();
        Map<Integer, ItemStack> itemAdditionalItems = new HashMap<>();

        for(ItemStack itemStack : itemStacks) {
            if (itemStack != null) {
                int currentInventory = 0;

                do {
                    Inventory inventory = getPage(currentInventory);
                    if(inventory != null) {
                        itemAdditionalItems = inventory.addItem(itemStack);
                    }
                    currentInventory++;
                } while (!itemAdditionalItems.isEmpty() && currentInventory < getPagesAmount());

                additionalItems.putAll(itemAdditionalItems);
            }
        }

        return additionalItems;
    }

    @Override
    public void removeItem(int amountToRemove, ItemStack itemStack) {
        Inventory[] pages = getPages();

        int itemsRemoved = 0;

        for(int i = 0; i < pages.length && itemsRemoved < amountToRemove; i++){
            Inventory page = pages[i];
            int toRemove = Math.min(amountToRemove - itemsRemoved, ItemUtils.countItems(itemStack, page));
            ItemStack cloned = itemStack.clone();
            cloned.setAmount(toRemove);
            page.removeItem(cloned);
        }
    }

    @Override
    public boolean onBreak(BlockBreakEvent event){
        Location loc = getLocation();
        for(int page = 0; page < getPagesAmount(); page++){
            Inventory inventory = getPage(page);
            for(ItemStack itemStack : inventory.getContents())
                if (itemStack != null && itemStack.getType() != Material.AIR) {
                    ItemUtils.dropOrCollect(event.getPlayer(), itemStack, getData().isAutoCollect(), loc);
                }
            inventory.clear();
        }

        Iterator<UUID> viewers = WChest.viewers.keySet().iterator();

        while(viewers.hasNext()){
            UUID uuid = viewers.next();
            if(this.viewers.get(uuid).equals(this))
                viewers.remove();
        }

        return true;
    }

    @Override
    public boolean onPlace(BlockPlaceEvent event){
        return true;
    }

    @Override
    public boolean onOpen(PlayerInteractEvent event){
        if(event.getPlayer().getGameMode() != GameMode.SPECTATOR)
            plugin.getNMSAdapter().playChestAction(getLocation(), true);
        return true;
    }

    @Override
    public boolean onClose(InventoryCloseEvent event) {
        //Checking if player is buying new page
        if(InventoryListener.buyNewPage.containsKey(event.getPlayer().getUniqueId()))
            return false;

        //Checking if player is moving between pages
        if(movingBetweenPages.contains(event.getPlayer().getUniqueId())) {
            if(event.getPlayer().getGameMode() != GameMode.SPECTATOR)
                Executor.sync(() -> plugin.getNMSAdapter().playChestAction(getLocation(), true), 1L);
            movingBetweenPages.remove(event.getPlayer().getUniqueId());
            return false;
        }

        //Checking that it's the last player that views the inventory
        if(getViewersAmount(event.getViewers()) > 1)
            return false;

        //Playing close particle
        if(event.getPlayer().getGameMode() != GameMode.SPECTATOR)
            plugin.getNMSAdapter().playChestAction(getLocation(), false);

        //Selling & crafting chest if needed
        ChestData chestData = getData();
        if(chestData.isAutoCrafter()) ChestUtils.tryCraftChest(this);
        if(chestData.isSellMode()) ChestUtils.trySellChest(this);

        return true;
    }

    @Override
    public boolean onInteract(InventoryClickEvent event) {
        if(event.getSlotType() != InventoryType.SlotType.OUTSIDE)
            return false;

        ChestData chestData = getData();
        int index = getPageIndex(event.getWhoClicked().getOpenInventory().getTopInventory());

        if(event.getClick() == ClickType.LEFT){
            //Making sure he's not in the first page
            if(index != 0){
                movingBetweenPages.add(event.getWhoClicked().getUniqueId());
                openPage((Player) event.getWhoClicked(), index - 1);
            }
        }

        else if(event.getClick() == ClickType.RIGHT){
            //Making sure it's not the last page
            if(index + 1 < getPagesAmount()){
                movingBetweenPages.add(event.getWhoClicked().getUniqueId());
                openPage((Player) event.getWhoClicked(), index + 1);
            }

            //Making sure next page is purchasble
            else if(chestData.getPagesData().containsKey(++index + 1)){
                InventoryData inventoryData = chestData.getPagesData().get(index + 1);
                InventoryListener.buyNewPage.put(event.getWhoClicked().getUniqueId(), inventoryData);

                if(plugin.getSettings().confirmGUI){
                    event.getWhoClicked().openInventory(guiConfirm);
                }else {
                    Locale.EXPAND_CHEST.send(event.getWhoClicked(), inventoryData.getPrice());
                    event.getWhoClicked().closeInventory();
                }
            }
        }

        return true;
    }

    @Override
    public boolean onHopperMove(InventoryMoveItemEvent event) {
        return onHopperMove(event.getItem(), (Hopper) event.getSource().getHolder());
    }

    @Override
    public boolean onHopperMove(ItemStack itemStack, Hopper hopper) {
        if(addItems(itemStack).isEmpty()){
            hopper.getInventory().removeItem(itemStack);
            hopper.update();
        }
        return true;
    }

    @Override
    public boolean onHopperItemTake(Inventory hopperInventory) {
        ChestData chestData = getData();

        int hopperAmount = plugin.getNMSAdapter().getHopperAmount(location.getWorld());

        outerLoop: for(int i = 0; i < getPagesAmount(); i++){
            Inventory inventory = getPage(i);
            for(int slot = 0; slot < inventory.getSize(); slot++){
                ItemStack itemStack = inventory.getItem(slot);

                if(itemStack == null || (chestData.isHopperFilter() && !chestData.containsRecipe(itemStack)))
                    continue;

                int amount = Math.min(ItemUtils.getSpaceLeft(hopperInventory, itemStack), hopperAmount);

                if(amount == 0)
                    continue;

                amount = Math.min(amount, itemStack.getAmount());

                ItemStack copyItem = itemStack.clone();

                copyItem.setAmount(amount);

                HashMap<Integer, ItemStack> additionalItems = hopperInventory.addItem(copyItem);

                if(additionalItems.isEmpty()) {
                    if(itemStack.getAmount() > amount){
                        itemStack.setAmount(itemStack.getAmount() - amount);
                    }else{
                        itemStack.setType(Material.AIR);
                    }
                    inventory.setItem(slot, itemStack);
                    break outerLoop;
                }
            }
        }

        return true;
    }

    public abstract void saveIntoData(boolean async);

    public void loadFromData(ResultSet resultSet) throws SQLException {
        String serialized = resultSet.getString("inventories");
        if(!serialized.isEmpty()) {
            Inventory[] inventories = plugin.getNMSAdapter().deserialze(serialized);
            for (int i = 0; i < inventories.length; i++)
                setPage(i, inventories[i]);
        }
    }

    public void loadFromFile(YamlConfiguration cfg){
        if (cfg.contains("inventory")) {
            ChestData chestData = getData();
            for (String inventoryIndex : cfg.getConfigurationSection("inventory").getKeys(false)) {
                Inventory inventory = Bukkit.createInventory(null, chestData.getDefaultSize(), chestData.getTitle(Integer.valueOf(inventoryIndex) + 1));
                if(cfg.isConfigurationSection("inventory." + inventoryIndex)){
                    for (String slot : cfg.getConfigurationSection("inventory." + inventoryIndex).getKeys(false)) {
                        try {
                            inventory.setItem(Integer.valueOf(slot), cfg.getItemStack("inventory." + inventoryIndex + "." + slot));
                        } catch (Exception ex) {
                            break;
                        }
                    }
                }
                setPage(Integer.valueOf(inventoryIndex), inventory);
            }
        }
    }

    private int getViewersAmount(List<HumanEntity> viewersList){
        int viewers = 0;

        for(HumanEntity viewer : viewersList){
            if(viewer.getGameMode() != GameMode.SPECTATOR)
                viewers++;
        }

        return viewers;
    }

}
