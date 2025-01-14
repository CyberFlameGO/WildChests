package com.bgsoftware.wildchests.objects.chests;

import com.bgsoftware.wildchests.database.Query;
import com.bgsoftware.wildchests.database.StatementHolder;
import com.bgsoftware.wildchests.objects.containers.LinkedChestsContainer;
import com.bgsoftware.wildchests.utils.Executor;
import com.bgsoftware.wildchests.utils.LocationUtils;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import com.bgsoftware.wildchests.api.objects.chests.LinkedChest;
import com.bgsoftware.wildchests.api.objects.data.ChestData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("ConstantConditions")
public final class WLinkedChest extends WRegularChest implements LinkedChest {

    private LinkedChestsContainer linkedChestsContainer;

    public WLinkedChest(UUID placer, Location location, ChestData chestData){
        super(placer, location, chestData);
        this.linkedChestsContainer = null;
    }

    @Override
    public void linkIntoChest(LinkedChest linkedChest) {
        if(linkedChest == null) {
            linkedChestsContainer = null;
        }

        else{
            LinkedChestsContainer otherContainer = ((WLinkedChest) linkedChest).linkedChestsContainer;

            if(otherContainer == null) {
                linkedChest.onBreak(new BlockBreakEvent(null, null));

                if (linkedChestsContainer == null)
                    linkedChestsContainer = new LinkedChestsContainer(this);

                ((WLinkedChest) linkedChest).linkedChestsContainer = linkedChestsContainer;

                linkedChestsContainer.linkChest(linkedChest);
            }

            else{
                otherContainer.linkChest(this);
                linkedChestsContainer = otherContainer;
            }

            ((WLinkedChest) linkedChest).saveLinkedChest();
        }

        saveLinkedChest();
    }

    public void saveLinkedChest(){
        Query.LINKED_CHEST_UPDATE_LINKED_CHEST.getStatementHolder(this)
                .setLocation(!isLinkedIntoChest() || linkedChestsContainer == null ? null : linkedChestsContainer.getSourceChest().getLocation())
                .setLocation(location)
                .execute(true);
    }

    @Override
    public LinkedChest getLinkedChest() {
        return linkedChestsContainer == null ? null : linkedChestsContainer.getSourceChest();
    }

    @Override
    public boolean isLinkedIntoChest() {
        LinkedChest linkedChest = getLinkedChest();
        return linkedChest != null && linkedChest != this;
    }

    @Override
    public List<LinkedChest> getAllLinkedChests() {
        return linkedChestsContainer == null ? new ArrayList<>() : linkedChestsContainer.getLinkedChests();
    }

    @Override
    public Inventory setPage(int page, int size, String title) {
        if(isLinkedIntoChest()){
            return getLinkedChest().setPage(page, size, title);
        }else {
            return super.setPage(page, size, title);
        }
    }

    @Override
    public Inventory getPage(int index) {
        return !isLinkedIntoChest() ? super.getPage(index) : getLinkedChest().getPage(index);
    }

    @Override
    public int getPagesAmount() {
        return !isLinkedIntoChest() ? super.getPagesAmount() : getLinkedChest().getPagesAmount();
    }

    @Override
    public void remove() {
        super.remove();
        //We want to unlink all linked chests only if that's the original chest
        if(linkedChestsContainer != null)
            linkedChestsContainer.unlinkChest(this);
    }

    @Override
    public boolean onBreak(BlockBreakEvent event) {
        if(!isLinkedIntoChest()){
            return super.onBreak(event);
        }

        return true;
    }

    @Override
    public boolean onOpen(PlayerInteractEvent event) {
        if(super.onOpen(event)) {
            if(tileEntityContainer.getViewingCount() != 0)
                getAllLinkedChests().forEach(linkedChest -> plugin.getNMSAdapter().playChestAction(linkedChest.getLocation(), true));
            return true;
        }
        return false;
    }

    @Override
    public boolean onClose(InventoryCloseEvent event) {
        if(super.onClose(event)) {
            if(tileEntityContainer.getViewingCount() == 0)
                getAllLinkedChests().forEach(linkedChest -> plugin.getNMSAdapter().playChestAction(linkedChest.getLocation(), false));
            return true;
        }
        return true;
    }

    public void loadFromData(String serialized, String linkedChest){
        super.loadFromData(serialized);
        if(!linkedChest.isEmpty()){
            Location linkedChestLocation = LocationUtils.fromString(linkedChest);
            Executor.sync(() -> {
                LinkedChest sourceChest = plugin.getChestsManager().getLinkedChest(linkedChestLocation);
                if(sourceChest != null){
                    if(((WLinkedChest) sourceChest).linkedChestsContainer == null)
                        ((WLinkedChest) sourceChest).linkedChestsContainer = new LinkedChestsContainer(sourceChest);

                    this.linkedChestsContainer = ((WLinkedChest) sourceChest).linkedChestsContainer;
                    this.linkedChestsContainer.linkChest(this);
                }
            }, 1L);
        }
    }

    @Override
    public void loadFromFile(YamlConfiguration cfg) {
        super.loadFromFile(cfg);
        if (cfg.contains("linked-chest")) {
            //We want to run it on the first tick, after all chests are loaded.
            Location linkedChest = LocationUtils.fromString(cfg.getString("linked-chest"));
            Executor.sync(() -> linkIntoChest(plugin.getChestsManager().getLinkedChest(linkedChest)), 1L);
        }
    }

    @Override
    public StatementHolder setUpdateStatement(StatementHolder statementHolder) {
        return statementHolder.setInventories(isLinkedIntoChest() ? null : getPages()).setLocation(location);
    }

    @Override
    public void executeUpdateStatement(boolean async) {
        setUpdateStatement(Query.LINKED_CHEST_UPDATE_INVENTORIES.getStatementHolder(this)).execute(async);
    }

    @Override
    public void executeInsertStatement(boolean async) {
        Query.LINKED_CHEST_INSERT.getStatementHolder(this)
                .setLocation(location)
                .setString(placer.toString())
                .setString(getData().getName())
                .setInventories(isLinkedIntoChest() ? null : getPages())
                .setLocation(linkedChestsContainer == null ? null : linkedChestsContainer.getSourceChest().getLocation())
                .execute(async);
    }

    @Override
    public void executeDeleteStatement(boolean async) {
        Query.LINKED_CHEST_DELETE.getStatementHolder(this)
                .setLocation(location)
                .execute(async);
    }

}
